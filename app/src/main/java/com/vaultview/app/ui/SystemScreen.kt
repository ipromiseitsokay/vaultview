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

private const val ALL = "ALL"
private val sections = listOf(ALL, "#") + ('A'..'Z').map { it.toString() }

@Composable
fun SystemScreen(code: String, onBack: () -> Unit, onGame: (Game) -> Unit) {
    val sys = Systems.byCode(code) ?: return CenterMessage("Unknown system")
    val wide = LocalWide.current
    var section by rememberSaveable { mutableStateOf(ALL) }
    var byRating by rememberSaveable { mutableStateOf(true) }
    // Second screen: letter jumps.
    SideEffect { Deck.letters = DeckLetters(sections, section) { section = it } }
    DisposableEffect(code) { onDispose { Deck.letters = null } }
    var pagesDone by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val state by produceState<Load<List<Game>>>(Load.Loading, section, submitted, retry) {
        value = Load.Loading
        pagesDone = 0
        value = try {
            Load.Ok(
                when {
                    submitted.isNotBlank() -> Vimm.search(submitted, code)
                    section == ALL -> Vimm.allGames(code) { partial, n ->
                        pagesDone = n
                        if (partial.isNotEmpty()) value = Load.Ok(partial)
                    }
                    else -> Vimm.section(code, section)
                }
            ).also { pagesDone = Vimm.sections.size }
        } catch (e: Exception) {
            Load.Err(e.message ?: "Couldn't reach vimm.net")
        }
    }
    val raw = (state as? Load.Ok)?.value.orEmpty()
    // vimm's own Top 10 for the system leads the "Top rated" order.
    val topIds by produceState(emptyList<Int>(), code) { value = runCatching { Vimm.topGames(code).map { it.id } }.getOrDefault(emptyList()) }
    // Sort off the main thread; big systems have thousands of games.
    val games by produceState(raw, raw, byRating, topIds) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            if (byRating) rankTopRated(raw, topIds) else raw.sortedBy { it.title.lowercase() }
        }
    }
    LaunchedEffect(games.take(30).map { it.id }) { prefetchArt(context, games, 30) }
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

    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val headerShown = rememberHeaderVisible(grid)
    Column(Modifier.fillMaxSize()) {
      androidx.compose.animation.AnimatedVisibility(
        visible = headerShown,
        enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.fadeOut(),
      ) {
      Column {
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
                        label = { Text(if (s == ALL) "All games" else s, fontWeight = FontWeight.Bold) },
                    )
                }
            }
        } else {
            Text("Results for “$submitted”", color = Muted, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // Count, sort switch and loading progress.
        if (state is Load.Ok) {
            Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val shownCount = if (region == null) games.size else games.count { region in it.regions }
                val loadingAll = section == ALL && submitted.isBlank() && pagesDone < Vimm.sections.size
                Text(
                    "$shownCount games" + if (loadingAll) " · loading ${pagesDone * 100 / Vimm.sections.size}%" else "",
                    color = Muted, style = MaterialTheme.typography.labelLarge,
                )
                if (!loadingAll && section == ALL && submitted.isBlank() && Vimm.incomplete) {
                    Text("  ·  Top 10 only — pick a letter for the rest", color = Muted, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.weight(1f))
                if (section == ALL && submitted.isBlank()) {
                    val p = com.vaultview.app.data.ArtSaver.progress[code]
                    val saved = remember(p) { com.vaultview.app.data.ArtSaver.isSaved(context, code) }
                    ToolButton(
                        when {
                            p != null -> "Saving art ${(p * 100).toInt()}%"
                            saved -> "Art saved"
                            else -> "Save art offline"
                        },
                        { com.vaultview.app.data.ArtSaver.save(context, code, raw) },
                        icon = SaveArtIcon,
                        enabled = p == null && raw.isNotEmpty(),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                SortToggle(byRating) { byRating = it }
            }
        }
      }
      }

        when (val s = state) {
            Load.Loading -> SkeletonGrid()
            is Load.Err -> CenterMessage(s.message, "Try again") { retry++ }
            is Load.Ok -> {
                val shown = if (region == null) games else games.filter { region in it.regions }
                if (shown.isEmpty()) CenterMessage("No games here") else GameGrid(shown, onGame, state = grid)
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
        ToolButton(selected ?: "All regions", { open = true }, icon = GlobeIcon, trailing = Icons.Filled.ArrowDropDown)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All regions") }, onClick = { onPick(null); open = false })
            regions.forEach { r ->
                DropdownMenuItem(text = { Text("${flag(r)} $r") }, onClick = { onPick(r); open = false })
            }
        }
    }
}

/** "Top rated" / "A–Z" switch. */
@Composable
private fun SortToggle(byRating: Boolean, onChange: (Boolean) -> Unit) {
    ToolSwitch(listOf("Top rated", "A–Z"), if (byRating) 0 else 1, { onChange(it == 0) })
}

/** Re-releases, chips, pirates, demos and the like: they tend to have a perfect score from a vote or two. */
private val sideRelease = Regex(
    """\((?:[^)]*\b)?(Virtual Console|Switch Online|Classic Mini|Enhancement Chip|Competition|Pirate|Proto|Prototype|Beta|Demo|Sample|Kiosk|BIOS|Program|Test|Collection|Evercade|Aftermarket|Unl)\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * "Top rated" order: vimm's Top 10 first, then regular releases by rating (a lone 10.0 usually
 * means one or two votes, so it counts a little lower), then side releases, then unrated games.
 */
internal fun rankTopRated(games: List<Game>, topIds: List<Int>): List<Game> {
    fun junk(g: Game) = sideRelease.containsMatchIn(g.title) || g.tags.any { it == "Unlicensed" || it == "Hack" || it == "Prototype" }
    fun score(g: Game): Double = g.rating?.toDoubleOrNull()?.let { if (it >= 9.95) it - 1.2 else it } ?: -1.0
    return games.sortedWith(
        compareBy<Game> { topIds.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .thenBy { if (it.rating == null) 2 else if (junk(it)) 1 else 0 }
            .thenByDescending { score(it) }
            .thenBy { it.title.lowercase() }
    )
}
