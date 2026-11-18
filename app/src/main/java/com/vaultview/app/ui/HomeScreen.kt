package com.vaultview.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaultview.app.data.Brand
import com.vaultview.app.data.Game
import com.vaultview.app.data.GameSystem
import com.vaultview.app.data.Systems
import com.vaultview.app.data.Vimm

@Composable
fun HomeScreen(onSystem: (String) -> Unit, onGame: (Game) -> Unit, onSearch: () -> Unit, onOwned: (com.vaultview.app.data.LibraryGame) -> Unit = {}, onRandom: () -> Unit = {}) {
    val wide = LocalWide.current
    val library = com.vaultview.app.data.Downloads.library().take(20)
    val playing = com.vaultview.app.data.Library.games
        .mapNotNull { g -> com.vaultview.app.data.Playtime.of(g)?.let { g to it } }
        .sortedByDescending { it.second.lastPlayed }.take(12).map { it.first }
    var brandName by rememberSaveable { mutableStateOf<String?>(null) }
    val brand = brandName?.let { Brand.valueOf(it) }
    val shown = Systems.all.filter { brand == null || it.brand == brand }
    val consoles = shown.filterNot { it.handheld }.sortedBy { it.year }
    val handhelds = shown.filter { it.handheld }.sortedBy { it.year }
    val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }

    val glow by animateColorAsState(brand?.color ?: VimmRed, tween(600), label = "glow")
    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            // Soft light in the selected maker's colour, fading out well before the edges.
            drawRect(Brush.radialGradient(
                listOf(glow.copy(alpha = 0.24f), glow.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(size.width * 0.15f, 0f), radius = size.maxDimension * 0.6f,
            ))
        }
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 190.dp else 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = full) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (wide) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
                        Text("The Vault", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(12.dp))
                        Text("Pick a manufacturer, then a system", color = Muted, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                } else {
                    Column(Modifier.padding(top = 8.dp, bottom = 4.dp).weight(1f)) {
                        Text("The Vault", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                        Text("Pick a manufacturer, then a system", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // Surprise me and Search, top right of Browse.
                FilledTonalButton(
                    onClick = onRandom,
                    modifier = Modifier.focusRing(RoundedCornerShape(50)),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) { Text("🎲  Surprise me", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                FilledTonalButton(
                    onClick = onSearch,
                    modifier = Modifier.focusRing(RoundedCornerShape(50)),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Filled.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Search", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (playing.isNotEmpty()) {
            item(span = full) { SectionLabel("Continue playing") }
            item(span = full) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier.bleed(10.dp),
                ) {
                    items(playing, key = { "p-" + it.key }) { g ->
                        ContinueCard(g) { onOwned(g) }
                    }
                }
            }
        }
        if (library.isNotEmpty()) {
            item(span = full) { SectionLabel("Recently downloaded") }
            item(span = full) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier.bleed(10.dp),
                ) {
                    items(library, key = { it.key }) { d ->
                        val g = Game(d.gameId, d.title, d.system?.name)
                        Box(Modifier.width(if (wide) 104.dp else 96.dp)) { GameCard(g.copy(system = d.system?.code), showSystem = true) { onGame(g) } }
                    }
                }
            }
        }
        item(span = full) {
            BrandPicker(brand, wide) { brandName = it?.name }
        }
        if (brand == null) {
            var idx = 0
            Brand.entries.forEach { b ->
                val list = Systems.all.filter { it.brand == b }.sortedWith(compareBy({ it.handheld }, { it.year }))
                item(span = full, key = "h-${b.name}") { BrandHeader(b, list.size) }
                itemsIndexed(list, key = { _, s -> s.code }) { i, s ->
                    val n = idx + i
                    Box(Modifier.animateItem().staggerIn(n).then(if (n == 0) Modifier.initialFocus() else Modifier)) { SystemCard(s) { onSystem(s.code) } }
                }
                idx += list.size
            }
        }
        if (brand != null && consoles.isNotEmpty()) {
            item(span = full, key = "consoles") { SectionLabel("Consoles") }
            itemsIndexed(consoles, key = { _, s -> s.code }) { i, s ->
                Box(Modifier.animateItem().staggerIn(i).then(if (i == 0) Modifier.initialFocus() else Modifier)) { SystemCard(s) { onSystem(s.code) } }
            }
        }
        if (brand != null && handhelds.isNotEmpty()) {
            item(span = full, key = "handhelds") { SectionLabel("Handhelds") }
            itemsIndexed(handhelds, key = { _, s -> s.code }) { i, s ->
                Box(Modifier.animateItem().staggerIn(consoles.size + i)) { SystemCard(s) { onSystem(s.code) } }
            }
        }
    }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(
    text.uppercase(), color = Muted, style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp),
)

