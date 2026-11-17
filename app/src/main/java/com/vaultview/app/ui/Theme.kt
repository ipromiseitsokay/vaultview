package com.vaultview.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0E1015)
val Panel = Color(0xFF161A23)
val PanelHi = Color(0xFF202534)
val Muted = Color(0xFF8E95A8)
val Accent = Color(0xFF7C93FF)
val Gold = Color(0xFFFFC94D)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B1030),
    secondaryContainer = Color(0xFF2A3360),
    onSecondaryContainer = Color(0xFFDDE2FF),
    background = Bg,
    onBackground = Color(0xFFE9EBF2),
    surface = Bg,
    onSurface = Color(0xFFE9EBF2),
    surfaceVariant = PanelHi,
    onSurfaceVariant = Muted,
    surfaceContainer = Panel,
    surfaceContainerLow = Panel,
    surfaceContainerHigh = PanelHi,
    outline = Color(0xFF3A4152),
    outlineVariant = Color(0xFF2A3040),
)

@Composable
fun VaultTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
