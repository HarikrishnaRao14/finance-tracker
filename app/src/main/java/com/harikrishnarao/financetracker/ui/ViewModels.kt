package com.harikrishnarao.financetracker.ui

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harikrishnarao.financetracker.data.AccountEntity
import com.harikrishnarao.financetracker.data.AppTheme
import com.harikrishnarao.financetracker.data.Bill
import com.harikrishnarao.financetracker.data.DetectedSender
import com.harikrishnarao.financetracker.data.FinanceRepository
import com.harikrishnarao.financetracker.data.Settings
import com.harikrishnarao.financetracker.data.SettingsStore
import com.harikrishnarao.financetracker.data.SmsLogEntry
import com.harikrishnarao.financetracker.data.TrackedSender
import com.harikrishnarao.financetracker.data.TransactionEntity
import com.harikrishnarao.financetracker.data.accountKey
import com.harikrishnarao.financetracker.data.counts
import com.harikrishnarao.financetracker.domain.BudgetEngine
import com.harikrishnarao.financetracker.domain.BudgetSuggestion
import com.harikrishnarao.financetracker.domain.BudgetTxn
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.FrequentPayee
import com.harikrishnarao.financetracker.domain.Paise
import com.harikrishnarao.financetracker.domain.PayeeInsights
import com.harikrishnarao.financetracker.domain.Cycle
import com.harikrishnarao.financetracker.domain.PayCycle
import com.harikrishnarao.financetracker.domain.PayCycleConfig
import com.harikrishnarao.financetracker.domain.PayRule
import java.time.LocalDate
import com.harikrishnarao.financetracker.domain.TxnSource
import com.harikrishnarao.financetracker.domain.TxnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

private const val STOP_TIMEOUT = 5_000L
private const val DAY_MS = 24 * 60 * 60 * 1000L
const val SYNC_EVERY_MS = 3 * 60 * 60 * 1000L

/** Lookup data shared by every list: payee nicknames and accounts. */
data class Lookups(val payeeNames: Map<String, String> = emptyMap(), val accounts: Map<String, AccountEntity> = emptyMap())

private fun lookups(repo: FinanceRepository): Flow<Lookups> =
    combine(repo.observePayeeNames(), repo.observeAccounts()) { names, accs -> Lookups(names, accs.associateBy { it.key }) }

/** Frequent payees over the last 60 days. */
private fun frequentPayees(repo: FinanceRepository): Flow<List<FrequentPayee>> =
    combine(repo.observeSince(System.currentTimeMillis() - PayeeInsights.LOOKBACK_DAYS * DAY_MS), repo.observePayeeNames()) { txns, names ->
        PayeeInsights.find(txns.toPayeeTxns(names))
    }

// ---------------- Root (theme, parser upgrade) ----------------

class RootViewModel(store: SettingsStore, repo: FinanceRepository) : ViewModel() {
    val settings: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // After an update with better SMS rules, rebuild SMS transactions once.
        viewModelScope.launch { runCatching { repo.ensureParserUpToDate() } }
    }
}

// ---------------- Quick add (centre + button) ----------------

class QuickAddViewModel(private val repo: FinanceRepository) : ViewModel() {
    fun add(amount: Paise, type: TxnType, category: Category, note: String) =
        viewModelScope.launch { repo.addManual(amount, type, category, note, System.currentTimeMillis()) }
}

// ---------------- Onboarding ----------------

