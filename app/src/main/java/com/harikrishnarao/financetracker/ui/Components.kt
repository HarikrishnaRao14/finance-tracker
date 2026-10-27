package com.harikrishnarao.financetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harikrishnarao.financetracker.data.TransactionEntity
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.TxnSource
import com.harikrishnarao.financetracker.domain.TxnType
import com.harikrishnarao.financetracker.ui.theme.LocalFinanceColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = color) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp,
    )
}

@Composable
fun Notice(text: String, warn: Boolean = false) {
    val c = LocalFinanceColors.current
    Surface(shape = RoundedCornerShape(14.dp), color = if (warn) c.warnBg else c.okBg, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), color = if (warn) c.warnText else c.okText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
        Text(month.format(fmt), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext, enabled = month < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
        }
    }
}

@Composable
fun Progress(fraction: Float, color: Color = MaterialTheme.colorScheme.primary, track: Color = LocalFinanceColors.current.track) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = color,
        trackColor = track,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun CategoryTag(category: Category) {
    val unsorted = category == Category.UNCATEGORISED
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (unsorted) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
        border = if (unsorted) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Text(
            if (unsorted) "Tap to categorise" else category.label,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (unsorted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun TxnRow(t: TransactionEntity, showDate: Boolean = false, onClick: (() -> Unit)? = null) {
    val income = LocalFinanceColors.current.income
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                t.merchant ?: if (t.type == TxnType.DEBIT) "Payment" else "Money received",
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryTag(t.category)
                val meta = listOfNotNull(
                    if (t.source == TxnSource.MANUAL) "Manual" else t.bankName,
                    t.accountLast4?.let { "··$it" },
                    if (showDate) Format.dateTime(t.timestamp) else Format.time(t.timestamp),
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.padding(4.dp))
        val sign = if (t.type == TxnType.DEBIT) "−" else "+"
        Text(
            sign + Format.money(t.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (t.type == TxnType.CREDIT) income else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun RowsDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
