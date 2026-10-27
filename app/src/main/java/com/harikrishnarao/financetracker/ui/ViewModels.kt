package com.harikrishnarao.financetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harikrishnarao.financetracker.data.AppTheme
import com.harikrishnarao.financetracker.data.Bill
import com.harikrishnarao.financetracker.data.BudgetLimit
import com.harikrishnarao.financetracker.data.DetectedSender
import com.harikrishnarao.financetracker.data.FinanceRepository
import com.harikrishnarao.financetracker.data.Settings
import com.harikrishnarao.financetracker.data.SettingsStore
import com.harikrishnarao.financetracker.data.TrackedSender
import com.harikrishnarao.financetracker.data.TransactionEntity
import com.harikrishnarao.financetracker.domain.BudgetEngine
import com.harikrishnarao.financetracker.domain.BudgetSuggestion
import com.harikrishnarao.financetracker.domain.BudgetTxn
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.Pace
import com.harikrishnarao.financetracker.domain.Paise
import com.harikrishnarao.financetracker.domain.TxnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private const val STOP_TIMEOUT = 5_000L

/** Spend per category for a month: debits minus refunds, excluding own transfers and income. */
fun spendByCategory(txns: List<TransactionEntity>): Map<Category, Paise> =
    txns.filter { it.category.isExpense }
        .groupBy { it.category }
        .mapValues { (_, list) ->
            list.sumOf { if (it.type == TxnType.DEBIT) it.amount else if (it.isRefund) -it.amount else 0L }.coerceAtLeast(0)
        }
        .filterValues { it > 0 }

fun incomeOf(txns: List<TransactionEntity>): Paise =
    txns.filter { it.type == TxnType.CREDIT && it.category == Category.INCOME }.sumOf { it.amount }

// ---------------- Root (theme + onboarding) ----------------

class RootViewModel(store: SettingsStore) : ViewModel() {
    val settings: StateFlow<Settings?> = store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
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

    fun scan() = viewModelScope.launch {
        _state.update { it.copy(scanning = true) }
        val found = repo.detectBankSenders()
        _state.update { s -> s.copy(scanning = false, senders = found, chosen = found.filter { it.known }.map { it.code }.toSet()) }
    }

    fun toggle(code: String) = _state.update { s ->
        s.copy(chosen = if (code in s.chosen) s.chosen - code else s.chosen + code)
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        _state.update { it.copy(importing = true) }
        repo.saveSenders(s.senders.orEmpty(), s.chosen)
        if (s.chosen.isNotEmpty()) {
            store.setSmsEnabled(true)
            runCatching { repo.syncSms() } // import first, so Home opens with data
        }
        store.setOnboardingDone(smsEnabled = s.chosen.isNotEmpty())
        onDone()
    }

    fun skipSms(onDone: () -> Unit) = viewModelScope.launch {
        store.setOnboardingDone(smsEnabled = false)
        onDone()
    }
}

// ---------------- Home ----------------

class HomeViewModel(private val repo: FinanceRepository, store: SettingsStore) : ViewModel() {
    data class State(
        val month: YearMonth,
        val spent: Paise = 0,
        val income: Paise = 0,
        val budget: Paise = 0,
        val byCategory: List<Pair<Category, Paise>> = emptyList(),
        val recent: List<TransactionEntity> = emptyList(),
        val tip: String? = null,
        val lastSyncAt: Long = 0,
        val smsEnabled: Boolean = false,
    )

    private val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = combine(
        month.flatMapLatest { m -> repo.observeMonth(m) },
        repo.observeLimits(),
        store.settings,
        month,
    ) { txns, limits, settings, m ->
        val byCat = spendByCategory(txns)
        val limitMap = limits.associate { it.category to it.limit }
        State(
            month = m,
            spent = byCat.values.sum(),
            income = incomeOf(txns),
            budget = limitMap.values.sum(),
            byCategory = byCat.entries.sortedByDescending { it.value }.map { it.key to it.value },
            recent = txns.take(5),
            tip = buildTip(m, byCat, limitMap),
            lastSyncAt = settings.lastSyncAt,
            smsEnabled = settings.smsEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), State(YearMonth.now()))

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { if (it < YearMonth.now()) it.plusMonths(1) else it }

    private fun buildTip(m: YearMonth, spent: Map<Category, Paise>, limits: Map<Category, Paise>): String? {
        if (limits.isEmpty()) return "Open Budget to get a suggested limit for each category."
        if (m != YearMonth.now()) return null
        val today = LocalDate.now()
        val daysLeft = m.lengthOfMonth() - today.dayOfMonth
        val worst = limits.mapNotNull { (cat, limit) ->
            val s = spent[cat] ?: 0
            val pace = BudgetEngine.pace(s, limit, today.dayOfMonth, m.lengthOfMonth())
            if (pace == Pace.ON_TRACK) null else Triple(cat, s, limit)
        }.maxByOrNull { (_, s, l) -> s.toDouble() / l } ?: return null
        val (cat, s, limit) = worst
        return if (s > limit) {
            "${cat.label} is ${Format.money(s - limit)} over its limit this month."
        } else {
            "${cat.label} has used ${s * 100 / limit}% of its limit with $daysLeft days left. " +
                "Keep it under ${Format.money(limit - s)} for the rest of the month."
        }
    }
}