class OnboardingViewModel(private val repo: FinanceRepository, private val store: SettingsStore) : ViewModel() {
    data class State(
        val scanning: Boolean = false,
        val senders: List<DetectedSender>? = null,
        val chosen: Set<String> = emptySet(),
        val importing: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setName(name: String) = viewModelScope.launch { store.setUserName(name) }

    fun scan() = viewModelScope.launch {
        _state.update { it.copy(scanning = true) }
        val found = repo.detectBankSenders()
        _state.update { s -> s.copy(scanning = false, senders = found, chosen = found.filter { it.known }.map { it.code }.toSet()) }
    }

    fun toggle(code: String) = _state.update { s ->
        s.copy(chosen = if (code in s.chosen) s.chosen - code else s.chosen + code)
    }

    fun finish() = viewModelScope.launch {
        val s = _state.value
        _state.update { it.copy(importing = true) }
        repo.saveSenders(s.senders.orEmpty(), s.chosen)
        if (s.chosen.isNotEmpty()) {
            store.setSmsEnabled(true)
            runCatching { repo.syncSms() } // import first, so Home opens with data
        }
        store.setParserVersion(com.harikrishnarao.financetracker.sms.SmsParser.VERSION)
        store.setOnboardingDone(smsEnabled = s.chosen.isNotEmpty())
    }

    fun skipSms() = viewModelScope.launch { store.setOnboardingDone(smsEnabled = false) }
}

// ---------------- Home ----------------

enum class Period(val label: String) {
    PAY_CYCLE("This Pay Cycle"), THIS_MONTH("This Month"), LAST_MONTH("Last Month"), LAST_3_MONTHS("Last 3 Months"),
}

data class ChecklistItem(val id: String, val title: String, val done: Boolean)

class HomeViewModel(private val repo: FinanceRepository, private val store: SettingsStore, private val appContext: Context) : ViewModel() {
    data class State(
        val name: String = "",
        val period: Period = Period.THIS_MONTH,
        val spending: Paise = 0,
        val income: Paise = 0,
        val budget: Paise = 0,
        val budgetSpent: Paise = 0,
        val recent: List<UiTxn> = emptyList(),
        val syncText: String? = null,
        val checklist: List<ChecklistItem> = emptyList(),
        val showChecklist: Boolean = false,
        val toReview: Int = 0,
        val payCycleSet: Boolean = false,
        /** Shown under the cash flow title for the pay cycle, e.g. "30 Oct – 29 Nov". */
        val cycleRange: String? = null,
        val daysToSalary: Long? = null,
        val nextPayday: String? = null,
    ) {
        val net: Paise get() = income - spending
        /** What can be spent per day until the next salary and still end at zero. */
        val perDay: Paise? get() = daysToSalary?.takeIf { it > 0 && net > 0 }?.let { net / it }
    }

    /** null = automatic: the pay cycle when one is set, otherwise this month. */
    private val period = MutableStateFlow<Period?>(null)
    private val batteryOk = MutableStateFlow(isBatteryUnrestricted())

    private fun range(p: Period, cycle: Cycle?): Pair<Long, Long> {
        val now = YearMonth.now()
        val zone = java.time.ZoneId.systemDefault()
        return when (p) {
            Period.PAY_CYCLE -> if (cycle == null) range(Period.THIS_MONTH, null) else
                cycle.start.atStartOfDay(zone).toInstant().toEpochMilli() to cycle.nextPayday.atStartOfDay(zone).toInstant().toEpochMilli()
            Period.THIS_MONTH -> repo.startOf(now) to repo.startOf(now.plusMonths(1))
            Period.LAST_MONTH -> repo.startOf(now.minusMonths(1)) to repo.startOf(now)
            Period.LAST_3_MONTHS -> repo.startOf(now.minusMonths(2)) to repo.startOf(now.plusMonths(1))
        }
    }

    private data class Extra(val limits: Map<Category, Paise>, val billCount: Int, val namedAccounts: Boolean, val payees: List<FrequentPayee>)

    private val extra: Flow<Extra> = combine(repo.observeLimits(), repo.observeBills(), repo.observeAccounts(), frequentPayees(repo)) { l, b, a, p ->
        Extra(l.associate { it.category to it.limit }, b.size, a.any { !it.nickname.isNullOrBlank() }, p)
    }

