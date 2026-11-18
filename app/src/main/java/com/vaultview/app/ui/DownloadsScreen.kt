package com.vaultview.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaultview.app.data.DownloadItem
import com.vaultview.app.data.DownloadState
import com.vaultview.app.data.Downloads
import com.vaultview.app.data.Game
import com.vaultview.app.data.Settings

@Composable
fun DownloadsScreen(onGame: (Game) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text(Settings.folderLabel()?.let { "Saving to $it" } ?: "No download folder yet. Pick one in Settings.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (Downloads.items.any { !it.active }) TextButton(onClick = { Downloads.clearFinished() }) { Text("Clear finished") }
        }
        if (Downloads.items.isEmpty()) {
            CenterMessage("Games you download show up here")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(Downloads.items, key = { _, d -> d.key }) { i, d ->
                    Box(Modifier.animateItem().staggerIn(i).then(if (i == 0) Modifier.initialFocus() else Modifier)) { Row(d, onGame) }
                }
            }
        }
    }
}

@Composable
private fun Row(d: DownloadItem, onGame: (Game) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(color = Panel, shape = shape, modifier = Modifier.fillMaxWidth().focusRing(shape, 1.01f).clip(shape)
        .clickable { onGame(Game(d.gameId, d.title, d.system?.name)) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(d.gameId, d.title, Modifier.width(54.dp).aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)), d.system?.code)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(statusText(d), color = statusColor(d), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (d.state == DownloadState.DOWNLOADING || d.state == DownloadState.EXTRACTING) {
                    Spacer(Modifier.height(6.dp))
                    val smooth by animateFloatAsState(d.progress, tween(400), label = "p")
                    if (d.total > 0 || d.state == DownloadState.EXTRACTING)
                        LinearProgressIndicator(progress = { smooth }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.width(8.dp))
            if (d.active) IconButton(onClick = { Downloads.cancel(d) }) { Icon(Icons.Filled.Close, "Cancel") }
            else IconButton(onClick = { Downloads.remove(d) }) { Icon(Icons.Filled.Delete, "Remove from list") }
        }
    }
}

private fun mb(b: Long) = if (b >= 1L shl 30) "%.2f GB".format(b / 1073741824.0) else "%.1f MB".format(b / 1048576.0)

private fun statusText(d: DownloadItem): String {
    val sys = d.system?.name?.let { "$it · " }.orEmpty()
    return sys + when (d.state) {
        DownloadState.QUEUED -> "Waiting…"
        DownloadState.DOWNLOADING -> if (d.total > 0) "${(d.progress * 100).toInt()}% · ${mb(d.bytes)} of ${mb(d.total)}" else "Downloading · ${mb(d.bytes)}"
        DownloadState.EXTRACTING -> "Unzipping… ${(d.progress * 100).toInt()}%"
        DownloadState.DONE -> "Saved to ${d.savedTo ?: "your folder"}" + (d.message?.let { " · $it" } ?: d.fileName?.let { " · $it" } ?: "")
        DownloadState.FAILED -> "Failed: ${d.message}"
        DownloadState.CANCELLED -> "Cancelled"
    }
}

private fun statusColor(d: DownloadItem) = when (d.state) {
    DownloadState.DONE -> Color(0xFF7BD88F)
    DownloadState.FAILED -> Color(0xFFFF7A7A)
    else -> Muted
}