// ---------------- Spends ----------------

enum class SpendFilter(val label: String) { ALL("All"), DEBITS("Debits"), CREDITS("Credits"), TO_SORT("To sort") }

class SpendsViewModel(private val repo: FinanceRepository, store: SettingsStore) : ViewModel() {
    data class State(
        val month: YearMonth,
        val filter: SpendFilter = SpendFilter.ALL,
        val items: List<TransactionEntity> = emptyList(),
        val syncing: Boolean = false,
        val lastSync: String? = null,
        val smsEnabled: Boolean = false,
    )

    private val month = MutableStateFlow(YearMonth.now())
    private val filter = MutableStateFlow(SpendFilter.ALL)
    private val syncing = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = combine(
        month.flatMapLatest { repo.observeMonth(it) },
        filter,
        syncing,
        store.settings,
        month,
    ) { txns, f, sync, s, m ->
        val shown = when (f) {
            SpendFilter.ALL -> txns
            SpendFilter.DEBITS -> txns.filter { it.type == TxnType.DEBIT }
            SpendFilter.CREDITS -> txns.filter { it.type == TxnType.CREDIT }
            SpendFilter.TO_SORT -> txns.filter { it.category == Category.UNCATEGORISED }
        }
        val summary = if (s.lastSyncAt > 0) {
            "Last sync ${Format.dateTime(s.lastSyncAt)} · ${s.lastSyncRead} bank SMS read, " +
                "${s.lastSyncAdded} added, ${s.lastSyncSkipped} skipped (OTPs, promos, duplicates)"
        } else null
        State(m, f, shown, sync, summary, s.smsEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), State(YearMonth.now()))

    fun setFilter(f: SpendFilter) { filter.value = f }
    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { if (it < YearMonth.now()) it.plusMonths(1) else it }

    fun syncNow() = viewModelScope.launch {
        syncing.value = true
        runCatching { repo.syncSms() }
        syncing.value = false
    }

    fun recategorise(t: TransactionEntity, c: Category, applyToMerchant: Boolean) =
        viewModelScope.launch { repo.recategorise(t, c, applyToMerchant) }

    fun delete(t: TransactionEntity) = viewModelScope.launch { repo.deleteTransaction(t.id) }

    fun addManual(amount: Paise, type: TxnType, category: Category, note: String, at: Long) =
        viewModelScope.launch { repo.addManual(amount, type, category, note, at) }
}

// ---------------- Budget ----------------

class BudgetViewModel(private val repo: FinanceRepository, private val store: SettingsStore) : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val suggestion: BudgetSuggestion? = null,
        val limits: Map<Category, Paise> = emptyMap(),
        val spentThisMonth: Map<Category, Paise> = emptyMap(),
    )

    private val suggestion = MutableStateFlow<BudgetSuggestion?>(null)

    val state: StateFlow<State> = combine(
        suggestion,
        repo.observeLimits(),
        repo.observeMonth(YearMonth.now()),
    ) { sug, limits: List<BudgetLimit>, txns ->
        State(sug == null, sug, limits.associate { it.category to it.limit }, spendByCategory(txns))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), State())

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val now = YearMonth.now()
        val history = mutableListOf<BudgetTxn>()
        val incomes = mutableListOf<Paise>()
        for (i in 0 until BudgetEngine.HISTORY_MONTHS) {
            val txns = repo.monthTransactions(now.minusMonths(i + 1L))
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
    fun markPaid(bill: Bill) = viewModelScope.launch {
        repo.saveBill(bill.copy(lastPaidMonth = YearMonth.now().toString()))
    }
    fun markUnpaid(bill: Bill) = viewModelScope.launch { repo.saveBill(bill.copy(lastPaidMonth = null)) }
}

// ---------------- Settings ----------------

class SettingsViewModel(private val repo: FinanceRepository, private val store: SettingsStore) : ViewModel() {
    val settings: StateFlow<Settings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Settings())
    val senders: StateFlow<List<TrackedSender>> = repo.observeSenders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun setTheme(t: AppTheme) = viewModelScope.launch { store.setTheme(t) }
    fun setFollowSystem(on: Boolean) = viewModelScope.launch { store.setFollowSystemDark(on) }
    fun setSenderTracked(s: TrackedSender, on: Boolean) = viewModelScope.launch { repo.setSenderTracked(s, on) }

    /** Find banks again (e.g. after opening a new account) and re-read the last 90 days. */
    fun rescanBanks() = viewModelScope.launch {
        _busy.value = true
        val existing = senders.value
        val found = repo.detectBankSenders()
        val keepChoice = existing.associate { it.code to it.tracked }
        val chosen = found.filter { keepChoice[it.code] ?: it.known }.map { it.code }.toSet() +
            existing.filter { it.tracked }.map { it.code }
        repo.saveSenders(found.filter { f -> existing.none { it.code == f.code } }, chosen)
        store.setSmsEnabled(true)
        repo.rescan()
        _busy.value = false
    }
}