    private val thisMonth = repo.observeMonth(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = combine(
        combine(period, store.settings.map { it.payCycle }.distinctUntilChanged()) { p, cfg -> p to cfg }
            .flatMapLatest { (chosen, cfg) ->
                val cycle = PayCycle.current(LocalDate.now(), cfg)
                val p = chosen ?: if (cycle != null) Period.PAY_CYCLE else Period.THIS_MONTH
                range(p, cycle).let { (a, b) -> repo.observeBetween(a, b) }.map { PeriodData(p, cycle, it) }
            },
        thisMonth,
        store.settings,
        combine(extra, batteryOk) { e, ok -> e to ok },
        lookups(repo),
    ) { (p, cycle, txns), monthTxns, s, (e, battery), look ->
        val spend = spendByCategory(txns).values.sum()
        val reviewCount = monthTxns.count { it.possibleDuplicate || it.category == Category.UNCATEGORISED }
        val checklist = listOf(
            ChecklistItem("paycycle", "Set your salary date (pay cycle)", s.payCycle.isSet),
            ChecklistItem("budget", "Set your budget for this month", e.limits.isNotEmpty()),
            ChecklistItem("bills", "Add recurring bills", e.billCount > 0),
            ChecklistItem("accounts", "Name your accounts", e.namedAccounts),
            ChecklistItem("payees", "Sort your frequent payees", e.payees.none { it.needsAttention }),
            ChecklistItem("battery", "Allow background SMS check", battery || !s.smsEnabled),
        )
        State(
            name = s.userName,
            period = p,
            spending = spend,
            income = incomeOf(txns),
            budget = e.limits.values.sum(),
            budgetSpent = spendByCategory(monthTxns).values.sum(),
            recent = txns.take(8).map { it.toUi(look.payeeNames, look.accounts) },
            syncText = syncText(s),
            checklist = checklist,
            showChecklist = !s.checklistDismissed && checklist.any { !it.done },
            toReview = reviewCount,
            payCycleSet = cycle != null,
            cycleRange = if (p == Period.PAY_CYCLE && cycle != null) "${Format.dayMonth(cycle.start)} – ${Format.dayMonth(cycle.lastDay)}" else null,
            daysToSalary = if (p == Period.PAY_CYCLE) cycle?.daysLeft(LocalDate.now()) else null,
            nextPayday = if (p == Period.PAY_CYCLE) cycle?.nextPayday?.let(Format::weekdayDayMonth) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), State())

    private data class PeriodData(val period: Period, val cycle: Cycle?, val txns: List<TransactionEntity>)

    fun setPeriod(p: Period) { period.value = p }
    fun dismissChecklist() = viewModelScope.launch { store.setChecklistDismissed(true) }
    fun refreshBattery() { batteryOk.value = isBatteryUnrestricted() }

    private fun isBatteryUnrestricted(): Boolean =
        (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(appContext.packageName)
}

fun syncText(s: Settings): String? = when {
    !s.smsEnabled -> null
    s.lastSyncAt == 0L -> "SMS not checked yet"
    else -> "SMS checked ${Format.time(s.lastSyncAt)}"
}

// ---------------- Spends ----------------

enum class TypeFilter(val label: String) { ALL("All"), SPENT("Spent"), RECEIVED("Received"), TO_SORT("To sort"), DUPLICATES("Possible duplicates") }

/** Account choice in the dropdown: null key = all accounts, CASH = manual entries. */
data class AccountChoice(val key: String?, val label: String, val isCard: Boolean = false) {
    companion object {
        val ALL = AccountChoice(null, "All accounts")
        val CASH = AccountChoice("cash", "Cash (added by you)")
    }
}

data class Statement(
    val moneyIn: Paise,
    val moneyOut: Paise,
    val lastBalance: Paise?,
    val lastBalanceAt: Long?,
    val isCard: Boolean,
    val count: Int,
    /** Part of money out that went to your own accounts or card bill. */
    val transfersOut: Paise = 0,
)

class SpendsViewModel(private val repo: FinanceRepository, store: SettingsStore) : ViewModel() {
    data class Filters(
        val month: YearMonth = YearMonth.now(),
        val account: AccountChoice = AccountChoice.ALL,
        val type: TypeFilter = TypeFilter.ALL,
        val category: Category? = null,
        val query: String = "",
    )

    data class State(
        val filters: Filters = Filters(),
        val items: List<UiTxn> = emptyList(),
        val accounts: List<AccountChoice> = listOf(AccountChoice.ALL),
        val statement: Statement? = null,
        val duplicates: Int = 0,
        val toSort: Int = 0,
        val suggestions: List<FrequentPayee> = emptyList(),
        val syncing: Boolean = false,
        val syncStatus: String? = null,
        val smsEnabled: Boolean = false,
        val message: String? = null,
    )

    private val filters = MutableStateFlow(Filters())
    private val syncing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthTxns = filters.map { it.month }.flatMapLatestDistinct { repo.observeMonth(it) }

    val state: StateFlow<State> = combine(
        combine(filters, monthTxns) { f, t -> f to t },
        lookups(repo),
        frequentPayees(repo),
        combine(syncing, message) { a, b -> a to b },
        store.settings,
    ) { (f, txns), look, payees, (sync, msg), s ->
        val accountChoices = buildList {
            add(AccountChoice.ALL)
            look.accounts.values.forEach { a ->
                add(AccountChoice(a.key, a.nickname?.takeIf { it.isNotBlank() }
                    ?: "${shortBank(a.bankName)} ${if (a.isCard) "Credit card" else "A/c"} ··${a.last4}", a.isCard))
            }
            if (txns.any { it.source == TxnSource.MANUAL }) add(AccountChoice.CASH)
        }
        val byAccount = when (f.account.key) {
            null -> txns
            AccountChoice.CASH.key -> txns.filter { it.source == TxnSource.MANUAL }
            else -> txns.filter { it.accountKey == f.account.key }
        }
        val q = f.query.trim().lowercase()
        val shown = byAccount
            .filter { t ->
                when (f.type) {
                    TypeFilter.ALL -> true
                    TypeFilter.SPENT -> t.type == TxnType.DEBIT
                    TypeFilter.RECEIVED -> t.type == TxnType.CREDIT
                    TypeFilter.TO_SORT -> t.category == Category.UNCATEGORISED
                    TypeFilter.DUPLICATES -> t.possibleDuplicate
                }
            }
            .filter { f.category == null || it.category == f.category }
            .map { it.toUi(look.payeeNames, look.accounts) }
            .filter { u ->
                q.isEmpty() || u.title.lowercase().contains(q) || (u.t.note?.lowercase()?.contains(q) == true) ||
                    (u.t.amount / 100).toString().startsWith(q) || u.subtitle.lowercase().contains(q)
            }
        State(
            filters = f,
            items = shown,
            accounts = accountChoices,
            statement = if (f.account.key == null) null else statementOf(byAccount, f.account.isCard),
            duplicates = txns.count { it.possibleDuplicate },
            toSort = txns.count { it.category == Category.UNCATEGORISED && it.counts },
            suggestions = payees.filter { it.needsAttention }.take(3),
            syncing = sync,
            syncStatus = syncStatus(s),
            smsEnabled = s.smsEnabled,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), State())

    private fun statementOf(txns: List<TransactionEntity>, isCard: Boolean): Statement {
        val counted = txns.filter { it.counts }
        val withBal = txns.filter { it.balance != null }.maxByOrNull { it.timestamp }
        return Statement(
            moneyIn = counted.filter { it.type == TxnType.CREDIT }.sumOf { it.amount },
            moneyOut = counted.filter { it.type == TxnType.DEBIT }.sumOf { it.amount },
            lastBalance = withBal?.balance,
            lastBalanceAt = withBal?.timestamp,
            isCard = isCard,
            count = counted.size,
            transfersOut = counted.filter { it.type == TxnType.DEBIT && it.category == Category.TRANSFER }.sumOf { it.amount },
        )
    }

    private fun syncStatus(s: Settings): String? {
        if (!s.smsEnabled) return null
        if (s.lastSyncAt == 0L) return "Bank SMS not checked yet. Tap Sync now."
        val next = Format.time(s.lastSyncAt + SYNC_EVERY_MS)
        val found = if (s.lastSyncAdded == 0L) "no new transactions" else "${s.lastSyncAdded} new transaction(s)"
        val skipped = if (s.lastSyncSkipped.isBlank()) "" else "\nSkipped: ${s.lastSyncSkipped}"
        return "Last checked ${Format.dateTime(s.lastSyncAt)}: ${s.lastSyncRead} bank SMS, $found.$skipped\nNext automatic check around $next."
    }

    fun update(change: (Filters) -> Filters) = filters.update(change)
    fun previousMonth() = filters.update { it.copy(month = it.month.minusMonths(1)) }
    fun nextMonth() = filters.update { if (it.month < YearMonth.now()) it.copy(month = it.month.plusMonths(1)) else it }
    fun clearMessage() { message.value = null }

    fun syncNow() = viewModelScope.launch {
        syncing.value = true
        val r = runCatching { repo.syncSms() }.getOrNull()
        syncing.value = false
        message.value = when {
            r == null -> "Could not read SMS. Check that SMS permission is allowed."
            r.added == 0 -> "Up to date. No new bank transactions."
            else -> "Added ${r.added} new transaction(s)."
        }
    }

    fun recategorise(t: TransactionEntity, c: Category, applyToMerchant: Boolean) =
        viewModelScope.launch { repo.recategorise(t, c, applyToMerchant) }

    fun renamePayee(key: String, name: String) = viewModelScope.launch { repo.renamePayee(key, name) }

    fun applySuggestion(p: FrequentPayee, category: Category) =
        viewModelScope.launch { repo.applyCategoryToPayee(p.key, category) }

    fun delete(t: TransactionEntity) = viewModelScope.launch { repo.deleteTransaction(t) }
    fun keep(t: TransactionEntity) = viewModelScope.launch { repo.keepTransaction(t) }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T, R> Flow<T>.flatMapLatestDistinct(transform: (T) -> Flow<R>): Flow<R> =
    distinctUntilChanged().flatMapLatest(transform)

// ---------------- Analysis (spending breakdown + smart budget) ----------------

class AnalysisViewModel(private val repo: FinanceRepository, private val store: SettingsStore) : ViewModel() {
    data class State(
        val month: YearMonth = YearMonth.now(),
        val byCategory: List<Pair<Category, Paise>> = emptyList(),
        val totalSpent: Paise = 0,
        val income: Paise = 0,
        val topPayees: List<FrequentPayee> = emptyList(),
        val suggestion: BudgetSuggestion? = null,
        val limits: Map<Category, Paise> = emptyMap(),
        val spentThisMonth: Map<Category, Paise> = emptyMap(),
    )

    private val month = MutableStateFlow(YearMonth.now())
    private val suggestion = MutableStateFlow<BudgetSuggestion?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = combine(
        month.flatMapLatest { m -> repo.observeMonth(m).map { m to it } },
        repo.observeMonth(YearMonth.now()),
        repo.observeLimits(),
        suggestion,
        frequentPayees(repo),
    ) { (m, txns), current, limits, sug, payees ->
        val byCat = spendByCategory(txns)
        State(
            month = m,
            byCategory = byCat.entries.sortedByDescending { it.value }.map { it.key to it.value },
            totalSpent = byCat.values.sum(),
            income = incomeOf(txns),
            topPayees = payees.sortedByDescending { it.totalAmount }.take(5),
            suggestion = sug,
            limits = limits.associate { it.category to it.limit },
            spentThisMonth = spendByCategory(current),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), State())

    init { refresh() }

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { if (it < YearMonth.now()) it.plusMonths(1) else it }

    fun refresh() = viewModelScope.launch {
        val now = YearMonth.now()
        val history = mutableListOf<BudgetTxn>()
        val incomes = mutableListOf<Paise>()
        for (i in 0 until BudgetEngine.HISTORY_MONTHS) {
            val txns = repo.monthTransactions(now.minusMonths(i + 1L)).filter { it.counts }
            incomes += incomeOf(txns)
            txns.filter { it.category.isExpense }.forEach { t ->
                if (t.type == TxnType.DEBIT) history += BudgetTxn(t.category, t.amount, i)
                else if (t.isRefund) history += BudgetTxn(t.category, t.amount, i, isRefund = true)
            }
        }
        val override = store.current().incomeOverride.takeIf { it > 0 }
        suggestion.value = BudgetEngine.suggest(history, incomes, override)
    }

    fun setIncome(paise: Paise) = viewModelScope.launch {
        store.setIncomeOverride(paise)
        refresh()
    }

    fun applySuggestion() = viewModelScope.launch {
        val s = suggestion.value ?: return@launch
        repo.applyLimits(s.advice.associate { it.category to it.suggestedLimit })
    }

    fun setLimit(category: Category, paise: Paise) = viewModelScope.launch {
        val current = state.value.limits.toMutableMap()
        if (paise > 0) current[category] = paise else current.remove(category)
        repo.applyLimits(current)
    }
}

// ---------------- Bills ----------------

class BillsViewModel(private val repo: FinanceRepository) : ViewModel() {
    val bills: StateFlow<List<Bill>> = repo.observeBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    fun save(bill: Bill) = viewModelScope.launch { repo.saveBill(bill) }
    fun delete(bill: Bill) = viewModelScope.launch { repo.deleteBill(bill.id) }
    fun toggleReminder(bill: Bill) = viewModelScope.launch { repo.saveBill(bill.copy(reminderOn = !bill.reminderOn)) }
    fun markPaid(bill: Bill) = viewModelScope.launch { repo.saveBill(bill.copy(lastPaidMonth = YearMonth.now().toString())) }
    fun markUnpaid(bill: Bill) = viewModelScope.launch { repo.saveBill(bill.copy(lastPaidMonth = null)) }
}

// ---------------- Settings ----------------

class SettingsViewModel(private val repo: FinanceRepository, private val store: SettingsStore) : ViewModel() {
    val settings: StateFlow<Settings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Settings())
    val senders: StateFlow<List<TrackedSender>> = repo.observeSenders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())
    val accounts: StateFlow<List<AccountEntity>> = repo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    fun setTheme(t: AppTheme) = viewModelScope.launch { store.setTheme(t) }
    fun setPayRule(rule: PayRule, day: Int) = viewModelScope.launch { store.setPayRule(rule, day); store.setActualPayday(null) }
    fun setActualPayday(day: LocalDate?) = viewModelScope.launch { store.setActualPayday(day) }
    fun setName(n: String) = viewModelScope.launch { store.setUserName(n) }
    fun setSenderTracked(s: TrackedSender, on: Boolean) = viewModelScope.launch { repo.setSenderTracked(s, on) }
    fun renameAccount(a: AccountEntity, name: String) = viewModelScope.launch { repo.renameAccount(a.key, name) }
    fun setAccountIsCard(a: AccountEntity, isCard: Boolean) = viewModelScope.launch { repo.setAccountIsCard(a.key, isCard) }

    /** Find banks again (e.g. a new account) and read the last 90 days for them. */
    fun rescanBanks() = viewModelScope.launch {
        _busy.value = "Looking for bank SMS..."
        val existing = senders.value
        val found = repo.detectBankSenders()
        val chosen = found.filter { f -> existing.firstOrNull { it.code == f.code }?.tracked ?: f.known }.map { it.code }.toSet() +
            existing.filter { it.tracked }.map { it.code }
        repo.saveSenders(found.filter { f -> existing.none { it.code == f.code } }, chosen)
        store.setSmsEnabled(true)
        repo.rescan()
        _busy.value = null
    }

    /** Start over: re-import SMS transactions with the current rules (manual entries and rules are kept). */
    fun rebuild() = viewModelScope.launch {
        _busy.value = "Rebuilding from SMS..."
        runCatching { repo.rebuildFromSms() }
        _busy.value = null
    }
}

// ---------------- SMS activity ----------------

class SmsActivityViewModel(repo: FinanceRepository) : ViewModel() {
    val log: StateFlow<List<SmsLogEntry>?> = repo.observeSmsLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)
}
