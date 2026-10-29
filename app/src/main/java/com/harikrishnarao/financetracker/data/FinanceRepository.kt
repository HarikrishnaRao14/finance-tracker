package com.harikrishnarao.financetracker.data

import androidx.room.withTransaction
import com.harikrishnarao.financetracker.domain.BillMatcher
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.Deduper
import com.harikrishnarao.financetracker.domain.DupVerdict
import com.harikrishnarao.financetracker.domain.Paise
import com.harikrishnarao.financetracker.domain.TransferMatcher
import com.harikrishnarao.financetracker.domain.TxnCandidate
import com.harikrishnarao.financetracker.domain.TxnSource
import com.harikrishnarao.financetracker.domain.TxnType
import com.harikrishnarao.financetracker.sms.BankDirectory
import com.harikrishnarao.financetracker.sms.Categorizer
import com.harikrishnarao.financetracker.sms.ParseOutcome
import com.harikrishnarao.financetracker.sms.SkipReason
import com.harikrishnarao.financetracker.sms.SmsParser
import com.harikrishnarao.financetracker.sms.SmsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class SyncResult(val read: Int, val added: Int, val skippedSummary: String)

/** A bank sender found in the inbox during setup. */
data class DetectedSender(val code: String, val bankName: String, val known: Boolean, val transactionCount: Int)

