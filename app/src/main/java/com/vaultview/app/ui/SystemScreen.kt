package com.vaultview.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vaultview.app.data.Game
import com.vaultview.app.data.GameSystem
import com.vaultview.app.data.Systems
import com.vaultview.app.data.Vimm

private const val TOP = "TOP"
private val sections = listOf(TOP, "#") + ('A'..'Z').map { it.toString() }

@Composable
fun SystemScreen(code: String, onBack: () -> Unit, onGame: (Game) -> Unit) {
    val sys = Systems.byCode(code) ?: return CenterMessage("Unknown system")
    val wide = LocalWide.current
    var section by rememberSaveable { mutableStateOf(TOP) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val state by produceState<Load<List<Game>>>(Load.Loading, section, submitted, retry) {
        value = Load.Loading
        value = try {
            Load.Ok(
                when {
                    submitted.isNotBlank() -> Vimm.search(submitted, code)
                    section == TOP -> Vimm.topGames(code)
                    else -> Vimm.section(code, section)
                }
            )
        } catch (e: Exception) {
            Load.Err(e.message ?: "Couldn't reach vimm.net")
        }
    }
    val games = (state as? Load.Ok)?.value.orEmpty()
    val regions = games.flatMap { it.regions }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(10).map { it.key }

    val search: @Composable (Modifier) -> Unit = { m ->
        SearchBox(
            query = query, onQueryChange = { query = it }, placeholder = "Search ${sys.name}",
            onSubmit = { submitted = query.trim(); region = null },
            onClear = { query = ""; submitted = "" },
            modifier = m,
        )
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(sys.brand.color.copy(alpha = 0.45f), Color.Transparent)))
        ) {
            if (wide) {
                Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.focusRing(CircleShape)) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Title(sys, Modifier.widthIn(max = 260.dp))
                    Spacer(Modifier.width(16.dp))
                    search(Modifier.weight(1f).heightIn(min = 52.dp))
                    Spacer(Modifier.width(12.dp))
                    RegionPicker(regions, region) { region = it }
                }
            } else {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.focusRing(CircleShape)) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Title(sys, Modifier.weight(1f))
                    RegionPicker(regions, region) { region = it }
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
        if (!wide) search(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))

        if (submitted.isBlank()) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = sections.indexOf(section).coerceAtLeast(0))
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sections) { s ->
                    FilterChip(
                        selected = s == section,
                        onClick = { section = s; region = null },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp), 1.08f),
                        label = { Text(if (s == TOP) "★ Top rated" else s, fontWeight = FontWeight.Bold) },
                    )
                }
            }
        } else {
            Text("Results for “$submitted”", color = Muted, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        when (val s = state) {
            Load.Loading -> SkeletonGrid()
            is Load.Err -> CenterMessage(s.message, "Try again") { retry++ }
            is Load.Ok -> {
                val shown = if (region == null) s.value else s.value.filter { region in it.regions }
                if (shown.isEmpty()) CenterMessage("No games here") else GameGrid(shown, onGame)
            }
        }
    }
}

@Composable
private fun Title(sys: GameSystem, modifier: Modifier) {
    Column(modifier) {
        Text(sys.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text("${sys.brand.label} · ${sys.year}${if (sys.handheld) " · Handheld" else ""}",
            color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun RegionPicker(regions: List<String>, selected: String?, onPick: (String?) -> Unit) {
    if (regions.size < 2 && selected == null) return
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { open = true }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
            Text(selected?.let { "${flag(it)} $it" } ?: "🌐 All regions", maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("🌐 All regions") }, onClick = { onPick(null); open = false })
            regions.forEach { r ->
                DropdownMenuItem(text = { Text("${flag(r)} $r") }, onClick = { onPick(r); open = false })
            }
        }
    }
}
