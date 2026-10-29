@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.harikrishnarao.financetracker.ui.spends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.FrequentPayee
import com.harikrishnarao.financetracker.domain.TxnSource
import com.harikrishnarao.financetracker.domain.TxnType
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.CategoryTile
import com.harikrishnarao.financetracker.ui.Format
import com.harikrishnarao.financetracker.ui.MonthSwitcher
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.Pill
import com.harikrishnarao.financetracker.ui.RowsDivider
import com.harikrishnarao.financetracker.ui.ScreenTitle
import com.harikrishnarao.financetracker.ui.SectionLabel
import com.harikrishnarao.financetracker.ui.SpendsViewModel
import com.harikrishnarao.financetracker.ui.Statement
import com.harikrishnarao.financetracker.ui.TxnRow
import com.harikrishnarao.financetracker.ui.TypeFilter
import com.harikrishnarao.financetracker.ui.UiTxn
import com.harikrishnarao.financetracker.ui.icon
import com.harikrishnarao.financetracker.ui.theme.LocalFinanceColors
import kotlinx.coroutines.delay

@Composable
fun SpendsScreen(startWithSearch: Boolean, onOpenSmsActivity: () -> Unit) {
    val vm: SpendsViewModel = viewModel(factory = AppViewModelFactory)
    val s by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<UiTxn?>(null) }
    var choosingFor by remember { mutableStateOf<FrequentPayee?>(null) }
    var searching by remember { mutableStateOf(startWithSearch) }
    var accountMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    val f = s.filters

    LaunchedEffect(s.message) {
        if (s.message != null) { delay(4000); vm.clearMessage() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Title, search, sync ----
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenTitle("Spends") }
                IconButton(onClick = { searching = !searching; if (!searching) vm.update { it.copy(query = "") } }) {
                    Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "Search")
                }
                if (s.smsEnabled) {
                    Button(onClick = vm::syncNow, enabled = !s.syncing) {
                        if (s.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sync now")
                    }
                }
            }
        }
        if (searching) {
            item {
                OutlinedTextField(
                    value = f.query,
                    onValueChange = { q -> vm.update { it.copy(query = q.take(40)) } },
                    placeholder = { Text("Search payee, note or amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        s.message?.let { item { Notice(it) } }
        s.syncStatus?.let { status ->
            item {
                Card(Modifier.clickable(onClick = onOpenSmsActivity), color = LocalFinanceColors.current.okBg) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = LocalFinanceColors.current.okText)
                    Text("See what happened to each bank SMS ›", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ---- Account dropdown (acts as a statement filter) ----
        item {
            Box {
                Pill(
                    f.account.label,
                    modifier = Modifier.fillMaxWidth(),
                    selected = f.account.key != null,
                    trailing = { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp)) },
                ) { accountMenu = true }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    s.accounts.forEach { a ->
                        DropdownMenuItem(text = { Text(a.label) }, onClick = { vm.update { it.copy(account = a) }; accountMenu = false })
                    }
                }
            }
        }
        s.statement?.let { st -> item { StatementCard(f.account.label, st) } }

        // ---- Filters ----
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeFilter.entries.forEach { t ->
                    val count = when (t) {
                        TypeFilter.TO_SORT -> s.toSort
                        TypeFilter.DUPLICATES -> s.duplicates
                        else -> null
                    }
                    if (t == TypeFilter.DUPLICATES && s.duplicates == 0 && f.type != t) return@forEach
                    FilterChip(
                        selected = f.type == t,
                        onClick = { vm.update { it.copy(type = t) } },
                        label = { Text(if (count != null && count > 0) "${t.label} ($count)" else t.label) },
                    )
                }
                Box {
                    FilterChip(
                        selected = f.category != null,
                        onClick = { categoryMenu = true },
                        label = { Text(f.category?.label ?: "Category") },
                        trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(16.dp)) },
                    )
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        DropdownMenuItem(text = { Text("All categories") }, onClick = { vm.update { it.copy(category = null) }; categoryMenu = false })
                        (Category.userChoices + Category.UNCATEGORISED).forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.label) },
                                leadingIcon = { CategoryTile(c, size = 28) },
                                onClick = { vm.update { it.copy(category = c) }; categoryMenu = false },
                            )
                        }
                    }
                }
            }
        }
        item { MonthSwitcher(f.month, vm::previousMonth, vm::nextMonth) }

        // ---- Frequent payee suggestions ----
        if (f.account.key == null && f.query.isBlank() && s.suggestions.isNotEmpty()) {
            item { SectionLabel("Suggestions for frequent payees") }
            s.suggestions.forEach { p ->
                item(key = "sug${p.key}") {
                    SuggestionCard(p, onApply = { vm.applySuggestion(p, p.suggested) }, onChoose = { choosingFor = p })
                }
            }
        }

        // ---- The list, newest first ----
        if (s.items.isEmpty()) {
            item { Notice("Nothing matches these filters for this month.") }
        }
        val byDay = s.items.groupBy { Format.localDate(it.t.timestamp) }
        byDay.forEach { (day, list) ->
            item(key = "h$day") {
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { SectionLabel(Format.dayHeader(day)) }
                    val out = list.filter { it.t.type == TxnType.DEBIT && !it.t.possibleDuplicate }.sumOf { it.t.amount }
                    if (out > 0) Text("−${Format.money(out)}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "d$day") {
                Card(padding = 12) {
                    list.forEachIndexed { i, u ->
                        if (i > 0) RowsDivider()
                        TxnRow(u, showDate = false) { editing = u }
                    }
                }
            }
        }
    }

    editing?.let { u ->
        EditTransactionDialog(
            u = u,
            onDismiss = { editing = null },
            onSave = { c, all, name ->
                vm.recategorise(u.t, c, all)
                if (u.payeeKey != null && name != null) vm.renamePayee(u.payeeKey, name)
                editing = null
            },
            onDelete = { vm.delete(u.t); editing = null },
            onKeep = { vm.keep(u.t); editing = null },
        )
    }
    choosingFor?.let { p ->
        CategoryChoiceDialog(
            title = "Category for ${p.name}",
            initial = p.suggested,
            onDismiss = { choosingFor = null },
            onPick = { c -> vm.applySuggestion(p, c); choosingFor = null },
        )
    }
}

