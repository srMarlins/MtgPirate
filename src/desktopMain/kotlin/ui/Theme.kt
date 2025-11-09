package ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ========================================
// RETRO PIXEL ART COLOR PALETTE
// ========================================

// Dark Theme - CRT Monitor / Arcade Cabinet Aesthetic
private val PixelDarkPrimary = Color(0xFF00FFFF) // Bright cyan (classic terminal)
private val PixelDarkPrimaryVariant = Color(0xFF00AAAA) // Deeper cyan
private val PixelDarkSecondary = Color(0xFFFF00FF) // Hot magenta (neon accent)
private val PixelDarkSecondaryVariant = Color(0xFFAA00AA) // Deeper magenta
private val PixelDarkBackground = Color(0xFF0A0E27) // Deep space blue-black
private val PixelDarkSurface = Color(0xFF1A1F3A) // Slightly lighter surface
private val PixelDarkError = Color(0xFFFF3366) // Neon red
private val PixelDarkOnPrimary = Color(0xFF000000) // Black text on cyan
private val PixelDarkOnSecondary = Color(0xFF000000) // Black text on magenta
private val PixelDarkOnBackground = Color(0xFF00FF88) // Bright green text (terminal style)
private val PixelDarkOnSurface = Color(0xFFDDFFDD) // Light greenish text
private val PixelDarkOnError = Color(0xFF000000) // Black text on error

// Light Theme - Game Boy / Retro Handheld Aesthetic
private val PixelLightPrimary = Color(0xFF0F380F) // Dark green (Game Boy dark)
private val PixelLightPrimaryVariant = Color(0xFF306230) // Medium green
private val PixelLightSecondary = Color(0xFF8BAC0F) // Lime green accent
private val PixelLightSecondaryVariant = Color(0xFF9BBC0F) // Brighter lime
private val PixelLightBackground = Color(0xFF9BBC0F) // Game Boy light green background
private val PixelLightSurface = Color(0xFF8BAC0F) // Slightly darker surface
private val PixelLightError = Color(0xFF8B0000) // Dark red
private val PixelLightOnPrimary = Color(0xFFDDFFDD) // Light text on dark green
private val PixelLightOnSecondary = Color(0xFF0F380F) // Dark text on lime
private val PixelLightOnBackground = Color(0xFF0F380F) // Dark green text
private val PixelLightOnSurface = Color(0xFF0F380F) // Dark green text on surface
private val PixelLightOnError = Color(0xFFFFFFFF) // White text on error

// Additional Pixel Art Colors
val PixelAccent1 = Color(0xFFFFFF00) // Bright yellow (coins/stars)
val PixelAccent2 = Color(0xFFFF6600) // Orange (fire/warning)
val PixelAccent3 = Color(0xFF00FF00) // Lime green (success)
val PixelAccent4 = Color(0xFF8800FF) // Purple (magic/special)
val PixelGlow = Color(0xFF00FFFF) // Cyan glow
val PixelShadow = Color(0xFF000033) // Deep shadow

val AppDarkColors = darkColors(
    primary = PixelDarkPrimary,
    primaryVariant = PixelDarkPrimaryVariant,
    secondary = PixelDarkSecondary,
    secondaryVariant = PixelDarkSecondaryVariant,
    background = PixelDarkBackground,
    surface = PixelDarkSurface,
    error = PixelDarkError,
    onPrimary = PixelDarkOnPrimary,
    onSecondary = PixelDarkOnSecondary,
    onBackground = PixelDarkOnBackground,
    onSurface = PixelDarkOnSurface,
    onError = PixelDarkOnError
)

val AppLightColors = lightColors(
    primary = PixelLightPrimary,
    primaryVariant = PixelLightPrimaryVariant,
    secondary = PixelLightSecondary,
    secondaryVariant = PixelLightSecondaryVariant,
    background = PixelLightBackground,
    surface = PixelLightSurface,
    error = PixelLightError,
    onPrimary = PixelLightOnPrimary,
    onSecondary = PixelLightOnSecondary,
    onBackground = PixelLightOnBackground,
    onSurface = PixelLightOnSurface,
    onError = PixelLightOnError
)

// ========================================
// PIXEL TYPOGRAPHY
// ========================================
// Using monospace font to emulate pixel font appearance
val PixelTypography = Typography(
    h1 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 2.sp
    ),
    h2 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.5.sp
    ),
    h3 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 1.sp
    ),
    h4 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 1.sp
    ),
    h5 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 0.5.sp
    ),
    h6 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    body1 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    body2 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.25.sp
    ),
    button = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.sp
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    ),
    subtitle1 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp
    ),
    subtitle2 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp
    )
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
        typography = PixelTypography,
        content = content
    )
}

