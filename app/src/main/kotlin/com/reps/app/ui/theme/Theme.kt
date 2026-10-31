package com.reps.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.reps.app.data.model.ThemeMode

object RepsColors {
    val Ember = Color(0xFFFF7A45)
    val EmberDeep = Color(0xFFD14F1A)
    val Gold = Color(0xFFFFC857)
    val Mint = Color(0xFF6EDCA8)
    val Coral = Color(0xFFFF6B6B)
}

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = RepsColors.Ember,
    onPrimary = Color(0xFF1B0A00),
    primaryContainer = Color(0xFF4A1D0A),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = RepsColors.Mint,
    onSecondary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF14382A),
    onSecondaryContainer = Color(0xFFBDF3D7),
    tertiary = RepsColors.Gold,
    onTertiary = Color(0xFF3E2E00),
    tertiaryContainer = Color(0xFF4B3A00),
    onTertiaryContainer = Color(0xFFFFE8A8),
    error = RepsColors.Coral,
    onError = Color(0xFF410002),
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF0E1014),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF262B32),
    onSurfaceVariant = Color(0xFFA7AEB9),
    surfaceContainerLowest = Color(0xFF090B0E),
    surfaceContainerLow = Color(0xFF14171C),
    surfaceContainer = Color(0xFF181C22),
    surfaceContainerHigh = Color(0xFF1F242B),
    surfaceContainerHighest = Color(0xFF272D35),
    outline = Color(0xFF3B434D),
    outlineVariant = Color(0xFF2A3038),
    inverseSurface = Color(0xFFECEFF4),
    inverseOnSurface = Color(0xFF1B1F25),
    inversePrimary = RepsColors.EmberDeep,
    scrim = Color(0xFF000000)
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = RepsColors.EmberDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A1200),
    secondary = Color(0xFF1F7A54),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBDF3D7),
    onSecondaryContainer = Color(0xFF00391F),
    tertiary = Color(0xFF7A5B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8A8),
    onTertiaryContainer = Color(0xFF3E2E00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF1B1F25),
    surface = Color(0xFFF7F7F9),
    onSurface = Color(0xFF1B1F25),
    surfaceVariant = Color(0xFFE4E6EB),
    onSurfaceVariant = Color(0xFF4A515B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F2F5),
    surfaceContainer = Color(0xFFECEDF1),
    surfaceContainerHigh = Color(0xFFE5E7EC),
    surfaceContainerHighest = Color(0xFFDFE1E7),
    outline = Color(0xFFB4BAC4),
    outlineVariant = Color(0xFFD4D8DF),
    inverseSurface = Color(0xFF2F343B),
    inverseOnSurface = Color(0xFFF0F1F5),
    inversePrimary = RepsColors.Ember
)

val RepsTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

/** Big tabular numbers used for weights and timers. */
val NumberStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp)

@Composable
fun RepsTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = RepsTypography, content = content)
}
