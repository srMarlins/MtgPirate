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
// FANTASY PIXEL ART COLOR PALETTE
// ========================================

// Dark Theme - Enchanted Night / Mystical Dungeon Aesthetic
private val PixelDarkPrimary = Color(0xFFB794F6) // Mystical purple (arcane magic)
private val PixelDarkPrimaryVariant = Color(0xFF805AD5) // Deep enchanted purple
private val PixelDarkSecondary = Color(0xFF63B3ED) // Crystal blue (magic crystals)
private val PixelDarkSecondaryVariant = Color(0xFF4299E1) // Deep sapphire blue
private val PixelDarkBackground = Color(0xFF1A0F2E) // Deep purple-black night
private val PixelDarkSurface = Color(0xFF2D1B4E) // Mystical surface (darker purple)
private val PixelDarkError = Color(0xFFFC8181) // Soft crimson (dragon fire)
private val PixelDarkOnPrimary = Color(0xFF1A0F2E) // Dark text on glowing purple
private val PixelDarkOnSecondary = Color(0xFF1A0F2E) // Dark text on crystal blue
private val PixelDarkOnBackground = Color(0xFFF7FAFC) // Moonlight white text
private val PixelDarkOnSurface = Color(0xFFE9D8FD) // Lavender mist text
private val PixelDarkOnError = Color(0xFF1A0F2E) // Dark text on error

// Light Theme - Enchanted Forest / Fairy Tale Aesthetic
private val PixelLightPrimary = Color(0xFF38A169) // Emerald forest green
private val PixelLightPrimaryVariant = Color(0xFF2F855A) // Deep forest shadow
private val PixelLightSecondary = Color(0xFFED8936) // Autumn gold (magic amber)
private val PixelLightSecondaryVariant = Color(0xFFDD6B20) // Deep golden amber
private val PixelLightBackground = Color(0xFFF0FFF4) // Morning mist (pale mint)
private val PixelLightSurface = Color(0xFFE6FFFA) // Fairy dust (pale cyan)
private val PixelLightError = Color(0xFFC53030) // Ruby red warning
private val PixelLightOnPrimary = Color(0xFFF0FFF4) // Light text on forest green
private val PixelLightOnSecondary = Color(0xFF1A202C) // Dark text on gold
private val PixelLightOnBackground = Color(0xFF1A202C) // Deep shadow text
private val PixelLightOnSurface = Color(0xFF1A365D) // Deep blue-grey text
private val PixelLightOnError = Color(0xFFFFF5F5) // Light text on ruby

// Additional Fantasy Pixel Art Colors
val PixelAccent1 = Color(0xFFFBD38D) // Golden coins (treasure)
val PixelAccent2 = Color(0xFFFC8181) // Phoenix fire (warm glow)
val PixelAccent3 = Color(0xFF68D391) // Life energy (healing green)
val PixelAccent4 = Color(0xFFB794F6) // Mystical aura (magic purple)
val PixelAccent5 = Color(0xFF76E4F7) // Ice crystal (frost blue)
val PixelAccent6 = Color(0xFFFBB6CE) // Rose quartz (soft pink)
val PixelGlow = Color(0xFFB794F6) // Purple arcane glow
val PixelShadow = Color(0xFF1A0F2E) // Deep mystical shadow

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

