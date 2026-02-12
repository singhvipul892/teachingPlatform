package com.maths.teacher.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = OnPrimary,
    secondary = SecondaryTealLight,
    onSecondary = OnSecondary,
    tertiary = AccentSaffronLight,
    onTertiary = OnTertiary,
    background = BackgroundMainDark,
    onBackground = OnBackgroundDark,
    surface = BackgroundCardDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = BackgroundCardDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = ErrorDark,
    onError = OnError
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimary,
    secondary = SecondaryTeal,
    onSecondary = OnSecondary,
    tertiary = AccentSaffron,
    onTertiary = OnTertiary,
    background = BackgroundMain,
    onBackground = OnBackground,
    surface = BackgroundCard,
    onSurface = OnSurface,
    surfaceVariant = BackgroundCard,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = Outline,
    error = Error,
    onError = OnError
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                window.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
