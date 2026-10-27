package com.harikrishnarao.financetracker.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.domain.BudgetEngine
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.Pace
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.BudgetViewModel
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.Format
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.Progress
import com.harikrishnarao.financetracker.ui.RowsDivider
import com.harikrishnarao.financetracker.ui.ScreenTitle
import com.harikrishnarao.financetracker.ui.theme.LocalFinanceColors
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun BudgetScreen() {
    val vm: BudgetViewModel = viewModel(factory = AppViewModelFactory)
    val s by vm.state.collectAsStateWithLifecycle()
    var editIncome by remember { mutableStateOf(false) }
    var editLimit by remember { mutableStateOf<Category?>(null) }
    val fc = LocalFinanceColors.current
    val sug = s.suggestion

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitle("Smart budget", "Based on your last ${BudgetEngine.HISTORY_MONTHS} complete months") }

        if (sug == null) {
            item { Notice("Working out your budget...") }
            return@LazyColumn
        }

        item {
            Card {
                Row(Modifier.fillMaxWidth().clickable { editIncome = true }) {
                    Text("Monthly income", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(sug.monthlyIncome?.let(Format::money) ?: "Tap to set", fontWeight = FontWeight.Bold)
                }
                if (sug.monthlyIncome != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Split("Needs 50%", sug.needs!!)
                        Split("Wants 30%", sug.wants!!)
                        Split("Save 20%", sug.savings!!)
                    }
                } else {
                    Text("No salary credits found yet. Tap to enter your income.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (sug.monthsOfData == 0) {
            item { Notice("Not enough history yet. Suggestions appear after your first full month of tracking. You can still set limits yourself below.") }
        } else {
            item { Text("Suggested limits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Card {
                    sug.advice.forEachIndexed { i, a ->
                        if (i > 0) RowsDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(a.category.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(Format.money(a.suggestedLimit), fontWeight = FontWeight.Bold)
                            }
                            val diff = a.usualMonthly - a.suggestedLimit
                            Text(
                                if (diff > 0) "You usually spend ${Format.money(a.usualMonthly)} · trim ${Format.money(diff)}"
                                else "Usual ${Format.money(a.usualMonthly)} · on track",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (diff > 0) fc.warnText else fc.okText,
                            )
                        }
                    }
                }
            }
            if (sug.extraSaved > 0) item { Notice("Following this keeps about ${Format.money(sug.extraSaved)} more each month than usual.") }
            if (sug.scaledToIncome) item { Notice("Your usual spending is above 80% of income, so 'wants' were trimmed more to protect savings.", warn = true) }
            item {
                Button(onClick = vm::applySuggestion, modifier = Modifier.fillMaxWidth()) { Text("Apply suggested budget") }
            }
        }

        item { Text("This month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item {
            val today = LocalDate.now()
            val days = YearMonth.now().lengthOfMonth()
            Card {
                Category.budgetable.forEachIndexed { i, cat ->
                    if (i > 0) RowsDivider()
                    val limit = s.limits[cat] ?: 0
                    val spent = s.spentThisMonth[cat] ?: 0
                    val pace = BudgetEngine.pace(spent, limit, today.dayOfMonth, days)
                    Column(Modifier.fillMaxWidth().clickable { editLimit = cat }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(cat.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(
                                if (limit > 0) "${Format.money(spent)} / ${Format.money(limit)}" else "${Format.money(spent)} · set limit",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (limit > 0) {
                            Progress(
                                spent.toFloat() / limit,
                                color = when (pace) {
                                    Pace.ON_TRACK -> MaterialTheme.colorScheme.primary
                                    Pace.WATCH -> fc.warnText
                                    Pace.OVER -> MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (editIncome) {
        AmountDialog("Monthly income", sug?.monthlyIncome, onDismiss = { editIncome = false }) {
            vm.setIncome(it); editIncome = false
        }
    }
    editLimit?.let { cat ->
        AmountDialog("${cat.label} limit (0 to remove)", s.limits[cat], allowZero = true, onDismiss = { editLimit = null }) {
            vm.setLimit(cat, it); editLimit = null
        }
    }
}

@Composable
private fun Split(label: String, amount: Long) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Format.money(amount), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AmountDialog(title: String, initial: Long?, allowZero: Boolean = false, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(initial?.takeIf { it > 0 }?.let { (it / 100).toString() } ?: "") }
    val parsed = if (allowZero && text.trim() == "0") 0L else Format.parseRupees(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) },
                label = { Text("₹") },
                singleLine = true,
                isError = text.isNotEmpty() && parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
