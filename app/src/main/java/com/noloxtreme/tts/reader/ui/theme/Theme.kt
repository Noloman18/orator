package com.noloxtreme.tts.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = RomanNightPrimary,
    onPrimary = Color(0xFF5A1B17),
    primaryContainer = RomanNightPrimaryContainer,
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = RomanNightSecondary,
    onSecondary = Color(0xFF37311E),
    secondaryContainer = RomanNightSecondaryContainer,
    onSecondaryContainer = Color(0xFFEFE4BD),
    tertiary = RomanNightTertiary,
    onTertiary = Color(0xFF402A0A),
    tertiaryContainer = RomanNightTertiaryContainer,
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = RomanNightBackground,
    onBackground = Color(0xFFF2E3D8),
    surface = RomanNightBackground,
    onSurface = Color(0xFFF2E3D8),
    surfaceVariant = RomanNightSurfaceVariant,
    onSurfaceVariant = RomanNightOnSurfaceVariant,
    outline = Color(0xFFA08D80),
    outlineVariant = Color(0xFF51463E),
    inverseSurface = Color(0xFFF2E3D8),
    inverseOnSurface = Color(0xFF3A302B),
    inversePrimary = RomanPrimary,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = RomanPrimary,
    onPrimary = Color.White,
    primaryContainer = RomanPrimaryContainer,
    onPrimaryContainer = Color(0xFF3F0F0C),
    secondary = RomanSecondary,
    onSecondary = Color.White,
    secondaryContainer = RomanSecondaryContainer,
    onSecondaryContainer = Color(0xFF211B08),
    tertiary = RomanTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2C1700),
    background = RomanParchment,
    onBackground = RomanInk,
    surface = RomanParchment,
    onSurface = RomanInk,
    surfaceVariant = Color(0xFFEDE2D6),
    onSurfaceVariant = Color(0xFF51443B),
    outline = Color(0xFF82736A),
    outlineVariant = Color(0xFFD5C8BC),
    inverseSurface = Color(0xFF362F2B),
    inverseOnSurface = Color(0xFFF9EEE5),
    inversePrimary = RomanNightPrimary,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val RomanShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun OratorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RomanShapes,
        content = content
    )
}
