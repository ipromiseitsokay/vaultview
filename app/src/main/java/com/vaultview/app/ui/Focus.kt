package com.vaultview.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaultview.app.Controls
import kotlinx.coroutines.delay

/** True on wide screens (landscape handhelds like the Retroid Pocket, tablets). */
val LocalWide = compositionLocalOf { false }

val FocusColor = Color.White
val FocusGlow = Color(0xFFFFFF99) // vimm.net's title yellow

/**
 * Where the D-pad is: a white outline with a soft yellow glow and a slight lift, eased in and out.
 * Put it before `clip`/`clickable` so it sees that element's focus and draws outside the clip.
 */
fun Modifier.focusRing(shape: Shape, lift: Float = 1.05f): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val t by animateFloatAsState(
        if (focused) 1f else 0f,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "focus",
    )
    this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { val s = 1f + (lift - 1f) * t; scaleX = s; scaleY = s }
        .drawWithContent {
            if (t > 0.01f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                // Glow behind the element, then the element, then a crisp ring on top.
                drawOutline(outline, FocusGlow.copy(alpha = 0.05f * t), style = Stroke(12.dp.toPx() * t))
                drawOutline(outline, FocusGlow.copy(alpha = 0.12f * t), style = Stroke(6.dp.toPx() * t))
            }
            drawContent()
            if (t > 0.01f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, FocusColor.copy(alpha = 0.9f * t.coerceIn(0f, 1f)), style = Stroke(2.dp.toPx()))
            }
        }
}

/**
 * Gentle version for menu tabs: no outline, just a soft halo, so it sits nicely with the
 * tab's own highlighted pill instead of boxing it in.
 */
fun Modifier.focusGlow(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "glow")
    this
        .onFocusChanged { focused = it.hasFocus || it.isFocused }
        .drawBehind {
            if (t > 0.01f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(FocusGlow.copy(alpha = 0.16f * t), Color.Transparent),
                        center = center, radius = size.minDimension * 0.75f,
                    ),
                    radius = size.minDimension * 0.75f,
                )
            }
        }
}

/** When someone is using buttons, put the D-pad here as the screen opens. */
fun Modifier.initialFocus(delayMs: Long = 320): Modifier = composed {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (Controls.usingController) { delay(delayMs); runCatching { fr.requestFocus() } }
    }
    this.focusRequester(fr)
}

/** Items rise and fade in, a little after the one before, when a screen first fills. */
fun Modifier.staggerIn(index: Int): Modifier = composed {
    val first = index < 18
    val a = remember { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!first) return@LaunchedEffect
        delay(index * 24L)
        a.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
    }
    this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 18.dp.toPx()
        val s = 0.96f + 0.04f * a.value; scaleX = s; scaleY = s
    }
}

/** A moving shine over a dark card, used while content loads. */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    drawBehind {
        drawRect(PanelHi)
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                start = Offset(w * x - w * 0.6f, 0f), end = Offset(w * x, size.height),
            )
        )
    }
}

/** A game cartridge: rounded shell, a label window, and contacts at the bottom. */
val LibraryIcon: ImageVector by lazy {
    ImageVector.Builder("Library", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString(
                "M6.5,2h11a2.5,2.5 0 0,1 2.5,2.5v13a2.5,2.5 0 0,1 -2.5,2.5h-1.5v2h-8v-2h-1.5a2.5,2.5 0 0,1 -2.5,-2.5v-13a2.5,2.5 0 0,1 2.5,-2.5z" +
                    "M7.5,5v7.5h9V5z" + "M9,16.5h1.5v2H9z M11.25,16.5h1.5v2h-1.5z M13.5,16.5H15v2h-1.5z"
            ).toNodes(),
            fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
        )
        .build()
}

/** Browse: a 2x2 of rounded tiles, one of them a disc. */
val BrowseIcon: ImageVector by lazy {
    ImageVector.Builder("Browse", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString(
                "M5,3h4a2,2 0 0,1 2,2v4a2,2 0 0,1 -2,2h-4a2,2 0 0,1 -2,-2v-4a2,2 0 0,1 2,-2z" +
                    "M15,3h4a2,2 0 0,1 2,2v4a2,2 0 0,1 -2,2h-4a2,2 0 0,1 -2,-2v-4a2,2 0 0,1 2,-2z" +
                    "M5,13h4a2,2 0 0,1 2,2v4a2,2 0 0,1 -2,2h-4a2,2 0 0,1 -2,-2v-4a2,2 0 0,1 2,-2z" +
                    "M17,13a4,4 0 1,1 0,8a4,4 0 1,1 0,-8z M17,15.75a1.25,1.25 0 1,0 0,2.5a1.25,1.25 0 1,0 0,-2.5z"
            ).toNodes(),
            fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
        )
        .build()
}

/** Material "download" glyph (arrow into a tray); the core icon set doesn't include it. */
val DownloadIcon: ImageVector by lazy {
    ImageVector.Builder("Download", 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString("M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z").toNodes(), fill = SolidColor(Color.Black))
        .build()
}

/** Lets a row reach [amount] past its parent's side padding, so focus outlines aren't clipped. */
fun Modifier.bleed(amount: Dp): Modifier = this.layout { measurable, constraints ->
    val px = amount.roundToPx()
    val wide = if (constraints.hasBoundedWidth) constraints.copy(
        minWidth = constraints.minWidth + 2 * px, maxWidth = constraints.maxWidth + 2 * px,
    ) else constraints
    val placeable = measurable.measure(wide)
    layout(placeable.width - 2 * px, placeable.height) { placeable.place(-px, 0) }
}

/**
 * Header that tucks away while scrolling down and returns when scrolling back up (or at the top).
 * Follows the grid's position, so it works for touch, D-pad and stick alike.
 */
@androidx.compose.runtime.Composable
fun rememberHeaderVisible(state: androidx.compose.foundation.lazy.grid.LazyGridState): Boolean {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(state) {
        var anchor = 0L
        androidx.compose.runtime.snapshotFlow { state.firstVisibleItemIndex.toLong() * 100_000 + state.firstVisibleItemScrollOffset }
            .collect { pos ->
                // While shown, remember the highest point reached and hide after moving 80px down from it;
                // while hidden, remember the lowest point and show again after moving 80px back up.
                when {
                    state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 60 -> { visible = true; anchor = pos }
                    visible -> if (pos > anchor + 80) { visible = false; anchor = pos } else anchor = minOf(anchor, pos)
                    else -> if (pos < anchor - 80) { visible = true; anchor = pos } else anchor = maxOf(anchor, pos)
                }
            }
    }
    return visible
}
