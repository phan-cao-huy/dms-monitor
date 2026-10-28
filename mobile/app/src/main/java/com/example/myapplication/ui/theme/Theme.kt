package com.example.myapplication.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF315C41),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F28B),
    onPrimaryContainer = Color(0xFF142B24),
    secondary = Color(0xFF6C9D61),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF2E6),
    onSecondaryContainer = Color(0xFF315C41),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFEDF2ED),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF4F7F5),
    onBackground = Color(0xFF182D24),
    surfaceContainerHighest = Color(0xFFE7EEE7),
    outlineVariant = Color(0xFFDDE6DF),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF182D24),
    surfaceVariant = Color(0xFFEDF2ED),
    onSurfaceVariant = Color(0xFF687D70)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8D78B),
    onPrimary = Color(0xFF142B24),
    primaryContainer = Color(0xFF213B28),
    onPrimaryContainer = Color(0xFFC8F28B),
    secondary = Color(0xFFA4C996),
    onSecondary = Color(0xFF142B24),
    secondaryContainer = Color(0xFF294332),
    onSecondaryContainer = Color(0xFFDCE8DC),
    surfaceContainer = Color(0xFF172B26),
    surfaceContainerLow = Color(0xFF172B26),
    surfaceContainerHigh = Color(0xFF1B3029),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF10201D),
    onBackground = Color(0xFFDCE8DC),
    surfaceContainerHighest = Color(0xFF243B30),
    outlineVariant = Color(0xFF2B4035),
    surface = Color(0xFF172B26),
    onSurface = Color(0xFFDCE8DC),
    surfaceVariant = Color(0xFF243B30),
    onSurfaceVariant = Color(0xFF9DB3A4)
)

@Composable
fun DmsMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
        shapes = Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
            headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)),

        content = content
    )
}
