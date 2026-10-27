@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.harikrishnarao.financetracker.ui.spends

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.data.TransactionEntity
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.TxnSource
import com.harikrishnarao.financetracker.domain.TxnType
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.Format
import com.harikrishnarao.financetracker.ui.MonthSwitcher
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.RowsDivider
import com.harikrishnarao.financetracker.ui.ScreenTitle
import com.harikrishnarao.financetracker.ui.SectionLabel
import com.harikrishnarao.financetracker.ui.SpendFilter
import com.harikrishnarao.financetracker.ui.SpendsViewModel
import com.harikrishnarao.financetracker.ui.TxnRow

@Composable
fun SpendsScreen() {
    val vm: SpendsViewModel = viewModel(factory = AppViewModelFactory)
    val s by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { ScreenTitle("Spends") }
                    if (s.smsEnabled) {
                        Button(onClick = vm::syncNow, enabled = !s.syncing) {
                            if (s.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Sync now")
                        }
                    }
                }
            }
            s.lastSync?.let { item { Notice(it) } }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpendFilter.entries.forEach { f ->
                        FilterChip(selected = s.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
                    }
                }
            }
            item { MonthSwitcher(s.month, vm::previousMonth, vm::nextMonth) }

            if (s.items.isEmpty()) {
                item { Notice("Nothing here for this month. Tap + to add a cash spend.") }
            }

            val byDay = s.items.groupBy { Format.localDate(it.timestamp) }
            byDay.forEach { (day, list) ->
                item(key = "h$day") { SectionLabel(Format.dayHeader(day)) }
                item(key = "d$day") {
                    Card {
                        list.forEachIndexed { i, t ->
                            if (i > 0) RowsDivider()
                            TxnRow(t) { editing = t }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add a transaction") }
    }

    editing?.let { t ->
        EditDialog(
            t = t,
            onDismiss = { editing = null },
            onSave = { c, all -> vm.recategorise(t, c, all); editing = null },
            onDelete = { vm.delete(t); editing = null },
        )
    }
    if (adding) {
        AddDialog(
            onDismiss = { adding = false },
            onAdd = { amount, type, cat, note -> vm.addManual(amount, type, cat, note, System.currentTimeMillis()); adding = false },
        )
    }
}

@Composable
private fun CategoryPicker(selected: Category, choices: List<Category>, onPick: (Category) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { c ->
            FilterChip(selected = selected == c, onClick = { onPick(c) }, label = { Text(c.label) })
        }
    }
}

@Composable
private fun EditDialog(
    t: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (Category, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var cat by remember { mutableStateOf(t.category) }
    var all by remember { mutableStateOf(t.merchant != null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.merchant ?: "Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${if (t.type == TxnType.DEBIT) "Spent" else "Received"} ${Format.money(t.amount)} · ${Format.dateTime(t.timestamp)}" +
                        (t.bankName?.let { "\n$it" } ?: "") + (t.refNo?.let { " · Ref $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SectionLabel("Category")
                CategoryPicker(cat, Category.userChoices) { cat = it }
                if (t.merchant != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = all, onCheckedChange = { all = it })
                        Text("Always use this for ${t.merchant}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (t.source == TxnSource.MANUAL || confirmDelete) {
                    TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                        Text(if (confirmDelete) "Tap again to delete" else "Delete", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Not a real transaction? Remove it", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(cat, all) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddDialog(onDismiss: () -> Unit, onAdd: (Long, TxnType, Category, String) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxnType.DEBIT) }
    var cat by remember { mutableStateOf(Category.FOOD) }
    var note by remember { mutableStateOf("") }
    val amount = Format.parseRupees(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add transaction") },
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
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(40) },
                    label = { Text("What was it? (optional)") }, singleLine = true,
                )
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
