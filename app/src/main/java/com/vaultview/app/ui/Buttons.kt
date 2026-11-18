package com.vaultview.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Plain, squared-off controls in the spirit of vimm.net itself: a flat dark plate, a hairline edge,
 * a line icon and a short label. No emoji, no pill-shaped candy buttons.
 */

private val ToolShape = RoundedCornerShape(10.dp)
private val Hairline = Color.White.copy(alpha = 0.09f)

/** The standard small button for toolbars and headers. */
@Composable
fun ToolButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: ImageVector? = null,
    height: Dp = 44.dp,
) {
    Surface(
        onClick = onClick, enabled = enabled, shape = ToolShape,
        color = PanelHi, contentColor = if (enabled) Color.White else Muted,
        border = BorderStroke(1.dp, Hairline),
        modifier = modifier.focusRing(ToolShape, 1.03f).height(height),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp), tint = VimmTitle.copy(alpha = if (enabled) 0.9f else 0.4f))
                Spacer(Modifier.width(9.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                Icon(trailing, null, Modifier.size(18.dp), tint = Muted)
            }
        }
    }
}

/** Two or more options side by side in one plate; the chosen one is lit. */
@Composable
fun ToolSwitch(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(44.dp).clip(ToolShape).background(Panel).border(1.dp, Hairline, ToolShape).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val inner = RoundedCornerShape(7.dp)
            Box(
                Modifier.height(36.dp).focusRing(inner, 1.04f).clip(inner)
                    .background(if (on) PanelHi else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, Hairline, inner) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) Color.White else Muted)
            }
        }
    }
}

private fun filled(name: String, d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black),
        pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd)
    .build()

private fun lined(name: String, d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    .build()

/** A die showing five. */
val DiceIcon: ImageVector by lazy {
    filled("Dice",
        "M6,3h12a3,3 0 0,1 3,3v12a3,3 0 0,1 -3,3h-12a3,3 0 0,1 -3,-3v-12a3,3 0 0,1 3,-3z" +
            "M8,6.5a1.5,1.5 0 1,0 0,3a1.5,1.5 0 1,0 0,-3z M16,6.5a1.5,1.5 0 1,0 0,3a1.5,1.5 0 1,0 0,-3z" +
            "M12,10.5a1.5,1.5 0 1,0 0,3a1.5,1.5 0 1,0 0,-3z" +
            "M8,14.5a1.5,1.5 0 1,0 0,3a1.5,1.5 0 1,0 0,-3z M16,14.5a1.5,1.5 0 1,0 0,3a1.5,1.5 0 1,0 0,-3z")
}

/** Three bars on a baseline. */
val StatsIcon: ImageVector by lazy {
    filled("Stats", "M3.5,19.5h17v1.8h-17z M5,11.5a1,1 0 0,1 1,-1h1.6a1,1 0 0,1 1,1v6.5h-3.6z" +
        "M10.2,5.5a1,1 0 0,1 1,-1h1.6a1,1 0 0,1 1,1v12.5h-3.6z M15.4,13.5a1,1 0 0,1 1,-1h1.6a1,1 0 0,1 1,1v4.5h-3.6z")
}

/** A line-drawn globe. */
val GlobeIcon: ImageVector by lazy {
    lined("Globe", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z" +
        "M12,3c-2.4,2.6 -3.6,5.6 -3.6,9s1.2,6.4 3.6,9 M12,3c2.4,2.6 3.6,5.6 3.6,9s-1.2,6.4 -3.6,9 M3.5,9.2h17 M3.5,14.8h17")
}

/** A magnifier, line-drawn to match. */
val SearchLineIcon: ImageVector by lazy {
    lined("Search", "M10.5,4a6.5,6.5 0 1,0 0,13a6.5,6.5 0 1,0 0,-13z M15.3,15.3l4.7,4.7")
}

/** An arrow going down into a tray, for saving art. */
val SaveArtIcon: ImageVector by lazy {
    lined("SaveArt", "M12,4v10 M7.5,9.8l4.5,4.5l4.5,-4.5 M4.5,15.5v2.5a2,2 0 0,0 2,2h11a2,2 0 0,0 2,-2v-2.5")
}

/** Two arrows passing each other: switch between Vault and Ports, or over to another app. */
val SwapIcon: ImageVector by lazy {
    lined("Swap", "M7,4l-3,3l3,3 M4,7h12a4,4 0 0,1 4,4 M17,20l3,-3l-3,-3 M20,17h-12a4,4 0 0,1 -4,-4")
}

/** A gamepad outline, for the Ports tab. */
val PortsIcon: ImageVector by lazy {
    ImageVector.Builder("Ports", 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(
            "M7,7h10a5,5 0 0,1 4.9,6l-0.6,3a2.6,2.6 0 0,1 -4.5,1.2l-1.8,-2.2h-6l-1.8,2.2a2.6,2.6 0 0,1 -4.5,-1.2l-0.6,-3a5,5 0 0,1 4.9,-6z" +
                "M7,9.5h1.6v1.4h1.4v1.6h-1.4v1.4h-1.6v-1.4h-1.4v-1.6h1.4z" +
                "M16,9.6a1.1,1.1 0 1,0 0,2.2a1.1,1.1 0 1,0 0,-2.2z M18.2,11.8a1.1,1.1 0 1,0 0,2.2a1.1,1.1 0 1,0 0,-2.2z"
        ).toNodes(), fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd)
        .build()
}

/** Two arrows chasing each other round: sync. */
val SyncIcon: ImageVector by lazy {
    lined("Sync", "M19.5,12a7.5,7.5 0 0,1 -12.8,5.3 M4.5,12a7.5,7.5 0 0,1 12.8,-5.3 M17.5,3v4h-4 M6.5,21v-4h4")
}
