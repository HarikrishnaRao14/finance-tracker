package com.harikrishnarao.financetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.TxnType
import com.harikrishnarao.financetracker.ui.theme.LocalFinanceColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Card(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = color,
        border = BorderStroke(1.dp, LocalFinanceColors.current.cardBorder),
    ) {
        Column(Modifier.padding(padding.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
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
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
    )
}

@Composable
fun Notice(text: String, warn: Boolean = false) {
    val c = LocalFinanceColors.current
    Surface(shape = RoundedCornerShape(16.dp), color = if (warn) c.warnBg else c.okBg, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), color = if (warn) c.warnText else c.okText, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Rounded pill button like "This Month ▾" and "See all". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, selected: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(50),
        color = if (selected) cs.primary else cs.surfaceVariant,
        contentColor = if (selected) cs.onPrimary else cs.onSurface,
        border = BorderStroke(1.dp, if (selected) cs.primary else LocalFinanceColors.current.cardBorder),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing?.invoke()
        }
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
        Text(month.format(fmt), style = MaterialTheme.typography.titleSmall)
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

/** Tinted rounded square with the category icon. */
@Composable
fun CategoryTile(category: Category, size: Int = 48) {
    val c = category.color()
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(c.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon(), contentDescription = category.label, tint = c, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun Tag(text: String, color: Color, outlined: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (outlined) Color.Transparent else color.copy(alpha = 0.16f),
        border = if (outlined) BorderStroke(1.dp, color.copy(alpha = 0.7f)) else null,
    ) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/** One transaction row: icon tile, payee, "UPI · ICICI ··719", amount and date. */
@Composable
fun TxnRow(u: UiTxn, showDate: Boolean = true, onClick: (() -> Unit)? = null) {
    val fc = LocalFinanceColors.current
    val t = u.t
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(t.category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(u.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    t.possibleDuplicate -> Tag("Possible duplicate", fc.warnText, outlined = true)
                    t.category == Category.UNCATEGORISED -> Tag("Tap to sort", MaterialTheme.colorScheme.primary, outlined = true)
                    t.category == Category.TRANSFER -> Tag("Own transfer", Category.TRANSFER.color())
                }
                Text(u.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val credit = t.type == TxnType.CREDIT
            Text(
                (if (credit) "+" else "−") + Format.money(t.amount),
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    t.possibleDuplicate -> MaterialTheme.colorScheme.onSurfaceVariant
                    credit -> fc.income
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(if (showDate) Format.shortDate(t.timestamp) else Format.time(t.timestamp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RowsDivider() = HorizontalDivider(color = LocalFinanceColors.current.cardBorder)
