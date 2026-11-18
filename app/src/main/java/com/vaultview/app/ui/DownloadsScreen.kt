package com.vaultview.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaultview.app.data.DownloadItem
import com.vaultview.app.data.DownloadState
import com.vaultview.app.data.Downloads
import com.vaultview.app.data.Game
import com.vaultview.app.data.Library
import com.vaultview.app.data.LibraryGame
import com.vaultview.app.data.Settings

private val Good = Color(0xFF7BD88F)
private val Bad = Color(0xFFFF7A7A)

@Composable
fun DownloadsScreen(onGame: (Game) -> Unit, onOwned: (LibraryGame) -> Unit = {}) {
    val active = Downloads.items.filter { it.active }
    val failed = Downloads.items.filter { it.state == DownloadState.FAILED || it.state == DownloadState.CANCELLED }
    val done = Downloads.items.filter { it.state == DownloadState.DONE }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    listOfNotNull(
                        active.size.takeIf { it > 0 }?.let { "$it downloading" },
                        done.size.takeIf { it > 0 }?.let { "$it finished" },
                        Settings.folderLabel()?.let { "saving to $it" } ?: "no download folder yet",
                    ).joinToString("  ·  "),
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (done.isNotEmpty() || failed.isNotEmpty()) {
                TextButton(onClick = { Downloads.clearFinished() }, modifier = Modifier.focusRing(RoundedCornerShape(50))) { Text("Clear list") }
            }
        }
        if (Downloads.items.isEmpty()) {
            Empty()
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (active.isNotEmpty()) {
                item { Label("In progress") }
                items(active, key = { it.key }) { d -> Box(Modifier.animateItem()) { ActiveCard(d) } }
            }
            if (failed.isNotEmpty()) {
                item { Label("Didn't finish") }
                items(failed, key = { it.key }) { d -> Box(Modifier.animateItem()) { FailedRow(d, onGame) } }
            }
            if (done.isNotEmpty()) {
                item { Label("Finished") }
                items(done, key = { it.key }) { d -> Box(Modifier.animateItem()) { DoneRow(d, onGame, onOwned) } }
            }
        }
    }
}

@Composable
private fun Label(t: String) = Text(t.uppercase(), color = Muted, style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))

@Composable
private fun Empty() {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(VimmNavy, PanelHi))),
            contentAlignment = Alignment.Center,
        ) { Icon(DownloadIcon, null, tint = VimmTitle, modifier = Modifier.size(44.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("Nothing downloading", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text("Open a game and press Download. It'll show up here, and in your Library when it's done.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun size(b: Long) = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.0f KB".format(b / 1024.0)
}

private fun eta(d: DownloadItem): String? {
    if (d.speed <= 0 || d.total <= 0) return null
    val s = (d.total - d.bytes) / d.speed
    return if (s < 60) "${s}s left" else "${s / 60}m ${s % 60}s left"
}

/** A download in progress: art, title, a glowing bar, speed and time left. */
@Composable
private fun ActiveCard(d: DownloadItem) {
    val shape = RoundedCornerShape(20.dp)
    val p by animateFloatAsState(d.progress, tween(500), label = "p")
    val color = d.system?.brand?.color ?: Accent
    Surface(color = Panel, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Box {
            // A soft wash of the system's colour, growing with progress.
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                0f to color.copy(alpha = 0.22f), p.coerceIn(0.01f, 1f) to color.copy(alpha = 0.06f), 1f to Color.Transparent)))
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverArt(d.gameId, d.title, Modifier.width(72.dp).aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)), d.system?.code)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(d.system?.name ?: "", color = color, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(PanelHi)) {
                        if (d.state == DownloadState.QUEUED || (d.total <= 0 && d.state == DownloadState.DOWNLOADING)) {
                            Box(Modifier.matchParentSize().shimmer())
                        } else {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(5.dp))
                                .background(Brush.horizontalGradient(listOf(color, VimmTitle))))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (d.state) {
                            DownloadState.QUEUED -> "Waiting to start…"
                            DownloadState.EXTRACTING -> "Unzipping…"
                            else -> listOfNotNull(
                                if (d.total > 0) "${size(d.bytes)} of ${size(d.total)}" else size(d.bytes),
                                d.speed.takeIf { it > 0 }?.let { "${size(it)}/s" }, eta(d),
                            ).joinToString("  ·  ")
                        },
                        color = Muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${(p * 100).toInt()}%", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { Downloads.cancel(d) }, modifier = Modifier.focusRing(CircleShape)) { Icon(Icons.Filled.Close, "Cancel") }
                }
            }
        }
    }
}

@Composable
private fun FailedRow(d: DownloadItem, onGame: (Game) -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    Surface(color = Bad.copy(alpha = 0.08f), shape = shape, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(d.gameId, d.title, Modifier.width(48.dp).aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)), d.system?.code)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (d.state == DownloadState.CANCELLED) "Cancelled" else d.message ?: "Failed",
                    color = Bad, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            if (d.request != null) {
                FilledTonalButton(onClick = { Downloads.retry(context, d) }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                    Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Retry")
                }
            } else {
                OutlinedButton(onClick = { onGame(Game(d.gameId, d.title, d.system?.name)) }, modifier = Modifier.focusRing(RoundedCornerShape(50))) { Text("Open") }
            }
            IconButton(onClick = { Downloads.remove(d) }, modifier = Modifier.focusRing(CircleShape)) { Icon(Icons.Filled.Close, "Remove") }
        }
    }
}

@Composable
private fun DoneRow(d: DownloadItem, onGame: (Game) -> Unit, onOwned: (LibraryGame) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    // The same game in the Library, if the folder scan has found it, so it can be played from here.
    val owned = remember(d.key, Library.games) { Library.games.firstOrNull { it.gameId == d.gameId } }
    Surface(color = Panel, shape = shape, modifier = Modifier.fillMaxWidth().focusRing(shape, 1.01f).clip(shape)
        .clickable { owned?.let(onOwned) ?: onGame(Game(d.gameId, d.title, d.system?.name)) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(d.gameId, d.title, Modifier.width(48.dp).aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)), d.system?.code)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "✓  " + listOfNotNull(
                        d.savedTo,
                        d.finishedAt.takeIf { it > 0 }?.let { android.text.format.DateUtils.getRelativeTimeSpanString(it).toString() },
                    ).joinToString("  ·  "),
                    color = Good, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (owned != null) {
                FilledTonalButton(onClick = { onOwned(owned) }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play") }
            }
            IconButton(onClick = { Downloads.remove(d) }) { Icon(Icons.Filled.Delete, "Remove from list") }
        }
    }
}
