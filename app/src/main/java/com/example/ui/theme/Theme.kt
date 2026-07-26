package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = lightColorScheme(
    primary = PremiumGold,
    secondary = ImmersiveHeaderBlue,
    tertiary = LightGold,
    background = ImmersiveBackground,
    surface = ImmersiveHeaderBlue,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    primaryContainer = ImmersiveCardGradientStart,
    onPrimaryContainer = TextPrimary,
    secondaryContainer = ImmersiveNavBlue,
    onSecondaryContainer = PremiumGold
)

private val LightColorScheme = lightColorScheme(
    primary = PremiumGold,
    secondary = ImmersiveHeaderBlue,
    tertiary = LightGold,
    background = ImmersiveBackground,
    surface = ImmersiveHeaderBlue,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    primaryContainer = ImmersiveCardGradientStart,
    onPrimaryContainer = TextPrimary,
    secondaryContainer = ImmersiveNavBlue,
    onSecondaryContainer = PremiumGold
)

@Composable
fun ScoreboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme // Force Light Scheme as requested by user

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
