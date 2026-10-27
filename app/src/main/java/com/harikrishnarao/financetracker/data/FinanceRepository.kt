package com.harikrishnarao.financetracker.data

import com.harikrishnarao.financetracker.domain.BillMatcher
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.Deduper
import com.harikrishnarao.financetracker.domain.Paise
import com.harikrishnarao.financetracker.domain.TransferMatcher
import com.harikrishnarao.financetracker.domain.TxnCandidate
import com.harikrishnarao.financetracker.domain.TxnSource
import com.harikrishnarao.financetracker.domain.TxnType
import com.harikrishnarao.financetracker.sms.BankDirectory
import com.harikrishnarao.financetracker.sms.Categorizer
import com.harikrishnarao.financetracker.sms.SmsParser
import com.harikrishnarao.financetracker.sms.SmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class SyncResult(val read: Int, val skipped: Int, val added: Int)

/** A bank sender found in the inbox during setup. */
data class DetectedSender(val code: String, val bankName: String, val known: Boolean, val transactionCount: Int)

class FinanceRepository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val smsReader: SmsReader,
) {
    private val txDao = db.transactions()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Only one sync at a time: the 3-hour worker and "Sync now" must not import the same SMS twice. */
    private val syncLock = Mutex()

    companion object {
        const val FIRST_SCAN_DAYS = 90L
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

    suspend fun syncSms(): SyncResult = syncLock.withLock {
        withContext(Dispatchers.IO) {
            val s = settings.current()
            if (!s.smsEnabled || !smsReader.hasPermission()) return@withContext SyncResult(0, 0, 0)

            val now = System.currentTimeMillis()
            val since = if (s.lastSmsTimestamp > 0) s.lastSmsTimestamp else now - FIRST_SCAN_DAYS * DAY_MS
            val tracked = db.senders().all().filter { it.tracked }.associateBy { it.code }
            val rules = db.rules().all().associate { it.merchantKey to it.category }

            var read = 0
            var skipped = 0
            var added = 0
            var newest = s.lastSmsTimestamp

            for (sms in smsReader.readInbox(since)) {
                newest = maxOf(newest, sms.date)
                val code = BankDirectory.coreCode(sms.address) ?: continue
                val sender = tracked[code] ?: continue
                read++

                val parsed = SmsParser.parse(sms.body)
                if (parsed == null) { skipped++; continue }

                val candidate = TxnCandidate(parsed.type, parsed.amount, parsed.accountLast4, parsed.refNo, sms.date)
                if (isDuplicate(candidate)) { skipped++; continue }

                val category = Categorizer.categorise(parsed.merchant, parsed.type, parsed.isRefund, rules)
                val id = txDao.insert(
                    TransactionEntity(
                        amount = parsed.amount,
                        type = parsed.type,
                        category = category,
                        merchant = parsed.merchant,
                        accountLast4 = parsed.accountLast4,
                        bankName = sender.bankName,
                        senderCode = code,
                        refNo = parsed.refNo,
                        timestamp = sms.date,
                        source = TxnSource.SMS,
                        isRefund = parsed.isRefund,
                        smsId = sms.id,
                    ),
                )
                if (id <= 0) { skipped++; continue } // same SMS already imported
                added++
                markOwnTransfer(id, candidate)
                if (parsed.type == TxnType.DEBIT) tickOffBill(parsed.amount, sms.date)
            }

            settings.recordSync(now, newest, read, skipped, added)
            SyncResult(read, skipped, added)
        }
    }

    /** Re-read the last 90 days (after adding a bank). Already-imported SMS are skipped by their unique id. */
    suspend fun rescan(): SyncResult {
        val s = settings.current()
        settings.recordSync(s.lastSyncAt, 0, 0, 0, 0)
        return syncSms()
    }

    private suspend fun isDuplicate(c: TxnCandidate): Boolean {
        if (c.refNo != null && txDao.countByRef(c.refNo) > 0) return true
        val nearby = txDao.near(c.timestamp - Deduper.WINDOW_MS, c.timestamp + Deduper.WINDOW_MS)
            .filter { it.source == TxnSource.SMS }
            .map { it.toCandidate() }
        return Deduper.isDuplicate(c, nearby)
    }

    private suspend fun markOwnTransfer(newId: Long, c: TxnCandidate) {
        val others = txDao.near(c.timestamp - TransferMatcher.WINDOW_MS, c.timestamp + TransferMatcher.WINDOW_MS)
            .filter { it.id != newId && it.source == TxnSource.SMS && it.category != Category.TRANSFER }
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

    fun observeMonth(month: YearMonth) = txDao.observeBetween(startOf(month), startOf(month.plusMonths(1)))

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

    suspend fun deleteTransaction(id: Long) = txDao.delete(id)

    /**
     * Change one transaction's category. With [applyToMerchant], the app also remembers the
     * choice for this merchant and fixes its older transactions, so it "learns".
     */
    suspend fun recategorise(t: TransactionEntity, category: Category, applyToMerchant: Boolean) {
        val key = Categorizer.merchantKey(t.merchant)
        if (applyToMerchant && key != null) {
            db.rules().upsert(MerchantRule(key, category))
            val sameMerchant = txDao.allWithMerchant()
                .filter { Categorizer.merchantKey(it.merchant) == key && it.type == t.type && it.category != Category.TRANSFER }
                .map { it.id }
            txDao.setCategory(sameMerchant + t.id, category)
        } else {
            txDao.setCategory(listOf(t.id), category)
        }
    }

    // ---------- Bills, budget, senders ----------

    fun observeBills() = db.bills().observeAll()
    suspend fun saveBill(bill: Bill) = db.bills().upsert(bill)
    suspend fun deleteBill(id: Long) = db.bills().delete(id)
    suspend fun allBills() = db.bills().all()

    fun observeLimits() = db.budgets().observeAll()
    suspend fun applyLimits(limits: Map<Category, Paise>) {
        db.budgets().clear()
        db.budgets().upsertAll(limits.filterValues { it > 0 }.map { BudgetLimit(it.key, it.value) })
    }

    fun observeSenders() = db.senders().observeAll()
    suspend fun setSenderTracked(sender: TrackedSender, tracked: Boolean) =
        db.senders().upsertAll(listOf(sender.copy(tracked = tracked)))
}
