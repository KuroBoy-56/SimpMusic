package com.maxrave.simpmusic.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.manager.DataStoreManager
import org.koin.compose.koinInject
import java.util.Calendar

val DarkColors =
    darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        secondary = md_theme_dark_secondary,
        onSecondary = md_theme_dark_onSecondary,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        tertiary = md_theme_dark_tertiary,
        onTertiary = md_theme_dark_onTertiary,
        tertiaryContainer = md_theme_dark_tertiaryContainer,
        onTertiaryContainer = md_theme_dark_onTertiaryContainer,
        error = md_theme_dark_error,
        errorContainer = md_theme_dark_errorContainer,
        onError = md_theme_dark_onError,
        onErrorContainer = md_theme_dark_onErrorContainer,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        outline = md_theme_dark_outline,
        inverseOnSurface = md_theme_dark_inverseOnSurface,
        inverseSurface = md_theme_dark_inverseSurface,
        inversePrimary = md_theme_dark_inversePrimary,
        surfaceTint = md_theme_dark_surfaceTint,
        outlineVariant = md_theme_dark_outlineVariant,
        scrim = md_theme_dark_scrim,
    )

val LightColors =
    lightColorScheme(
        primary = Color(0xFF1C1B1F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEBEBEB),
        onPrimaryContainer = Color(0xFF000000),
        secondary = Color(0xFF4D4D4D),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8E8E8),
        onSecondaryContainer = Color(0xFF1C1B1F),
        tertiary = Color(0xFF1C1B1F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD9D9D9),
        onTertiaryContainer = Color(0xFF000000),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF79747E),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        inverseOnSurface = Color(0xFFF4EFF4),
        inverseSurface = Color(0xFF313033),
        inversePrimary = Color(0xFFD0BCFF),
        surfaceTint = Color(0xFF1C1B1F),
        outlineVariant = Color(0xFFCAC4D0),
        scrim = Color(0xFF000000),
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val dataStoreManager: DataStoreManager = koinInject()
    val autoNightModeString by dataStoreManager.getString("auto_night_mode").collectAsStateWithLifecycle(initialValue = "TRUE")

    val forceDarkMode = autoNightModeString != "FALSE"

    val isDarkTheme = if (forceDarkMode) {
        true
    } else {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val isDayTime = (hour > 6 || (hour == 6 && minute >= 30)) && (hour < 18 || (hour == 18 && minute < 30))
        !isDayTime
    }

    val colors = if (isDarkTheme) DarkColors else LightColors

    MaterialExpressiveTheme(
        colorScheme = colors,
        content = {
            CompositionLocalProvider(
                LocalContentColor provides colors.onBackground,
                content,
            )
        },
        typography = typo(),
    )
}