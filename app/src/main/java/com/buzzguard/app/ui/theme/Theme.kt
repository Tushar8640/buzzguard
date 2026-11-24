package com.buzzguard.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

// Handcrafted editorial neutral palette: Warm Stone + Botanical Forest + Terracotta
val ForestLight = Color(0xFF1B5E48)
val ForestDark = Color(0xFF6DD3AE)
val TerracottaLight = Color(0xFFC94F2A)
val TerracottaDark = Color(0xFFF08765)
val SlateBlueLight = Color(0xFF2E5C8A)
val SlateBlueDark = Color(0xFF7EB4E8)
val OchreLight = Color(0xFFA86A14)
val OchreDark = Color(0xFFE5B35C)

private val LightColors = lightColorScheme(
    primary = ForestLight,
    onPrimary = Color(0xFFFAF9F5),
    primaryContainer = Color(0xFFD6EDE3),
    onPrimaryContainer = Color(0xFF0E382A),
    secondary = TerracottaLight,
    onSecondary = Color(0xFFFFFBF8),
    secondaryContainer = Color(0xFFFBE2D9),
    onSecondaryContainer = Color(0xFF561D0C),
    tertiary = SlateBlueLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE9F5),
    onTertiaryContainer = Color(0xFF122C47),
    background = Color(0xFFF6F5F1),
    onBackground = Color(0xFF181816),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181816),
    surfaceVariant = Color(0xFFECEAE3),
    onSurfaceVariant = Color(0xFF62605A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF9F6),
    surfaceContainer = Color(0xFFF0EEE8),
    surfaceContainerHigh = Color(0xFFE8E5DD),
    outline = Color(0xFFDAD6CC),
    outlineVariant = Color(0xFFE8E5DD),
    error = Color(0xFFBA3232),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = ForestDark,
    onPrimary = Color(0xFF06291D),
    primaryContainer = Color(0xFF163D30),
    onPrimaryContainer = Color(0xFFC5F2DF),
    secondary = TerracottaDark,
    onSecondary = Color(0xFF3A1206),
    secondaryContainer = Color(0xFF472015),
    onSecondaryContainer = Color(0xFFFCD6C8),
    tertiary = SlateBlueDark,
    onTertiary = Color(0xFF0A243D),
    tertiaryContainer = Color(0xFF1A344D),
    onTertiaryContainer = Color(0xFFD4E8FA),
    background = Color(0xFF121210),
    onBackground = Color(0xFFF1F0EA),
    surface = Color(0xFF1B1B18),
    onSurface = Color(0xFFF1F0EA),
    surfaceVariant = Color(0xFF262521),
    onSurfaceVariant = Color(0xFF9E9B92),
    surfaceContainerLowest = Color(0xFF151513),
    surfaceContainerLow = Color(0xFF1B1B18),
    surfaceContainer = Color(0xFF21211E),
    surfaceContainerHigh = Color(0xFF2A2925),
    outline = Color(0xFF3A3832),
    outlineVariant = Color(0xFF2A2925),
    error = Color(0xFFE67070),
    onError = Color(0xFF3B0C0C),
)

private val EditorialTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.35).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.5.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.5.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val EditorialShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun BuzzGuardTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = EditorialTypography,
        shapes = EditorialShapes,
        content = content,
    )
}
