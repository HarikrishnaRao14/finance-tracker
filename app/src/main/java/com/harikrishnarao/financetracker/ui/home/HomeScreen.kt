package com.harikrishnarao.financetracker.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.Format
import com.harikrishnarao.financetracker.ui.HomeViewModel
import com.harikrishnarao.financetracker.ui.MonthSwitcher
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.Progress
import com.harikrishnarao.financetracker.ui.RowsDivider
import com.harikrishnarao.financetracker.ui.ScreenTitle
import com.harikrishnarao.financetracker.ui.TxnRow
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(onSeeAll: () -> Unit, onOpenBudget: () -> Unit) {
    val vm: HomeViewModel = viewModel(factory = AppViewModelFactory)
    val s by vm.state.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { ScreenTitle("Overview") }
                MonthSwitcher(s.month, vm::previousMonth, vm::nextMonth)
            }
        }

        item {
            Card(color = cs.primary) {
                Text("Spent this month", color = cs.onPrimary, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Format.money(s.spent), color = cs.onPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    if (s.budget > 0) Text("of ${Format.money(s.budget)}", color = cs.onPrimary, style = MaterialTheme.typography.bodyMedium)
                }
                if (s.budget > 0) {
                    Progress(s.spent.toFloat() / s.budget, color = cs.onPrimary, track = cs.onPrimary.copy(alpha = 0.25f))
                    val left = s.budget - s.spent
                    val daysLeft = if (s.month == YearMonth.now()) s.month.lengthOfMonth() - LocalDate.now().dayOfMonth else 0
                    Text(
                        if (left >= 0) "${Format.money(left)} left · $daysLeft days to go" else "${Format.money(-left)} over budget",
                        color = cs.onPrimary, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Income ${Format.money(s.income)}" +
                        if (s.smsEnabled && s.lastSyncAt > 0) " · SMS synced ${Format.dateTime(s.lastSyncAt)}" else "",
                    color = cs.onPrimary, style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (s.byCategory.isNotEmpty()) {
            item {
                Card {
                    s.byCategory.forEach { (cat, amount) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(cat.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(Format.money(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        s.tip?.let { tip ->
            item {
                Column(Modifier.clickable(onClick = onOpenBudget)) { Notice(tip, warn = true) }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }

        if (s.recent.isEmpty()) {
            item {
                Notice(
                    if (s.smsEnabled) "No transactions yet this month. New bank SMS are read every 3 hours, or tap Sync in Spends."
                    else "No transactions yet. Add one in Spends, or turn on SMS tracking in Settings.",
                )
            }
        } else {
            item {
                Card {
                    s.recent.forEachIndexed { i, t ->
                        if (i > 0) RowsDivider()
                        TxnRow(t, showDate = true)
                    }
                }
            }
        }
    }
}
