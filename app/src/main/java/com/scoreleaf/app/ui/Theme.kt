package com.scoreleaf.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ScoreleafColors = lightColorScheme(
    primary = Color(0xFF9E3D2C), onPrimary = Color.White,
    secondary = Color(0xFF53634A), background = Color(0xFFF7F2E9),
    surface = Color(0xFFFFFBF4), onBackground = Color(0xFF201D1A),
    onSurface = Color(0xFF201D1A)
)

@Composable fun ScoreleafTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ScoreleafColors, typography = Typography(), content = content)
}
