// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Branded fallback for phones without dynamic color (Android 11 and older).
// Seed: a deep teal, picked to read as "screen glow" without clashing with app icon tiles.
// Every role is set, tonal-spot tones from that seed: a role left out falls back to
// Material's stock purple.
private val LightBrand = lightColorScheme(
    primary = Color(0xFF00696B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F2),
    onPrimaryContainer = Color(0xFF002020),
    inversePrimary = Color(0xFF80D4D5),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E8),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    background = Color(0xFFF4FBFA),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FBFA),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE4E4),
    onSurfaceVariant = Color(0xFF3F4948),
    inverseSurface = Color(0xFF2B3232),
    inverseOnSurface = Color(0xFFECF2F1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C8),
    surfaceBright = Color(0xFFF4FBFA),
    surfaceContainer = Color(0xFFE9EFEE),
    surfaceContainerHigh = Color(0xFFE3E9E9),
    surfaceContainerHighest = Color(0xFFDDE4E3),
    surfaceContainerLow = Color(0xFFEEF5F4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD5DBDB),
    primaryFixed = Color(0xFF9CF1F2),
    primaryFixedDim = Color(0xFF80D4D5),
    onPrimaryFixed = Color(0xFF002020),
    onPrimaryFixedVariant = Color(0xFF004F50),
    secondaryFixed = Color(0xFFCCE8E7),
    secondaryFixedDim = Color(0xFFB0CCCB),
    onSecondaryFixed = Color(0xFF051F1F),
    onSecondaryFixedVariant = Color(0xFF324B4B),
    tertiaryFixed = Color(0xFFD3E4FF),
    tertiaryFixedDim = Color(0xFFB3C8E8),
    onTertiaryFixed = Color(0xFF041C35),
    onTertiaryFixedVariant = Color(0xFF334863),
)

private val DarkBrand = darkColorScheme(
    primary = Color(0xFF80D4D5),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F50),
    onPrimaryContainer = Color(0xFF9CF1F2),
    inversePrimary = Color(0xFF00696B),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E8),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC8C8),
    inverseSurface = Color(0xFFDDE4E3),
    inverseOnSurface = Color(0xFF2B3232),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899392),
    outlineVariant = Color(0xFF3F4948),
    surfaceBright = Color(0xFF343A3A),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF2F3636),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceDim = Color(0xFF0E1415),
    primaryFixed = Color(0xFF9CF1F2),
    primaryFixedDim = Color(0xFF80D4D5),
    onPrimaryFixed = Color(0xFF002020),
    onPrimaryFixedVariant = Color(0xFF004F50),
    secondaryFixed = Color(0xFFCCE8E7),
    secondaryFixedDim = Color(0xFFB0CCCB),
    onSecondaryFixed = Color(0xFF051F1F),
    onSecondaryFixedVariant = Color(0xFF324B4B),
    tertiaryFixed = Color(0xFFD3E4FF),
    tertiaryFixedDim = Color(0xFFB3C8E8),
    onTertiaryFixed = Color(0xFF041C35),
    onTertiaryFixedVariant = Color(0xFF334863),
)

/** Numbers come first: the app font with tabular figures, so values line up and don't jitter as they update. */
val StatNumber = TextStyle(fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = "tnum")
val SmallNumber = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TellyscopeTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkBrand
        else -> LightBrand
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        typography = Typography(),
        content = content,
    )
}
