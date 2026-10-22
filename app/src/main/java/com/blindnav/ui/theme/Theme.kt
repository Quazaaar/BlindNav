package com.blindnav.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.blindnav.scoring.Grade

val Blue = Color(0xFF1A73E8)
val RouteOutline = Color(0xFF0B57D0)
val TrackGray = Color(0xFF3C4043)
val DetourRed = Color(0xFFD93025)
val SecondaryText = Color(0xFF5F6368)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = SecondaryText,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E3FD),
    onSecondaryContainer = Color(0xFF041E49),
    background = Color.White,
    onBackground = Color(0xFF1F1F1F),
    surface = Color.White,
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = SecondaryText,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFF1F3F4),
    surfaceContainerHigh = Color(0xFFE8EAED),
    surfaceContainerHighest = Color(0xFFDADCE0),
    outline = Color(0xFFDADCE0),
    outlineVariant = Color(0xFFE8EAED),
    error = DetourRed,
)

val Grade.color: Color
    get() = when (this) {
        Grade.S -> Color(0xFF9334E6)
        Grade.A -> Color(0xFF1E8E3E)
        Grade.B -> Blue
        Grade.C -> Color(0xFFF29900)
        Grade.D -> Color(0xFFE8710A)
        Grade.F -> DetourRed
    }

@Composable
fun BlindNavTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
