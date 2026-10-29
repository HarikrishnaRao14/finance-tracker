package com.harikrishnarao.financetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.harikrishnarao.financetracker.R
import com.harikrishnarao.financetracker.data.AppTheme

/** Bundled font, so the phone's font style (e.g. Samsung handwriting fonts) never changes the app's look. */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold),
    headlineMedium = headlineMedium.copy(fontFamily = f, fontWeight = FontWeight.Bold),
    headlineSmall = headlineSmall.copy(fontFamily = f, fontWeight = FontWeight.Bold),
    titleLarge = titleLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    titleMedium = titleMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    titleSmall = titleSmall.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    bodyLarge = bodyLarge.copy(fontFamily = f), bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f), labelLarge = labelLarge.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)

/** Big amount style used on the cash flow card. */
val AmountStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp)

/** Extra colours Material has no slot for. */
@Immutable
data class FinanceColors(
    val spending: Color,
    val income: Color,
    val warnBg: Color,
    val warnText: Color,
    val okBg: Color,
    val okText: Color,
    val track: Color,
    val glowA: Color,
    val glowB: Color,
    val cardBorder: Color,
    val inset: Color,
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceColors(Color.Red, Color.Cyan, Color.DarkGray, Color.Yellow, Color.DarkGray, Color.Cyan, Color.Gray, Color.Blue, Color.Magenta, Color.Gray, Color.Black)
}

// Shared deep-black base (Netflix-like): near-black background, slightly lifted cards.
private val Bg = Color(0xFF07070B)
private val Surface1 = Color(0xFF121219)
private val Surface2 = Color(0xFF1A1A24)
private val Inset = Color(0xFF0C0C12)
private val Ink = Color(0xFFF4F4F8)
private val Muted = Color(0xFFA0A3B4)
private val Line = Color(0xFF262633)

private fun scheme(accent: Color, onAccent: Color, accentSoft: Color, onAccentSoft: Color, secondary: Color) = darkColorScheme(
    primary = accent, onPrimary = onAccent,
    primaryContainer = accentSoft, onPrimaryContainer = onAccentSoft,
    secondary = secondary, onSecondary = Color.White,
    secondaryContainer = accentSoft, onSecondaryContainer = onAccentSoft,
    background = Bg, onBackground = Ink,
    surface = Surface1, onSurface = Ink,
    surfaceVariant = Surface2, onSurfaceVariant = Muted,
    surfaceContainerLowest = Bg, surfaceContainerLow = Surface1, surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2, surfaceContainerHighest = Color(0xFF22222E),
    inverseSurface = Ink, inverseOnSurface = Bg,
    outline = Color(0xFF3A3A4A), outlineVariant = Line,
    error = Color(0xFFFF6B81), onError = Color.Black,
)

private fun extras(glowA: Color, glowB: Color) = FinanceColors(
    spending = Color(0xFFFF5C7A),
    income = Color(0xFF5EEAD4), // soft teal: green kept quiet on purpose
    warnBg = Color(0xFF2A1F0E), warnText = Color(0xFFFBBF24),
    okBg = Color(0xFF0F2328), okText = Color(0xFF7EE7DA),
    track = Color(0xFF262633),
    glowA = glowA, glowB = glowB,
    cardBorder = Line, inset = Inset,
)

private val cobalt = scheme(Color(0xFF4F8CFF), Color(0xFF04122E), Color(0xFF16254A), Color(0xFFC9DAFF), Color(0xFF8B5CF6))
private val crimson = scheme(Color(0xFFE5303A), Color.White, Color(0xFF3A1216), Color(0xFFFFC9CC), Color(0xFFFF8A3D))
private val nebula = scheme(Color(0xFFA66CFF), Color(0xFF16062E), Color(0xFF2A1748), Color(0xFFE6D6FF), Color(0xFFEC4899))

private fun AppTheme.colors() = when (this) {
    AppTheme.COBALT -> cobalt to extras(Color(0xFF2D5BFF), Color(0xFF7C3AED))
    AppTheme.CRIMSON -> crimson to extras(Color(0xFFE50914), Color(0xFF7C1D3A))
    AppTheme.NEBULA -> nebula to extras(Color(0xFF8B5CF6), Color(0xFFDB2777))
}

@Composable
fun FinanceTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val (scheme, extra) = theme.colors()
    CompositionLocalProvider(LocalFinanceColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = Typography().withFont(Poppins), content = content)
    }
}

/** Swatches for the theme picker: background, card, accent. */
fun AppTheme.previewColors(): Triple<Color, Color, Color> = Triple(Bg, Surface2, colors().first.primary)