@Composable
private fun BrandPicker(selected: Brand?, wide: Boolean, onPick: (Brand?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        modifier = Modifier.bleed(10.dp),
    ) {
        item { BrandTile("All", Systems.all.size, VimmRed.copy(red = 0.8f), selected == null, wide) { onPick(null) } }
        items(Brand.entries) { b ->
            BrandTile(b.label, Systems.all.count { it.brand == b }, b.color, selected == b, wide) { onPick(b) }
        }
    }
}

@Composable
private fun BrandTile(label: String, count: Int, color: Color, active: Boolean, wide: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val fill by animateColorAsState(if (active) color else Panel, tween(300), label = "fill")
    val edge by animateColorAsState(if (active) Color.Transparent else color.copy(alpha = 0.45f), tween(300), label = "edge")
    val grow by animateFloatAsState(if (active) 1.04f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "grow")
    Column(
        Modifier
            .graphicsLayer { scaleX = grow; scaleY = grow }
            .width(118.dp)
            .height(if (wide) 64.dp else 76.dp)
            .focusRing(shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(fill, fill.copy(alpha = if (active) 0.55f else 1f))))
            .border(BorderStroke(1.dp, edge), shape)
            .clickable(onClick = onClick)
            .padding(if (wide) 10.dp else 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Emblem(label, color, active)
        Column {
            Text(label, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text("$count systems", style = MaterialTheme.typography.labelSmall,
                color = if (active) Color.White.copy(alpha = 0.8f) else Muted)
        }
    }
}

@Composable
private fun SystemCard(sys: GameSystem, onClick: () -> Unit) {
    val covers by produceState(emptyList<Game>(), sys.code) {
        // Retry a couple of times: dozens of cards load at once on first launch.
        repeat(3) { attempt ->
            val r = runCatching { Vimm.topGames(sys.code).take(3) }
            if (r.isSuccess) { value = r.getOrThrow(); return@produceState }
            kotlinx.coroutines.delay(1500L * (attempt + 1))
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (LocalWide.current) 1.5f else 1.15f)
            .focusRing(shape, lift = 1.03f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(sys.brand.color.copy(alpha = 0.9f), Panel)))
            .clickable(onClick = onClick)
    ) {
        // Fanned stack of the system's top-rated box art.
        Box(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 10.dp).height(96.dp).width(120.dp)) {
            covers.reversed().forEachIndexed { i, g ->
                val idx = covers.size - 1 - i
                CoverArt(
                    g.id, null,
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-idx * 26).dp, y = (idx * 6).dp)
                        .rotate(-6f + idx * 6f)
                        .width(62.dp)
                        .height(84.dp)
                        .shadow(8.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(sys.name, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            Text("${sys.year} · ${sys.brand.label}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** A wide card for a game you've been playing: box art, title, time played. */
@Composable
private fun ContinueCard(g: com.vaultview.app.data.LibraryGame, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val rec = com.vaultview.app.data.Playtime.of(g)
    Row(
        Modifier.width(300.dp).height(120.dp).focusRing(shape, 1.04f).clip(shape)
            .background(Brush.horizontalGradient(listOf((g.system?.brand?.color ?: Accent).copy(alpha = 0.35f), Panel)))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(g.gameId, g.title, Modifier.fillMaxHeight().aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)),
            system = g.system?.code, regions = g.regions, exactName = g.baseName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.title, fontWeight = FontWeight.Bold, maxLines = 2, color = Color.White,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(g.system?.name ?: "", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            rec?.let { Text("▶ " + com.vaultview.app.data.Playtime.format(it.seconds), color = VimmTitle, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** Short badge for each manufacturer, in its colour. */
private val emblems = mapOf("All" to "★", "Nintendo" to "N", "PlayStation" to "PS", "Sega" to "S", "Xbox" to "X",
    "Atari" to "A", "NEC" to "TG", "Philips" to "CDi")

@Composable
private fun Emblem(label: String, color: Color, active: Boolean) {
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(50))
            .then(if (active) Modifier.background(Color.White) else Modifier.background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f))))),
        contentAlignment = Alignment.Center,
    ) {
        Text(emblems[label] ?: label.take(1), color = if (active) color else Color.White, fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelSmall)
    }
}

/** Section title for a manufacturer on the All view. */
@Composable
private fun BrandHeader(b: Brand, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) {
        Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(b.color))
        Spacer(Modifier.width(10.dp))
        Text(b.label, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Text("$count systems", color = Muted, style = MaterialTheme.typography.labelLarge)
    }
}
