package com.vaultview.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vaultview.app.data.Catalog
import com.vaultview.app.data.Game
import com.vaultview.app.data.Library
import com.vaultview.app.data.Playtime
import com.vaultview.app.data.RetroAchievements
import com.vaultview.app.data.Systems
import com.vaultview.app.data.Vimm

/** Today's pick: one of the systems' Top 10s, the same for everyone on a given day. */
fun gameOfTheDay(): Game? {
    val pool = Systems.all.flatMap { Catalog.systemNow(it.code)?.top.orEmpty() }.sortedBy { it.id }
    if (pool.isEmpty()) return null
    val day = System.currentTimeMillis() / 86_400_000L
    return pool[((day * 7919L) % pool.size).toInt()]
}

/** A banner for the Game of the Day: blurred art behind, the box, title, system and rating. */
@Composable
fun GameOfTheDayCard(game: Game, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val wide = LocalWide.current
    val ctx = LocalContext.current
    Box(
        Modifier.fillMaxWidth().height(if (wide) 190.dp else 210.dp)
            .focusRing(shape, 1.015f).clip(shape).clickable(onClick = onOpen).background(Panel)
    ) {
        AsyncImage(
            model = remember(game.id) { coil.request.ImageRequest.Builder(ctx).data(Vimm.boxArt(game.id)).size(10, 8).precision(coil.size.Precision.EXACT).build() },
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(), alpha = 0.7f,
        )
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.75f)))))
        Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(game.id, game.title, Modifier.fillMaxHeight().aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)), game.system, game.regions)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("GAME OF THE DAY", color = VimmTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(game.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(game.system, game.rating?.let { "★ $it" }).joinToString("  ·  "),
                    color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text("Take a look  →", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Your play history at a glance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsSheet(onDismiss: () -> Unit) {
    val wide = LocalWide.current
    val ctx = LocalContext.current
    val played = Library.games.mapNotNull { g -> Playtime.of(g)?.let { g to it } }
    val total = Playtime.records.values.sumOf { it.seconds }
    val sessions = Playtime.records.values.sumOf { it.plays }
    val top = played.sortedByDescending { it.second.seconds }.take(6)
    val bySystem = played.groupBy { it.first.system }
        .mapValues { (_, v) -> v.sumOf { it.second.seconds } }.entries.sortedByDescending { it.value }.take(8)
    val favourite = top.firstOrNull()?.first

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel, sheetMaxWidth = if (wide) 1000.dp else BottomSheetDefaults.SheetMaxWidth,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            // Hero: total time over a wash of your most-played game's art.
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp)).background(VimmNavy)) {
                favourite?.let { f ->
                    AsyncImage(
                        model = remember(f.key) { coil.request.ImageRequest.Builder(ctx).data(f.gameId?.let { Vimm.boxArt(it) }
                            ?: Vimm.libretroExact(f.system?.code, f.baseName)).size(10, 8).precision(coil.size.Precision.EXACT).build() },
                        contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(), alpha = 0.55f,
                    )
                }
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent))))
                Column(Modifier.align(Alignment.CenterStart).padding(22.dp)) {
                    Text("TIME PLAYED", color = VimmTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                    Text(Playtime.format(total), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    favourite?.let { Text("Most played: ${it.title}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Games played", played.size.toString(), Modifier.weight(1f))
                StatTile("Sessions", sessions.toString(), Modifier.weight(1f))
                StatTile("In library", Library.games.size.toString(), Modifier.weight(1f))
                RetroAchievements.profile?.let { StatTile("RA points", it.points.toString(), Modifier.weight(1f)) }
            }
            if (top.isNotEmpty()) {
                SectionTitle("Most played")
                val max = top.first().second.seconds.coerceAtLeast(1)
                top.forEachIndexed { i, (g, r) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(g.gameId, g.title, Modifier.width(34.dp).aspectRatio(0.75f).clip(RoundedCornerShape(6.dp)),
                            system = g.system?.code, regions = g.regions, exactName = g.baseName)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            Bar(g.title, Playtime.format(r.seconds), r.seconds.toFloat() / max, g.system?.brand?.color ?: Accent, i)
                        }
                    }
                }
            }
            if (bySystem.size > 1) {
                SectionTitle("By system")
                val max = bySystem.first().value.coerceAtLeast(1)
                bySystem.forEachIndexed { i, (sys, secs) ->
                    Bar(sys?.name ?: "Other", Playtime.format(secs), secs.toFloat() / max, sys?.brand?.color ?: Accent, i)
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(color = PanelHi, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = VimmTitle)
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionTitle(t: String) = Text(t.uppercase(), color = Muted, style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

/** A labelled bar that grows in, one after another. */
@Composable
private fun Bar(label: String, value: String, fraction: Float, color: Color, index: Int) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(fraction) { kotlinx.coroutines.delay(index * 70L); grow.animateTo(fraction, tween(600)) }
    Column(Modifier.padding(vertical = 5.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(value, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(PanelHi)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(grow.value.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.6f)))))
        }
    }
}
