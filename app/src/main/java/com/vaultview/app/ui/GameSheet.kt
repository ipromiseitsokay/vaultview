package com.vaultview.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vaultview.app.data.Favorites
import com.vaultview.app.data.Game
import com.vaultview.app.data.Vimm

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameSheet(game: Game, onDismiss: () -> Unit, onGet: () -> Unit) {
    val context = LocalContext.current
    val wide = LocalWide.current
    val saved = Favorites.contains(game.id)
    val getFocus = remember { FocusRequester() }

    val info: @Composable ColumnScope.() -> Unit = {
        Text(game.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        game.system?.let { Text(it, color = Accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(8.dp))
        if (game.regions.isNotEmpty()) Fact("Region", game.regions.joinToString("  ") { "${flag(it)} $it" })
        game.version?.let { Fact("Version", it) }
        game.media?.let { Fact("Media", it) }
        game.rating?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(18.dp))
                Text(" $it / 10", fontWeight = FontWeight.Bold)
            }
        }
        if (game.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                game.tags.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
            }
        }
    }

    val buttons: @Composable ColumnScope.() -> Unit = {
        Button(
            onClick = onGet,
            modifier = Modifier.fillMaxWidth().height(52.dp).focusRing(RoundedCornerShape(14.dp), 1.02f).focusRequester(getFocus),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(DownloadIcon, null)
            Spacer(Modifier.width(8.dp))
            Text("Get game", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Favorites.toggle(game) }, modifier = Modifier.weight(1f).height(48.dp).focusRing(RoundedCornerShape(14.dp), 1.02f), shape = RoundedCornerShape(14.dp)) {
                Icon(if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                    tint = if (saved) Color(0xFFFF6B81) else LocalContentColor.current)
                Spacer(Modifier.width(6.dp))
                Text(if (saved) "Saved" else "Save")
            }
            OutlinedButton(onClick = { openOnSite(context, Vimm.gamePage(game.id)) }, modifier = Modifier.weight(1f).height(48.dp).focusRing(RoundedCornerShape(14.dp), 1.02f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Share, null)
                Spacer(Modifier.width(6.dp))
                Text("Web", maxLines = 1)
            }
        }
    }

    val screenshot: @Composable (Modifier) -> Unit = { m ->
        AsyncImage(
            model = Vimm.screenshot(game.id), contentDescription = "Screenshot",
            contentScale = ContentScale.Fit,
            modifier = m.clip(RoundedCornerShape(14.dp)).background(Color.Black),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        sheetMaxWidth = if (wide) 1000.dp else BottomSheetDefaults.SheetMaxWidth,
    ) {
        if (wide) {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CoverArt(game.id, game.title, Modifier.width(170.dp).aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    info()
                    Spacer(Modifier.height(16.dp))
                    buttons()
                }
                screenshot(Modifier.weight(1f).aspectRatio(4f / 3f))
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Row {
                    CoverArt(game.id, game.title, Modifier.width(132.dp).aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { info() }
                }
                Spacer(Modifier.height(16.dp))
                buttons()
                Text("SCREENSHOT", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                screenshot(Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            }
        }
    }
    // Controller users land on the main action straight away.
    LaunchedEffect(Unit) { runCatching { kotlinx.coroutines.delay(350); getFocus.requestFocus() } }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.padding(top = 4.dp)) {
        Text("$label  ", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
