package com.harikrishnarao.financetracker.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.Format
import com.harikrishnarao.financetracker.ui.HomeViewModel
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.Period
import com.harikrishnarao.financetracker.ui.Pill
import com.harikrishnarao.financetracker.ui.Progress
import com.harikrishnarao.financetracker.ui.Routes
import com.harikrishnarao.financetracker.ui.RowsDivider
import com.harikrishnarao.financetracker.ui.TxnRow
import com.harikrishnarao.financetracker.ui.theme.AmountStyle
import com.harikrishnarao.financetracker.ui.theme.LocalFinanceColors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSeeAll: () -> Unit, onSearch: () -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val vm: HomeViewModel = viewModel(factory = AppViewModelFactory)
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.refreshBattery() // user may come back from the battery settings screen
        onPauseOrDispose { }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Header(s.name, s.syncText, onSettings, onSearch) }
        item { CashFlowCard(s, vm::setPeriod) }

        if (s.budget > 0) {
            item {
                Card(Modifier.clickable { onOpen(Routes.BUDGET) }) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Budget this month", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("${Format.money(s.budgetSpent)} / ${Format.money(s.budget)}", style = MaterialTheme.typography.titleSmall)
                    }
                    Progress(s.budgetSpent.toFloat() / s.budget,
                        color = if (s.budgetSpent > s.budget) LocalFinanceColors.current.spending else MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (s.toReview > 0) {
            item {
                // Small one-line chip, so Recent Transactions stays close to the top.
                Surface(
                    onClick = onSeeAll,
                    shape = RoundedCornerShape(50),
                    color = LocalFinanceColors.current.warnBg,
                    border = BorderStroke(1.dp, LocalFinanceColors.current.warnText.copy(alpha = 0.4f)),
                ) {
                    Text(
                        "${s.toReview} to review: sort or check duplicates ›",
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalFinanceColors.current.warnText,
                    )
                }
            }
        }

        if (s.showChecklist) {
            item {
                Checklist(
                    s.checklist,
                    onDismiss = vm::dismissChecklist,
                    onClick = { id ->
                        when (id) {
                            "budget" -> onOpen(Routes.BUDGET)
                            "bills" -> onOpen(Routes.BILLS)
                            "accounts" -> onSettings()
                            "payees" -> onSeeAll()
                            "battery" -> runCatching {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", context.packageName, null)),
                                )
                            }
                        }
                    },
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent Transactions", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Pill("See all", onClick = onSeeAll)
            }
        }

        if (s.recent.isEmpty()) {
            item { Notice("No transactions in this period yet. Tap + to add a cash spend.") }
        } else {
            item {
                Card(padding = 14) {
                    s.recent.forEachIndexed { i, u ->
                        if (i > 0) RowsDivider()
                        TxnRow(u, onClick = onSeeAll)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(name: String, syncText: String?, onSettings: () -> Unit, onSearch: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fc = LocalFinanceColors.current
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "A" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(BorderStroke(2.dp, Brush.linearGradient(listOf(fc.glowA, fc.glowB))), CircleShape)
                .background(Brush.linearGradient(listOf(fc.glowA.copy(alpha = 0.35f), fc.glowB.copy(alpha = 0.35f))))
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name.ifBlank { "Welcome" }, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (syncText != null) {
                Surface(shape = RoundedCornerShape(50), color = fc.warnBg, border = BorderStroke(1.dp, fc.warnText.copy(alpha = 0.4f))) {
                    Text(syncText, Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = fc.warnText)
                }
            }
        }
        Surface(shape = CircleShape, color = cs.surfaceVariant, modifier = Modifier.size(52.dp)) {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search transactions") }
        }
    }
}

@Composable
private fun CashFlowCard(s: HomeViewModel.State, onPeriod: (Period) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fc = LocalFinanceColors.current
    var menu by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surface)
            .background(Brush.radialGradient(listOf(fc.glowB.copy(alpha = 0.28f), Color.Transparent), center = Offset(900f, 0f), radius = 700f))
            .background(Brush.radialGradient(listOf(fc.glowA.copy(alpha = 0.22f), Color.Transparent), center = Offset(80f, 520f), radius = 620f))
            .border(BorderStroke(1.dp, fc.cardBorder), RoundedCornerShape(28.dp))
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CASH FLOW", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, letterSpacing = 3.sp, color = cs.onSurfaceVariant)
                Box {
                    Pill(s.period.label, trailing = { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp)) }) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Period.entries.forEach { p ->
                            DropdownMenuItem(text = { Text(p.label) }, onClick = { onPeriod(p); menu = false })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SPENDING", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp, color = fc.spending, fontWeight = FontWeight.SemiBold)
                    Text(Format.money(s.spending), style = AmountStyle, maxLines = 1)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("INCOME", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp, color = fc.income, fontWeight = FontWeight.SemiBold)
                    Text(Format.money(s.income), style = AmountStyle, maxLines = 1)
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = fc.inset, modifier = Modifier.fillMaxWidth().clickable { explain = !explain }) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Net Balance", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                            textDecoration = TextDecoration.Underline)
                        Text(
                            (if (s.net < 0) "−" else "") + Format.money(kotlin.math.abs(s.net)),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = if (s.net < 0) fc.spending else cs.onSurface,
                        )
                    }
                    if (explain) {
                        Text("Income minus spending for ${s.period.label.lowercase()}. Own transfers and possible duplicates are not counted.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Checklist(items: List<com.harikrishnarao.financetracker.ui.ChecklistItem>, onDismiss: () -> Unit, onClick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var showDone by remember { mutableStateOf(false) }
    val todo = items.filter { !it.done }
    val done = items.filter { it.done }
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = cs.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Take your first steps", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Surface(shape = CircleShape, color = cs.surfaceVariant, modifier = Modifier.size(44.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Hide checklist") }
                }
            }
            todo.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick(item.id) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.RadioButtonUnchecked, null, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(Icons.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
                }
            }
            if (done.isNotEmpty()) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { showDone = !showDone }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (showDone) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Completed (${done.size})", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                }
                if (showDone) {
                    done.forEach { item ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = cs.primary)
                            Spacer(Modifier.width(14.dp))
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
