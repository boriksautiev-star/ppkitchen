package com.ppkitchen.ui

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF4CAF50),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF002106),
    tertiary = Color(0xFFE65100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFCCBC),
    onTertiaryContainer = Color(0xFF311300),
    error = Color(0xFFB00020),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FBF3),
    onBackground = Color(0xFF191D17),
    surface = Color(0xFFF7FBF3),
    onSurface = Color(0xFF191D17),
    surfaceVariant = Color(0xFFDDE5D8),
    onSurfaceVariant = Color(0xFF41493F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003910),
    primaryContainer = Color(0xFF005318),
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF003910),
    secondaryContainer = Color(0xFF2E5B33),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF4D2600),
    tertiaryContainer = Color(0xFF6E3900),
    onTertiaryContainer = Color(0xFFFFCCBC),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101410),
    onBackground = Color(0xFFE1E4DD),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE1E4DD),
    surfaceVariant = Color(0xFF41493F),
    onSurfaceVariant = Color(0xFFC1C9BD)
)

@Composable
fun PPKitchenTheme(content: @Composable () -> Unit) {
    // Читаем тему из наблюдаемого состояния Compose — она обновляется мгновенно
    val themeMode = ThemeState.mode

    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}