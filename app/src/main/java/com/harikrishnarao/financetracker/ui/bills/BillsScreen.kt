package com.harikrishnarao.financetracker.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.harikrishnarao.financetracker.data.Bill
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.BillsViewModel
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.Format
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.RowsDivider
import com.harikrishnarao.financetracker.ui.ScreenTitle
import com.harikrishnarao.financetracker.ui.theme.LocalFinanceColors
import com.harikrishnarao.financetracker.work.BillSchedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun BillsScreen() {
    val vm: BillsViewModel = viewModel(factory = AppViewModelFactory)
    val bills by vm.bills.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Bill?>(null) }
    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    val fc = LocalFinanceColors.current

    val upcoming = bills
        .map { it to BillSchedule.nextDueDate(it.dueDay, today, BillSchedule.isPaidThisMonth(it, today)) }
        .sortedBy { it.second }
    val dueIn15 = upcoming.filter { ChronoUnit.DAYS.between(today, it.second) <= 15 }.sumOf { it.first.amount }
    val urgent = upcoming.firstOrNull { (b, due) ->
        !b.isAutopay && !BillSchedule.isPaidThisMonth(b, today) && ChronoUnit.DAYS.between(today, due) <= b.remindDaysBefore
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ScreenTitle("Bills", if (bills.isEmpty()) null else "${Format.money(dueIn15)} due in the next 15 days") }

            urgent?.let { (b, due) ->
                item {
                    Card(color = fc.warnBg) {
                        val days = ChronoUnit.DAYS.between(today, due)
                        Text(
                            if (days == 0L) "DUE TODAY" else "DUE IN $days DAY${if (days == 1L) "" else "S"}",
                            color = fc.warnText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fc.warnText)
                            Text(Format.money(b.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fc.warnText)
                        }
                        Button(onClick = { vm.markPaid(b) }, modifier = Modifier.fillMaxWidth()) { Text("Mark paid") }
                    }
                }
            }

            if (bills.isEmpty()) {
                item { Notice("No bills yet. Tap 'Add bill' to add rent, electricity, internet, subscriptions...") }
            } else {
                item {
                    Card {
                        upcoming.forEachIndexed { i, (b, due) ->
                            if (i > 0) RowsDivider()
                            val paid = BillSchedule.isPaidThisMonth(b, today)
                            Row(Modifier.fillMaxWidth().clickable { editing = b }, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${b.name} · ${Format.money(b.amount)}", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOfNotNull(
                                            if (b.isAutopay) "Autopay" else "Manual",
                                            if (paid) "Paid this month · next ${due.format(fmt)}" else due.format(fmt),
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (paid) fc.okText else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = b.reminderOn, onCheckedChange = { vm.toggleReminder(b) })
                            }
                        }
                    }
                }
                item { Notice("Switch on = a reminder notification before the due date. A bill is ticked off automatically when a matching debit shows up in your bank SMS.") }
            }
        }

        // Labelled, so it is not confused with the centre + (which adds a cash transaction).
        ExtendedFloatingActionButton(
            onClick = { editing = Bill(name = "", amount = 0, dueDay = today.dayOfMonth, isAutopay = false) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add bill") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    editing?.let { b ->
        BillDialog(
            bill = b,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onDelete = { vm.delete(b); editing = null },
            onTogglePaid = { if (BillSchedule.isPaidThisMonth(b, today)) vm.markUnpaid(b) else vm.markPaid(b); editing = null },
        )
    }
}

@Composable
private fun BillDialog(bill: Bill, onDismiss: () -> Unit, onSave: (Bill) -> Unit, onDelete: () -> Unit, onTogglePaid: () -> Unit) {
    val isNew = bill.id == 0L
    var name by remember { mutableStateOf(bill.name) }
    var amountText by remember { mutableStateOf(if (bill.amount > 0) (bill.amount / 100).toString() else "") }
    var dayText by remember { mutableStateOf(bill.dueDay.toString()) }
    var autopay by remember { mutableStateOf(bill.isAutopay) }
    var remindText by remember { mutableStateOf(bill.remindDaysBefore.toString()) }

    val amount = Format.parseRupees(amountText)
    val day = dayText.toIntOrNull()?.takeIf { it in 1..31 }
    val remind = remindText.toIntOrNull()?.takeIf { it in 0..15 }
    val valid = name.isNotBlank() && amount != null && day != null && remind != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add bill" else "Edit bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name (e.g. Electricity)") }, singleLine = true)
                OutlinedTextField(
                    amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                    label = { Text("Amount (₹)") }, singleLine = true, isError = amountText.isNotEmpty() && amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    dayText, { dayText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Due day of month (1-31)") }, singleLine = true, isError = day == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    remindText, { remindText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Remind how many days before (0-15)") }, singleLine = true, isError = remind == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !autopay, onClick = { autopay = false }, label = { Text("I pay it") })
                    FilterChip(selected = autopay, onClick = { autopay = true }, label = { Text("Autopay") })
                }
                if (!isNew) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onTogglePaid) {
                            Text(if (BillSchedule.isPaidThisMonth(bill, LocalDate.now())) "Mark unpaid" else "Mark paid")
                        }
                        TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(bill.copy(name = name.trim(), amount = amount!!, dueDay = day!!, isAutopay = autopay, remindDaysBefore = remind!!))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
