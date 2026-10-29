package com.harikrishnarao.financetracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.data.SmsLogEntry
import com.harikrishnarao.financetracker.data.SmsVerdict
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.Format
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.ScreenTitle
import com.harikrishnarao.financetracker.ui.SmsActivityViewModel
import com.harikrishnarao.financetracker.ui.Tag
import com.harikrishnarao.financetracker.ui.theme.LocalFinanceColors

private enum class LogFilter(val label: String) { ALL("All"), ADDED("Added"), SKIPPED("Skipped"), DUPLICATES("Duplicates") }

/** Every bank SMS the app read, and what it did with it. Makes the tracking easy to trust and to check. */
@Composable
fun SmsActivityScreen(onBack: () -> Unit) {
    val vm: SmsActivityViewModel = viewModel(factory = AppViewModelFactory)
    val log by vm.log.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    var expanded by remember { mutableStateOf<Long?>(null) }

    val shown = log.orEmpty().filter {
        when (filter) {
            LogFilter.ALL -> true
            LogFilter.ADDED -> it.verdict == SmsVerdict.ADDED
            LogFilter.SKIPPED -> it.verdict == SmsVerdict.SKIPPED || it.verdict == SmsVerdict.REMOVED
            LogFilter.DUPLICATES -> it.verdict == SmsVerdict.DUPLICATE || it.verdict == SmsVerdict.POSSIBLE_DUPLICATE
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("SMS activity", "Bank SMS from your tracked banks, newest first")
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogFilter.entries.forEach { f ->
                    val n = log.orEmpty().count { e ->
                        when (f) {
                            LogFilter.ALL -> true
                            LogFilter.ADDED -> e.verdict == SmsVerdict.ADDED
                            LogFilter.SKIPPED -> e.verdict == SmsVerdict.SKIPPED || e.verdict == SmsVerdict.REMOVED
                            LogFilter.DUPLICATES -> e.verdict == SmsVerdict.DUPLICATE || e.verdict == SmsVerdict.POSSIBLE_DUPLICATE
                        }
                    }
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} ($n)") })
                }
            }
        }
        if (log != null && shown.isEmpty()) {
            item { Notice("Nothing here yet. Bank SMS appear here after each check.") }
        }
        items(shown, key = { it.smsId }) { e ->
            LogRow(e, expanded == e.smsId) { expanded = if (expanded == e.smsId) null else e.smsId }
        }
    }
}

@Composable
private fun LogRow(e: SmsLogEntry, open: Boolean, onClick: () -> Unit) {
    val fc = LocalFinanceColors.current
    val color: Color = when (e.verdict) {
        SmsVerdict.ADDED -> fc.income
        SmsVerdict.POSSIBLE_DUPLICATE, SmsVerdict.DUPLICATE -> fc.warnText
        SmsVerdict.REMOVED -> fc.spending
        SmsVerdict.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.clickable(onClick = onClick), padding = 14) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.bankName, style = MaterialTheme.typography.titleSmall)
                Text("${e.senderCode} · ${Format.dateTime(e.timestamp)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Tag(if (e.verdict == SmsVerdict.SKIPPED) "Skipped" else e.verdict.label.substringBefore(':'), color)
        }
        if (e.reason != null) Text(e.reason, style = MaterialTheme.typography.bodySmall, color = color)
        Text(
            e.body,
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (open) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
