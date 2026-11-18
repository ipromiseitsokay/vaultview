package com.vaultview.app.ui

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0E1015)
val Panel = Color(0xFF161A23)
val PanelHi = Color(0xFF202534)
val Muted = Color(0xFF8E95A8)
val Accent = Color(0xFF7C93FF)
val Gold = Color(0xFFFFC94D)

// vimm.net's own palette (from its stylesheet)
val VimmMaroon = Color(0xFF800000)
val VimmMaroonDeep = Color(0xFF4A0000)
val VimmNavy = Color(0xFF000035)
val VimmRed = Color(0xFFFF0000)
val VimmSilver = Color(0xFFC0C0C0)
val VimmTitle = Color(0xFFFFFF99)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme) {
    // Chips, buttons and tabs get a clearly visible highlight when the D-pad is on them.
    CompositionLocalProvider(
        LocalRippleConfiguration provides RippleConfiguration(
            color = Color.White,
            rippleAlpha = RippleAlpha(pressedAlpha = 0.22f, focusedAlpha = 0.30f, draggedAlpha = 0.16f, hoveredAlpha = 0.10f),
        ),
        content = content,
    )
}
