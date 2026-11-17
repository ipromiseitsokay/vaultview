package com.vaultview.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** True on wide screens (landscape handhelds like the Retroid Pocket, tablets). */
val LocalWide = compositionLocalOf { false }

/**
 * Bright outline and a slight lift on whatever the D-pad has selected.
 * Put it before `clickable` so it sees that element's focus.
 */
fun Modifier.focusRing(shape: Shape, lift: Float = 1.05f): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) lift else 1f, label = "focusScale")
    this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(if (focused) 3.dp else 0.dp, if (focused) FocusColor else Color.Transparent, shape)
}

val FocusColor = Color(0xFFFFFFFF)

/** Material "download" glyph (arrow into a tray); the core icon set doesn't include it. */
val DownloadIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("Download", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            androidx.compose.ui.graphics.vector.PathParser()
                .parsePathString("M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z").toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        ).build()
}
