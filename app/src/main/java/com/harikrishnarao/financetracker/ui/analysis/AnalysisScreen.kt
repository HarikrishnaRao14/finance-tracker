package com.harikrishnarao.financetracker.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.domain.BudgetEngine
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.Pace
import com.harikrishnarao.financetracker.domain.Paise
import com.harikrishnarao.financetracker.ui.AnalysisViewModel
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.CategoryTile
import com.harikrishnarao.financetracker.ui.Format
import com.harikrishnarao.financetracker.ui.MonthSwitcher
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.Progress
import com.harikrishnarao.financetracker.ui.RowsDivider
import com.harikrishnarao.financetracker.ui.ScreenTitle
import com.harikrishnarao.financetracker.ui.SectionLabel
import com.harikrishnarao.financetracker.ui.color
import com.harikrishnarao.financetracker.ui.theme.LocalFinanceColors
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun AnalysisScreen() {
    val vm: AnalysisViewModel = viewModel(factory = AppViewModelFactory)
    val s by vm.state.collectAsStateWithLifecycle()
    var editIncome by remember { mutableStateOf(false) }
    var editLimit by remember { mutableStateOf<Category?>(null) }
    val fc = LocalFinanceColors.current
    val sug = s.suggestion

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenTitle("Analysis") }
                MonthSwitcher(s.month, vm::previousMonth, vm::nextMonth)
            }
        }

        // ---- Where the money went ----
        item {
            Card {
                SectionLabel("Where your money went")
                if (s.byCategory.isEmpty()) {
                    Text("No spending recorded for this month.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(s.byCategory, s.totalSpent)
                        Spacer(Modifier.width(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Format.money(s.totalSpent), style = MaterialTheme.typography.headlineSmall, color = fc.spending)
                            Text("Income ${Format.money(s.income)}", style = MaterialTheme.typography.bodySmall, color = fc.income)
                        }
                    }
                    s.byCategory.forEach { (cat, amount) ->
                        val share = amount.toFloat() / s.totalSpent
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryTile(cat, size = 34)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text(cat.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${Format.money(amount)} · ${(share * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                                }
                                Progress(share, color = cat.color())
                            }
                        }
                    }
                }
            }
        }

        if (s.topPayees.isNotEmpty()) {
            item {
                Card {
                    SectionLabel("Who you pay most (last 60 days)")
                    s.topPayees.forEachIndexed { i, p ->
                        if (i > 0) RowsDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryTile(p.currentCategory, size = 34)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${p.count} payments${p.usualTime?.let { " · usually $it" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Format.money(p.totalAmount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ---- Smart budget ----
        item { ScreenTitle("Smart budget", "Based on your last ${BudgetEngine.HISTORY_MONTHS} complete months") }
        if (sug == null) {
            item { Notice("Working out your budget...") }
        } else {
            item {
                Card {
                    Row(Modifier.fillMaxWidth().clickable { editIncome = true }) {
                        Text("Monthly income", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sug.monthlyIncome?.let(Format::money) ?: "Tap to set", fontWeight = FontWeight.Bold)
                    }
                    val inc = sug.monthlyIncome
                    if (inc != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Split("Needs 50%", inc * 50 / 100)
                            Split("Wants 30%", inc * 30 / 100)
                            Split("Save 20%", inc * 20 / 100)
                        }
                    } else {
                        Text("No salary credits found yet. Tap to enter your income.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (sug.monthsOfData == 0) {
                item { Notice("Suggestions appear after your first full month of tracking. You can still set limits yourself below.") }
            } else {
                item {
                    Card {
                        SectionLabel("Suggested limits")
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
                item { Button(onClick = vm::applySuggestion, modifier = Modifier.fillMaxWidth()) { Text("Apply suggested budget") } }
            }
        }

        item {
            val today = LocalDate.now()
            val days = YearMonth.now().lengthOfMonth()
            Card {
                SectionLabel("This month's limits (tap to change)")
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
                                    Pace.OVER -> fc.spending
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
private fun Donut(parts: List<Pair<Category, Paise>>, total: Paise) {
    val track = LocalFinanceColors.current.track
    Canvas(Modifier.size(120.dp)) {
        val stroke = 18.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = Stroke(stroke))
        var start = -90f
        val gap = if (parts.size > 1) 3f else 0f
        parts.forEach { (cat, amount) ->
            val sweep = 360f * amount / total
            if (sweep - gap > 0.5f) {
                drawArc(cat.color(), start + gap / 2, sweep - gap, false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            }
            start += sweep
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
