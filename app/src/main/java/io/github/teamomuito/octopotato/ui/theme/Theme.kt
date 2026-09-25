package io.github.teamomuito.octopotato.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.teamomuito.octopotato.R

private val Light = lightColorScheme(
    primary = Color(0xFFD9437F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD6E7),
    onPrimaryContainer = Color(0xFF4A2340),
    secondary = Color(0xFF8E6BE8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E0FF),
    onSecondaryContainer = Color(0xFF2F1A63),
    tertiary = Color(0xFF238F75),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCF3E6),
    onTertiaryContainer = Color(0xFF0D3B30),
    background = Color(0xFFFFF5F9),
    onBackground = Color(0xFF3A2233),
    surface = Color(0xFFFFF5F9),
    onSurface = Color(0xFF3A2233),
    surfaceVariant = Color(0xFFFBE3EE),
    onSurfaceVariant = Color(0xFF7A5A6C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFEEF5),
    surfaceContainer = Color(0xFFFFE8F1),
    surfaceContainerHigh = Color(0xFFFFE1EC),
    surfaceContainerHighest = Color(0xFFFBDAE7),
    outline = Color(0xFFD99BB8),
    outlineVariant = Color(0xFFF1CFDE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8CBE),
    onPrimary = Color(0xFF4A1030),
    primaryContainer = Color(0xFF6A2A4C),
    onPrimaryContainer = Color(0xFFFFD6E7),
    secondary = Color(0xFFC9B6FF),
    onSecondary = Color(0xFF2F1A63),
    secondaryContainer = Color(0xFF45337A),
    onSecondaryContainer = Color(0xFFE9E0FF),
    tertiary = Color(0xFF7FDCC0),
    onTertiary = Color(0xFF0D3B30),
    tertiaryContainer = Color(0xFF1F4B40),
    onTertiaryContainer = Color(0xFFCCF3E6),
    background = Color(0xFF1D1420),
    onBackground = Color(0xFFF6E6EE),
    surface = Color(0xFF1D1420),
    onSurface = Color(0xFFF6E6EE),
    surfaceVariant = Color(0xFF3A2A3A),
    onSurfaceVariant = Color(0xFFD8BFCC),
    surfaceContainerLowest = Color(0xFF170F19),
    surfaceContainerLow = Color(0xFF261B29),
    surfaceContainer = Color(0xFF2C2030),
    surfaceContainerHigh = Color(0xFF342638),
    surfaceContainerHighest = Color(0xFF3D2E41),
    outline = Color(0xFF8E6F80),
    outlineVariant = Color(0xFF4E3A4A),
)

/** Colors for the little kind labels. Pastel on purpose, they sit on top of screenshots. */
object Pastel {
    val lavender = Color(0xFFE9E0FF)
    val lavenderInk = Color(0xFF4B2F99)
    val sky = Color(0xFFD6EEFF)
    val skyInk = Color(0xFF12507A)
    val mint = Color(0xFFCCF3E6)
    val mintInk = Color(0xFF0F5A43)
    val butter = Color(0xFFFFF0C2)
    val butterInk = Color(0xFF6E5200)
}

val Sniglet = FontFamily(
    Font(R.font.sniglet_regular, FontWeight.Normal),
    Font(R.font.sniglet_extrabold, FontWeight.ExtraBold),
)

private val base = Typography()

private val OctoType = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Sniglet, fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Sniglet, fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Sniglet, fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.copy(fontFamily = Sniglet, fontWeight = FontWeight.ExtraBold),
    titleMedium = base.titleMedium.copy(fontFamily = Sniglet, fontWeight = FontWeight.Normal, fontSize = 17.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Sniglet, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelMedium = base.labelMedium.copy(fontFamily = Sniglet, fontWeight = FontWeight.Normal),
    labelSmall = base.labelSmall.copy(fontFamily = Sniglet, fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

private val OctoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun OctoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = OctoType,
        shapes = OctoShapes,
        content = content,
    )
}
