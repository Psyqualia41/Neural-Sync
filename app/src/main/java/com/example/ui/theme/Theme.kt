package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberDarkColorScheme = darkColorScheme(
    primary = NeonTeal,
    secondary = NeonGreen,
    tertiary = NeonOrange,
    background = CyberBlack,
    surface = CyberCardBg,
    onPrimary = CyberBlack,
    onSecondary = CyberBlack,
    onTertiary = CyberBlack,
    onBackground = OffWhite,
    onSurface = OffWhite,
    surfaceVariant = CyberMediumGray,
    onSurfaceVariant = MutedText,
    outline = BorderGray
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default for the cyber athletic aesthetic
    content: @Composable () -> Unit
) {
    // We intentionally ignore dynamic theme mapping to preserve the premium cyber athletic neon design.
    MaterialTheme(
        colorScheme = CyberDarkColorScheme,
        typography = Typography,
        content = content
    )
}
