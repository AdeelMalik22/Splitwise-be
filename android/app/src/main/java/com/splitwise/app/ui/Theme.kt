package com.splitwise.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitwise.app.R

/** Colors from the SplitEase design that have no Material 3 slot. */
data class SplitColors(
    val fg2: Color, val fg3: Color, val input: Color, val border: Color,
    val primaryBg: Color, val tealRing: Color,
    val owed: Color, val owedBg: Color, val owedRing: Color,
    val owe: Color, val oweBg: Color, val oweRing: Color,
    val errorBg: Color,
)

private val LightSplit = SplitColors(
    fg2 = Color(0xFF475569), fg3 = Color(0xFF94A3B8), input = Color(0xFFF1F5F9), border = Color(0xFFE2E8F0),
    primaryBg = Color(0xFFF0FDFA), tealRing = Color(0xFFCCFBF1),
    owed = Color(0xFF16A34A), owedBg = Color(0xFFF0FDF4), owedRing = Color(0xFFBBF7D0),
    owe = Color(0xFFB45309), oweBg = Color(0xFFFFFBEB), oweRing = Color(0xFFFDE68A),
    errorBg = Color(0xFFFEF2F2),
)
private val DarkSplit = SplitColors(
    fg2 = Color(0xFF94A3B8), fg3 = Color(0xFF64748B), input = Color(0xFF1E2D42), border = Color(0xFF1E2D42),
    primaryBg = Color(0xFF0D2E2B), tealRing = Color(0xFF134E4A),
    owed = Color(0xFF4ADE80), owedBg = Color(0xFF052E16), owedRing = Color(0xFF166534),
    owe = Color(0xFFFCD34D), oweBg = Color(0xFF1C1408), oweRing = Color(0xFF92400E),
    errorBg = Color(0xFF1C0A0A),
)

val LocalSplit = staticCompositionLocalOf { LightSplit }
val Teal700 = Color(0xFF0F766E)

private val LightScheme = lightColorScheme(
    primary = Teal700, onPrimary = Color.White,
    background = Color(0xFFF8FAFB), onBackground = Color(0xFF0F172A),
    surface = Color.White, onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0), error = Color(0xFFDC2626),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF14B8A6), onPrimary = Color.White,
    background = Color(0xFF0B1120), onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF131E30), onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E2D42), onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF1E2D42), error = Color(0xFFF87171),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

private fun style(size: Int, weight: Int, spacing: Double = 0.0, line: Int? = null) = TextStyle(
    fontFamily = Inter, fontSize = size.sp, fontWeight = FontWeight(weight),
    letterSpacing = spacing.sp, lineHeight = (line ?: (size * 1.3).toInt()).sp,
)

private val SplitTypography = Typography(
    displaySmall = style(28, 700, -0.8),
    headlineSmall = style(22, 700, -0.55),
    titleLarge = style(17, 600, -0.35),
    titleMedium = style(15, 600, -0.2),
    titleSmall = style(13, 600),
    bodyLarge = style(15, 400),
    bodyMedium = style(14, 400),
    bodySmall = style(12, 400),
    labelLarge = style(16, 600, -0.15),
    labelMedium = style(12, 600),
    labelSmall = style(11, 600, 0.6),
)

private val SplitShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

/** [darkOverride] null follows the system setting. */
@Composable
fun SplitEaseTheme(darkOverride: Boolean?, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    CompositionLocalProvider(LocalSplit provides if (dark) DarkSplit else LightSplit) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = SplitTypography,
            shapes = SplitShapes,
            content = content,
        )
    }
}

val MaterialTheme.split: SplitColors @Composable get() = LocalSplit.current
