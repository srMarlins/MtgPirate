package ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Modern Dark Theme Colors - Inspired by GitHub Dark and VS Code Dark+
private val DarkPrimary = Color(0xFF6B9EFF) // Soft blue
private val DarkPrimaryVariant = Color(0xFF4A7FD6) // Deeper blue
private val DarkSecondary = Color(0xFF7DD3C0) // Mint/teal accent
private val DarkSecondaryVariant = Color(0xFF5FB8A5) // Deeper teal
private val DarkBackground = Color(0xFF0D1117) // Deep dark background
private val DarkSurface = Color(0xFF161B22) // Card/surface background
private val DarkError = Color(0xFFFF6B6B) // Soft red
private val DarkOnPrimary = Color(0xFF000000) // Text on primary
private val DarkOnSecondary = Color(0xFF000000) // Text on secondary
private val DarkOnBackground = Color(0xFFE6EDF3) // Main text color
private val DarkOnSurface = Color(0xFFE6EDF3) // Text on surfaces
private val DarkOnError = Color(0xFF000000) // Text on error

// Light Theme Colors - Clean and modern
private val LightPrimary = Color(0xFF0969DA) // GitHub blue
private val LightPrimaryVariant = Color(0xFF0550AE) // Deeper blue
private val LightSecondary = Color(0xFF1F883D) // Green accent
private val LightSecondaryVariant = Color(0xFF116329) // Deeper green
private val LightBackground = Color(0xFFFFFFFF) // Pure white
private val LightSurface = Color(0xFFF6F8FA) // Light gray surface
private val LightError = Color(0xFFCF222E) // Red
private val LightOnPrimary = Color(0xFFFFFFFF) // White text on primary
private val LightOnSecondary = Color(0xFFFFFFFF) // White text on secondary
private val LightOnBackground = Color(0xFF1F2328) // Dark text
private val LightOnSurface = Color(0xFF1F2328) // Dark text on surfaces
private val LightOnError = Color(0xFFFFFFFF) // White text on error

val AppDarkColors = darkColors(
    primary = DarkPrimary,
    primaryVariant = DarkPrimaryVariant,
    secondary = DarkSecondary,
    secondaryVariant = DarkSecondaryVariant,
    background = DarkBackground,
    surface = DarkSurface,
    error = DarkError,
    onPrimary = DarkOnPrimary,
    onSecondary = DarkOnSecondary,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    onError = DarkOnError
)

val AppLightColors = lightColors(
    primary = LightPrimary,
    primaryVariant = LightPrimaryVariant,
    secondary = LightSecondary,
    secondaryVariant = LightSecondaryVariant,
    background = LightBackground,
    surface = LightSurface,
    error = LightError,
    onPrimary = LightOnPrimary,
    onSecondary = LightOnSecondary,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    onError = LightOnError
)

@Composable
fun AppTheme(
    darkTheme: Boolean = true, // Default to dark theme
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        AppDarkColors
    } else {
        AppLightColors
    }

    MaterialTheme(
        colors = colors,
        content = content
    )
}

