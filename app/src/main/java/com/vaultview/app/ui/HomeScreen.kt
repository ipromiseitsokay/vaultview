package com.vaultview.app.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaultview.app.data.Brand
import com.vaultview.app.data.Game
import com.vaultview.app.data.GameSystem
import com.vaultview.app.data.Systems
import com.vaultview.app.data.Vimm

@Composable
fun HomeScreen(onSystem: (String) -> Unit, onGame: (Game) -> Unit) {
    val wide = LocalWide.current
    val library = com.vaultview.app.data.Downloads.library().take(20)
    var brandName by rememberSaveable { mutableStateOf<String?>(null) }
    val brand = brandName?.let { Brand.valueOf(it) }
    val shown = Systems.all.filter { brand == null || it.brand == brand }
    val consoles = shown.filterNot { it.handheld }.sortedBy { it.year }
    val handhelds = shown.filter { it.handheld }.sortedBy { it.year }
    val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 190.dp else 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = full) {
            if (wide) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("The Vault", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(12.dp))
                    Text("Pick a maker, then a system", color = Muted, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            } else {
                Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text("The Vault", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text("Pick a maker, then a system", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (library.isNotEmpty()) {
            item(span = full) { SectionLabel("Recently downloaded") }
            item(span = full) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(library, key = { it.key }) { d ->
                        val g = Game(d.gameId, d.title, d.system?.name)
                        Box(Modifier.width(if (wide) 104.dp else 96.dp)) { GameCard(g, showSystem = true) { onGame(g) } }
                    }
                }
            }
        }
        item(span = full) {
            BrandPicker(brand, wide) { brandName = it?.name }
        }
        if (consoles.isNotEmpty()) {
            item(span = full) { SectionLabel("Consoles") }
            items(consoles, key = { it.code }) { s -> SystemCard(s) { onSystem(s.code) } }
        }
        if (handhelds.isNotEmpty()) {
            item(span = full) { SectionLabel("Handhelds") }
            items(handhelds, key = { it.code }) { s -> SystemCard(s) { onSystem(s.code) } }
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        item { BrandTile("All", Systems.all.size, Accent, selected == null, wide) { onPick(null) } }
        items(Brand.entries) { b ->
            BrandTile(b.label, Systems.all.count { it.brand == b }, b.color, selected == b, wide) { onPick(b) }
        }
    }
}

@Composable
private fun BrandTile(label: String, count: Int, color: Color, active: Boolean, wide: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(118.dp)
            .height(if (wide) 64.dp else 76.dp)
            .focusRing(shape)
            .clip(shape)
            .background(
                if (active) Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f)))
                else Brush.linearGradient(listOf(Panel, Panel))
            )
            .border(BorderStroke(1.dp, if (active) Color.Transparent else color.copy(alpha = 0.45f)), shape)
            .clickable(onClick = onClick)
            .padding(if (wide) 10.dp else 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(if (active) Color.White else color))
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