@Composable
private fun StatementCard(label: String, st: Statement) {
    val fc = LocalFinanceColors.current
    Card {
        Text("Statement · $label", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(if (st.isCard) "Payments to card" else "Money in", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.money(st.moneyIn), style = MaterialTheme.typography.titleMedium, color = fc.income)
            }
            Column(Modifier.weight(1f)) {
                Text(if (st.isCard) "Spent on card" else "Money out", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.money(st.moneyOut), style = MaterialTheme.typography.titleMedium, color = fc.spending)
                if (st.transfersOut > 0) {
                    Text("incl. ${Format.money(st.transfersOut)} to your own accounts", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (st.lastBalance != null && st.lastBalanceAt != null) {
            Text(
                "${if (st.isCard) "Available limit" else "Balance"} ${Format.money(st.lastBalance)} (as per SMS on ${Format.dateTime(st.lastBalanceAt)})",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${st.count} transaction(s) this month", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SuggestionCard(p: FrequentPayee, onApply: () -> Unit, onChoose: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(p.suggested, size = 40)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text("${p.count} payments · ${Format.money(p.totalAmount)} in total", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(p.reason, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApply) { Text("Set all as ${p.suggested.label}") }
            OutlinedButton(onClick = onChoose) { Text("Other") }
        }
    }
}

@Composable
fun CategoryPicker(selected: Category, choices: List<Category>, onPick: (Category) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { c ->
            FilterChip(selected = selected == c, onClick = { onPick(c) }, label = { Text(c.label) },
                leadingIcon = { Icon(c.icon(), null, Modifier.size(16.dp)) })
        }
    }
}

@Composable
private fun CategoryChoiceDialog(title: String, initial: Category, onDismiss: () -> Unit, onPick: (Category) -> Unit) {
    var cat by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("All its payments, and future ones, get this category.", style = MaterialTheme.typography.bodyMedium)
                CategoryPicker(cat, Category.expenseChoices) { cat = it }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(cat) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditTransactionDialog(
    u: UiTxn,
    onDismiss: () -> Unit,
    onSave: (Category, Boolean, String?) -> Unit,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
) {
    val t = u.t
    var cat by remember { mutableStateOf(t.category) }
    var all by remember { mutableStateOf(u.payeeKey != null) }
    var name by remember { mutableStateOf(u.title) }
    var showSms by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fc = LocalFinanceColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(u.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                run {
                    Text(
                        "${if (t.type == TxnType.DEBIT) "Spent" else "Received"} ${Format.money(t.amount)} · ${Format.dateTime(t.timestamp)}" +
                            (u.accountLabel?.let { "\n$it" } ?: "") + (t.refNo?.let { " · Ref $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (t.possibleDuplicate) {
                    run {
                        Notice("This looks like a second SMS for the same payment, so it is not counted. " +
                            "Keep it only if you really paid twice.", warn = true)
                    }
                    run {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onDelete) { Text("Remove duplicate") }
                            OutlinedButton(onClick = onKeep) { Text("Keep, I paid twice") }
                        }
                    }
                }
                if (u.payeeKey != null) {
                    run {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it.take(40) },
                            label = { Text("Payee name (e.g. Office canteen lunch)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                run { SectionLabel("Category") }
                run { CategoryPicker(cat, Category.userChoices) { cat = it } }
                if (u.payeeKey != null) {
                    run {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = all, onCheckedChange = { all = it })
                            Text("Use for all payments to this payee", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (t.rawText != null) {
                    run {
                        TextButton(onClick = { showSms = !showSms }) { Text(if (showSms) "Hide original SMS" else "Show original SMS") }
                    }
                    if (showSms) {
                        run {
                            Text(t.rawText, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!t.possibleDuplicate) {
                    run {
                        TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                            Text(
                                when {
                                    confirmDelete -> "Tap again to remove"
                                    t.source == TxnSource.MANUAL -> "Delete"
                                    else -> "Not a real transaction? Remove it"
                                },
                                color = fc.spending,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(cat, all, if (name != u.title) name else null) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Used by the centre + button. */
@Composable
fun AddTransactionDialog(onDismiss: () -> Unit, onAdd: (Long, TxnType, Category, String) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxnType.DEBIT) }
    var cat by remember { mutableStateOf(Category.FOOD) }
    var note by remember { mutableStateOf("") }
    val amount = Format.parseRupees(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add cash transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == TxnType.DEBIT, onClick = { type = TxnType.DEBIT; if (cat == Category.INCOME) cat = Category.FOOD }, label = { Text("Spent") })
                    FilterChip(selected = type == TxnType.CREDIT, onClick = { type = TxnType.CREDIT; cat = Category.INCOME }, label = { Text("Received") })
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12) },
                    label = { Text("Amount (₹)") },
                    isError = amountText.isNotEmpty() && amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(value = note, onValueChange = { note = it.take(40) }, label = { Text("What was it? (optional)") }, singleLine = true)
                if (type == TxnType.DEBIT) {
                    Text("Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    CategoryPicker(cat, Category.expenseChoices) { cat = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let { onAdd(it, type, cat, note.trim()) } }, enabled = amount != null) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
