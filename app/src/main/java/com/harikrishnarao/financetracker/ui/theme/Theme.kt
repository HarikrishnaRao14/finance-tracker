package com.harikrishnarao.financetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.harikrishnarao.financetracker.data.AppTheme

/** Extra colours Material does not have a slot for. */
@Immutable
data class FinanceColors(
    val income: Color,
    val warnBg: Color,
    val warnText: Color,
    val okBg: Color,
    val okText: Color,
    val track: Color,
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceColors(Color.Green, Color.Yellow, Color.Black, Color.Green, Color.Black, Color.LightGray)
}

private val forest = lightColorScheme(
    primary = Color(0xFF1E6B52), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EFE9), onPrimaryContainer = Color(0xFF15513E),
    secondary = Color(0xFF6D8FB8), onSecondary = Color.White,
    background = Color(0xFFF4F5F1), onBackground = Color(0xFF17201C),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF17201C),
    surfaceVariant = Color(0xFFEEF0EB), onSurfaceVariant = Color(0xFF5B6660),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF7F8F5), outline = Color(0xFFC9CFC9), outlineVariant = Color(0xFFE2E5DF),
    error = Color(0xFFB3261E),
)
private val forestExtra = FinanceColors(
    income = Color(0xFF1E6B52), warnBg = Color(0xFFF8EADC), warnText = Color(0xFF8A4614),
    okBg = Color(0xFFE3EFE9), okText = Color(0xFF15513E), track = Color(0xFFEEF0EB),
)

private val midnight = darkColorScheme(
    primary = Color(0xFF5CC8A0), onPrimary = Color(0xFF0B2A1F),
    primaryContainer = Color(0xFF1F3A30), onPrimaryContainer = Color(0xFFBFEBD8),
    secondary = Color(0xFF8FB2DB), onSecondary = Color(0xFF0E2238),
    background = Color(0xFF101418), onBackground = Color(0xFFEEF1EE),
    surface = Color(0xFF1C232A), onSurface = Color(0xFFEEF1EE),
    surfaceVariant = Color(0xFF263038), onSurfaceVariant = Color(0xFFA3ADB5),
    surfaceContainer = Color(0xFF1C232A), surfaceContainerLow = Color(0xFF1C232A),
    surfaceContainerHigh = Color(0xFF232C34), outline = Color(0xFF4A5560), outlineVariant = Color(0xFF2C363F),
    error = Color(0xFFF2B8B5),
)
private val midnightExtra = FinanceColors(
    income = Color(0xFF5CC8A0), warnBg = Color(0xFF3A2A1A), warnText = Color(0xFFF5C08A),
    okBg = Color(0xFF1F3A30), okText = Color(0xFFBFEBD8), track = Color(0xFF2C363F),
)

private val saffron = lightColorScheme(
    primary = Color(0xFFA84A16), onPrimary = Color.White,
    primaryContainer = Color(0xFFF8E3D3), onPrimaryContainer = Color(0xFF6B2C08),
    secondary = Color(0xFF7A6A2A), onSecondary = Color.White,
    background = Color(0xFFFBF5EC), onBackground = Color(0xFF2A1D12),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF2A1D12),
    surfaceVariant = Color(0xFFF3EADF), onSurfaceVariant = Color(0xFF6B5A4A),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFCF8F2), outline = Color(0xFFD9CBB9), outlineVariant = Color(0xFFEDE2D4),
    error = Color(0xFFB3261E),
)
private val saffronExtra = FinanceColors(
    income = Color(0xFF2F7A4A), warnBg = Color(0xFFFBE4CF), warnText = Color(0xFF8A3A0C),
    okBg = Color(0xFFE6F0E3), okText = Color(0xFF2B5A31), track = Color(0xFFF3EADF),
)

@Composable
fun FinanceTheme(theme: AppTheme, followSystemDark: Boolean, content: @Composable () -> Unit) {
    val effective = if (followSystemDark && isSystemInDarkTheme()) AppTheme.MIDNIGHT else theme
    val (scheme: ColorScheme, extra: FinanceColors) = when (effective) {
        AppTheme.FOREST -> forest to forestExtra
        AppTheme.MIDNIGHT -> midnight to midnightExtra
        AppTheme.SAFFRON -> saffron to saffronExtra
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalFinanceColors provides extra) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

fun AppTheme.previewColors(): Triple<Color, Color, Color> = when (this) {
    AppTheme.FOREST -> Triple(forest.background, forest.surfaceVariant, forest.primary)
    AppTheme.MIDNIGHT -> Triple(midnight.background, midnight.surface, midnight.primary)
    AppTheme.SAFFRON -> Triple(saffron.background, saffron.surfaceVariant, saffron.primary)
}

fun isDarkTheme(theme: AppTheme) = theme == AppTheme.MIDNIGHT