class FinanceRepository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val smsReader: SmsSource,
) {
    private val txDao = db.transactions()
    private val logDao = db.smsLog()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Only one sync at a time: the 3-hour worker and "Sync now" must not import the same SMS twice. */
    private val syncLock = Mutex()

    companion object {
        const val FIRST_SCAN_DAYS = 90L
        const val LOG_KEEP_DAYS = 120L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }

    // ---------- Setup: find which banks message this phone ----------

    suspend fun detectBankSenders(): List<DetectedSender> = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - FIRST_SCAN_DAYS * DAY_MS
        smsReader.readInbox(since)
            .mapNotNull { sms -> BankDirectory.coreCode(sms.address)?.let { it to sms.body } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (code, bodies) ->
                val txCount = bodies.count { body -> SmsParser.parse(body)?.accountLast4 != null }
                val known = BankDirectory.isKnownBank(code)
                when {
                    known -> DetectedSender(code, BankDirectory.bankName(code)!!, true, txCount)
                    // Unknown sender: only offer it if it really sends account transactions.
                    txCount >= 2 -> DetectedSender(code, "Other bank ($code)", false, txCount)
                    else -> null
                }
            }
            .sortedWith(compareByDescending<DetectedSender> { it.known }.thenByDescending { it.transactionCount })
    }

    suspend fun saveSenders(all: List<DetectedSender>, chosen: Set<String>) {
        db.senders().upsertAll(all.map { TrackedSender(it.code, it.bankName, it.code in chosen) })
    }

    // ---------- SMS sync ----------

    /** Rebuilds SMS transactions once after the parsing rules improve (keeps manual entries and your rules). */
    suspend fun ensureParserUpToDate() {
        val s = settings.current()
        if (s.parserVersion >= SmsParser.VERSION) return
        if (s.smsEnabled && smsReader.hasPermission()) rebuildFromSms()
        settings.setParserVersion(SmsParser.VERSION)
    }

    /** Deletes SMS-made transactions and imports the last 90 days again with the current rules. */
    suspend fun rebuildFromSms(): SyncResult {
        syncLock.withLock {
            db.withTransaction {
                txDao.deleteAllSms()
                logDao.clearExceptRemoved()
            }
            settings.resetSmsCursor()
        }
        return syncSms()
    }

    /** Re-read the last 90 days (after adding a bank). Already-imported SMS are skipped. */
    suspend fun rescan(): SyncResult {
        settings.resetSmsCursor()
        return syncSms()
    }

    suspend fun syncSms(): SyncResult = syncLock.withLock {
        withContext(Dispatchers.IO) {
            val s = settings.current()
            if (!s.smsEnabled || !smsReader.hasPermission()) return@withContext SyncResult(0, 0, "")

            val now = System.currentTimeMillis()
            val since = if (s.lastSmsTimestamp > 0) s.lastSmsTimestamp else now - FIRST_SCAN_DAYS * DAY_MS
            val inbox = smsReader.readInbox(since) // read outside the DB transaction
            val tracked = db.senders().all().filter { it.tracked }.associateBy { it.code }
            val rules = db.rules().all().associate { it.merchantKey to it.category }
            val removed = logDao.removedIds().toHashSet()
            val imported = txDao.importedSmsIds().toHashSet()

            var read = 0
            var added = 0
            val skipped = LinkedHashMap<String, Int>()
            var newest = s.lastSmsTimestamp

            db.withTransaction {
                for (sms in inbox) {
                    newest = maxOf(newest, sms.date)
                    val code = BankDirectory.coreCode(sms.address) ?: continue
                    val sender = tracked[code] ?: continue
                    if (sms.id in removed || sms.id in imported) continue
                    read++

                    suspend fun log(verdict: SmsVerdict, reason: String?, txnId: Long?) = logDao.upsert(
                        SmsLogEntry(sms.id, sms.date, code, sender.bankName, sms.body, verdict, reason, txnId),
                    )

                    when (val outcome = SmsParser.classify(sms.body)) {
                        is ParseOutcome.Skipped -> {
                            skipped[skipLabel(outcome.reason)] = (skipped[skipLabel(outcome.reason)] ?: 0) + 1
                            log(SmsVerdict.SKIPPED, outcome.reason.label, null)
                        }
                        is ParseOutcome.Transaction -> {
                            val p = outcome.sms
                            val candidate = TxnCandidate(p.type, p.amount, p.accountLast4, p.refNo, sms.date)
                            val verdict = dupVerdict(candidate)
                            if (verdict == DupVerdict.DUPLICATE) {
                                skipped["duplicate"] = (skipped["duplicate"] ?: 0) + 1
                                log(SmsVerdict.DUPLICATE, "Same amount, account and reference as an earlier SMS", null)
                                continue
                            }
                            val possible = verdict == DupVerdict.POSSIBLE_DUPLICATE
                            val id = txDao.insert(
                                TransactionEntity(
                                    amount = p.amount, type = p.type,
                                    category = Categorizer.categorise(p.merchant, p.type, p.isRefund, rules),
                                    merchant = p.merchant, accountLast4 = p.accountLast4, bankName = sender.bankName,
                                    senderCode = code, refNo = p.refNo, timestamp = sms.date, source = TxnSource.SMS,
                                    isRefund = p.isRefund, smsId = sms.id, isCard = p.isCard, channel = p.channel,
                                    balance = p.balance, possibleDuplicate = possible, rawText = sms.body,
                                ),
                            )
                            if (id <= 0) continue // this SMS is already imported
                            added++
                            if (p.accountLast4 != null) {
                                db.accounts().insertIfNew(
                                    AccountEntity(AccountEntity.key(sender.bankName, p.accountLast4), sender.bankName, p.accountLast4, p.isCard),
                                )
                            }
                            if (possible) {
                                log(SmsVerdict.POSSIBLE_DUPLICATE, "Same amount and account within an hour of another payment", id)
                            } else {
                                log(SmsVerdict.ADDED, null, id)
                                markOwnTransfer(id, candidate)
                                if (p.type == TxnType.DEBIT) tickOffBill(p.amount, sms.date)
                            }
                        }
                    }
                }
                logDao.trim(now - LOG_KEEP_DAYS * DAY_MS)
            }

            val summary = skipped.entries.joinToString(" · ") { (k, v) -> "$v $k" }
            settings.recordSync(now, newest, read, added, summary)
            SyncResult(read, added, summary)
        }
    }

    private fun skipLabel(r: SkipReason) = when (r) {
        SkipReason.OTP -> "OTP"
        SkipReason.PROMO -> "promo"
        SkipReason.FAILED -> "failed"
        SkipReason.NOT_YET -> "reminder"
        SkipReason.NO_AMOUNT, SkipReason.NOT_TRANSACTION -> "info"
    }

    private suspend fun dupVerdict(c: TxnCandidate): DupVerdict {
        if (c.refNo != null && txDao.countByRef(c.refNo) > 0) return DupVerdict.DUPLICATE
        val nearby = txDao.near(c.timestamp - Deduper.POSSIBLE_WINDOW_MS, c.timestamp + Deduper.POSSIBLE_WINDOW_MS)
            .filter { it.source == TxnSource.SMS }
            .map { it.toCandidate() }
        return Deduper.check(c, nearby)
    }

    private suspend fun markOwnTransfer(newId: Long, c: TxnCandidate) {
        val others = txDao.near(c.timestamp - TransferMatcher.WINDOW_MS, c.timestamp + TransferMatcher.WINDOW_MS)
            .filter { it.id != newId && it.source == TxnSource.SMS && it.category != Category.TRANSFER && !it.possibleDuplicate }
        val idx = TransferMatcher.findPair(c, others.map { it.toCandidate() }) ?: return
        txDao.setCategory(listOf(newId, others[idx].id), Category.TRANSFER)
    }

    private suspend fun tickOffBill(amount: Paise, at: Long) {
        val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val month = YearMonth.from(date).toString()
        val bill = db.bills().all().firstOrNull {
            it.lastPaidMonth != month && BillMatcher.matches(amount, date.dayOfMonth, it.amount, it.dueDay)
        } ?: return
        db.bills().upsert(bill.copy(lastPaidMonth = month))
    }

    private fun TransactionEntity.toCandidate() = TxnCandidate(type, amount, accountLast4, refNo, timestamp)

    // ---------- Transactions ----------

    fun observeBetween(from: Long, to: Long) = txDao.observeBetween(from, to)

    fun observeMonth(month: YearMonth) = txDao.observeBetween(startOf(month), startOf(month.plusMonths(1)))

    fun observeSince(from: Long) = txDao.observeSince(from)

    suspend fun monthTransactions(month: YearMonth) = txDao.between(startOf(month), startOf(month.plusMonths(1)))

    fun startOf(month: YearMonth): Long = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    suspend fun addManual(amount: Paise, type: TxnType, category: Category, note: String?, at: Long) {
        txDao.insert(
            TransactionEntity(
                amount = amount, type = type, category = category, merchant = note?.takeIf { it.isNotBlank() },
                accountLast4 = null, bankName = null, senderCode = null, refNo = null,
                timestamp = at, source = TxnSource.MANUAL, note = note,
            ),
        )
    }

    /** Removes a transaction. An SMS one is remembered, so a re-scan never brings it back. */
    suspend fun deleteTransaction(t: TransactionEntity) = db.withTransaction {
        if (t.smsId != null) {
            logDao.upsert(
                SmsLogEntry(t.smsId, t.timestamp, t.senderCode.orEmpty(), t.bankName.orEmpty(), t.rawText.orEmpty(),
                    SmsVerdict.REMOVED, null, null),
            )
        }
        txDao.delete(t.id)
    }

    /** "Possible duplicate" turned out to be a real separate payment. */
    suspend fun keepTransaction(t: TransactionEntity) = db.withTransaction {
        txDao.keep(t.id)
        logDao.setVerdictForTxn(t.id, SmsVerdict.ADDED)
    }

    /**
     * Change one transaction's category. With [applyToMerchant], the app also remembers the
     * choice for this merchant and fixes its other transactions, so it "learns".
     */
    suspend fun recategorise(t: TransactionEntity, category: Category, applyToMerchant: Boolean) {
        val key = Categorizer.merchantKey(t.merchant)
        if (applyToMerchant && key != null) applyCategoryToPayee(key, category, t.type, extraIds = listOf(t.id))
        else txDao.setCategory(listOf(t.id), category)
    }

    suspend fun applyCategoryToPayee(key: String, category: Category, type: TxnType = TxnType.DEBIT, extraIds: List<Long> = emptyList()) {
        db.rules().upsert(MerchantRule(key, category))
        val ids = txDao.allWithMerchant()
            .filter { Categorizer.merchantKey(it.merchant) == key && it.type == type && it.category != Category.TRANSFER }
            .map { it.id }
        txDao.setCategory(ids + extraIds, category)
    }

    // ---------- Payees, accounts, SMS log ----------

    fun observePayeeNames(): Flow<Map<String, String>> =
        db.payees().observeAll().map { list -> list.associate { it.merchantKey to it.displayName } }

    suspend fun renamePayee(key: String, name: String) {
        if (name.isBlank()) db.payees().delete(key) else db.payees().upsert(PayeeName(key, name.trim().take(40)))
    }

    fun observeAccounts() = db.accounts().observeAll()
    suspend fun renameAccount(key: String, nickname: String) = db.accounts().rename(key, nickname.trim().take(30).ifBlank { null })
    suspend fun setAccountIsCard(key: String, isCard: Boolean) = db.accounts().setCard(key, isCard)

    fun observeSmsLog(limit: Int = 300) = logDao.observeRecent(limit)

    // ---------- Bills, budget, senders ----------

    fun observeBills() = db.bills().observeAll()
    suspend fun saveBill(bill: Bill) = db.bills().upsert(bill)
    suspend fun deleteBill(id: Long) = db.bills().delete(id)
    suspend fun allBills() = db.bills().all()

    fun observeLimits() = db.budgets().observeAll()
    suspend fun applyLimits(limits: Map<Category, Paise>) = db.withTransaction {
        db.budgets().clear()
        db.budgets().upsertAll(limits.filterValues { it > 0 }.map { BudgetLimit(it.key, it.value) })
    }

    fun observeSenders() = db.senders().observeAll()
    suspend fun setSenderTracked(sender: TrackedSender, tracked: Boolean) =
        db.senders().upsertAll(listOf(sender.copy(tracked = tracked)))
}
