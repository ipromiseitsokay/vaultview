package com.vaultview.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaultview.app.data.Brand
import com.vaultview.app.data.Library
import com.vaultview.app.data.LibraryGame
import com.vaultview.app.data.Emulators
import com.vaultview.app.data.Playtime
import com.vaultview.app.data.RetroAchievements
import com.vaultview.app.data.Settings
import kotlinx.coroutines.launch

private enum class Grouping(val label: String) { SYSTEM("Systems"), MAKER("Manufacturers"), PLAYED("Recently played"), RECENT("Recently added") }

@Composable
fun LibraryScreen(onOpen: (LibraryGame) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wide = LocalWide.current
    var groupingName by rememberSaveable { mutableStateOf(Grouping.SYSTEM.name) }
    val grouping = Grouping.valueOf(groupingName)
    val grid = rememberLazyGridState()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { Settings.setFolder(context, uri); scope.launch { Library.scan(context) } }
    }

    LaunchedEffect(Settings.folderUri) { if (Library.needsRescan() || Library.games.isEmpty()) Library.scan(context) }

    // Group the games into titled sections.
    val sections: List<Pair<String, List<LibraryGame>>> = remember(Library.games, grouping, Playtime.records.size, Playtime.records.values.sumOf { it.plays }) {
        val g = Library.games
        when (grouping) {
            Grouping.SYSTEM -> g.groupBy { it.system }.entries
                .sortedWith(compareBy({ it.key == null }, { it.key?.brand?.ordinal }, { it.key?.year }))
                .map { (s, list) -> (s?.name ?: "Other") to list }
            Grouping.MAKER -> g.groupBy { it.system?.brand }.entries
                .sortedWith(compareBy({ it.key == null }, { it.key?.ordinal }))
                .map { (b, list) -> (b?.label ?: "Other") to list.sortedWith(compareBy({ it.system?.year }, { it.title })) }
            Grouping.RECENT -> listOf("Newest first" to g.sortedByDescending { it.modified })
            Grouping.PLAYED -> {
                val played = g.filter { Playtime.of(it) != null }.sortedByDescending { Playtime.of(it)!!.lastPlayed }
                if (played.isEmpty()) emptyList() else listOf("Recently played" to played)
            }
        }
    }
    // Where each section header sits in the grid, for the quick-jump chips.
    val headerIndex = remember(sections) {
        var i = 0
        sections.map { (name, list) -> (name to i).also { i += 1 + list.size } }
    }

    val headerShown = rememberHeaderVisible(grid)
    var stats by remember { mutableStateOf(false) }
    if (stats) StatsSheet(onDismiss = { stats = false })
    Column(Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = headerShown || Settings.folderUri == null,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black)
                val systems = Library.games.mapNotNull { it.system }.distinct().size
                Text(
                    when {
                        Settings.folderUri == null -> "Pick your ROMs folder to see what's on this device"
                        Library.scanning && Library.games.isEmpty() -> "Looking through ${Settings.folderLabel()}…"
                        else -> "${Library.games.size} games · $systems systems" +
                            (Playtime.records.values.sumOf { it.seconds }.takeIf { it > 0 }?.let { " · ${Playtime.format(it)} played" } ?: "") +
                            " · ${Settings.folderLabel()}"
                    },
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (Playtime.records.isNotEmpty()) {
                ToolButton("Stats", { stats = true }, icon = StatsIcon)
                Spacer(Modifier.width(6.dp))
            }
            if (Library.scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else if (Settings.folderUri != null) IconButton(
                onClick = { scope.launch { Library.scan(context) } }, modifier = Modifier.focusRing(CircleShape),
            ) { Icon(Icons.Filled.Refresh, "Rescan folder") }
        }
        }

        if (Settings.folderUri == null) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your library shows every game in your ROMs folder, including ones you didn't get through VaultView.",
                    color = Muted)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { pickFolder.launchSafely(context) }, modifier = Modifier.initialFocus().focusRing(RoundedCornerShape(50))) {
                    Text("Choose ROMs folder")
                }
            }
            return@Column
        }

        // Grouping and quick-jump chips.
        androidx.compose.animation.AnimatedVisibility(
            visible = headerShown,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Grouping.entries) { gr ->
                FilterChip(
                    selected = gr == grouping, onClick = { groupingName = gr.name; scope.launch { grid.scrollToItem(0) } },
                    label = { Text(gr.label, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.focusRing(RoundedCornerShape(8.dp), 1.06f),
                )
            }
            if ((grouping == Grouping.SYSTEM || grouping == Grouping.MAKER) && sections.size > 1) {
                item { VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp)) }
                items(headerIndex) { (name, index) ->
                    AssistChip(
                        onClick = { scope.launch { grid.animateScrollToItem(index) } },
                        label = { Text(name) },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp), 1.06f),
                    )
                }
            }
        }
        }

        when {
            Library.error != null -> CenterMessage(Library.error!!, "Try again") { scope.launch { Library.scan(context) } }
            Library.games.isEmpty() && Library.scanning -> SkeletonGrid()
            grouping == Grouping.PLAYED && sections.isEmpty() -> CenterMessage("Games you play from here will show up with their playtime.")
            Library.games.isEmpty() -> CenterMessage(
                "No games found yet. Games go in system folders like snes or psx inside ${Settings.folderLabel()}.",
            )
            else -> LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(if (wide) 120.dp else 108.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                var running = 0
                sections.forEachIndexed { si, (name, list) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h-$name") { SectionHeader(name, list) }
                    val start = running
                    itemsIndexed(list, key = { _, g -> g.key }) { i, g ->
                        Box(Modifier.animateItem().staggerIn(start + i).then(if (si == 0 && i == 0) Modifier.initialFocus() else Modifier)) {
                            LibraryCard(g) { onOpen(g) }
                        }
                    }
                    running += list.size
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(name: String, list: List<LibraryGame>) {
    val color = list.firstOrNull()?.system?.brand?.color ?: Muted
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(name, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text("${list.size}", color = Muted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LibraryCard(g: LibraryGame, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.focusRing(shape).clip(shape).clickable(onClick = onClick).padding(bottom = 4.dp)) {
        CoverArt(
            g.gameId, g.title, Modifier.fillMaxWidth().aspectRatio(0.75f).clip(shape),
            system = g.system?.code, regions = g.regions, exactName = g.baseName,
        )
        Spacer(Modifier.height(6.dp))
        Text(g.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(
            listOfNotNull(
                Playtime.of(g)?.let { "▶ " + Playtime.format(it.seconds) },
                g.system?.name, g.regions.take(3).joinToString(" ") { flag(it) }.ifBlank { null },
            ).joinToString("  "),
            style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

private fun size(b: Long) = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.0f KB".format(b / 1024.0)
}

/** Details for a game on the device, with Play. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibrarySheet(g: LibraryGame, onDismiss: () -> Unit, onVimm: (Int) -> Unit) {
    val context = LocalContext.current
    val wide = LocalWide.current
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val v = Emulators.version // re-read the choice after it changes or an emulator gets installed
    val choice = remember(g.key, v) { Emulators.effectiveChoice(context, g.system?.code) }
    val played = Playtime.of(g)
    val ra by produceState<RaState>(RaState.Loading, g.key) {
        value = if (!RetroAchievements.connected) RaState.Off
        else runCatching { RetroAchievements.progressFor(g)?.let { RaState.Ready(it) } ?: RaState.None }
            .getOrElse { RaState.Failed(it.message ?: "Couldn't reach RetroAchievements") }
    }
    val playFocus = remember { FocusRequester() }

    fun play() {
        val c = choice
        if (c == null) { picking = true; return }
        when (val r = Emulators.launch(context, g, c)) {
            Emulators.Result.Started -> { Playtime.start(g); problem = null; onDismiss() }
            is Emulators.Result.Problem -> problem = r.message
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        sheetMaxWidth = if (wide) 1000.dp else BottomSheetDefaults.SheetMaxWidth,
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CoverArt(g.gameId, g.title, Modifier.width(if (wide) 170.dp else 120.dp).aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)),
                system = g.system?.code, regions = g.regions, exactName = g.baseName)
            Column(Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                g.system?.let { Text(it.name, color = Accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp)) }
                Spacer(Modifier.height(14.dp))

                // Play, and which emulator it uses.
                Button(
                    onClick = { com.vaultview.app.Sfx.tap(com.vaultview.app.Sfx.Cue.SELECT); play() },
                    modifier = Modifier.widthIn(min = 220.dp).height(56.dp).focusRing(RoundedCornerShape(16.dp), 1.03f).focusRequester(playFocus),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(Emulators.label(choice, g.system?.code), color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (g.system != null) TextButton(onClick = { picking = true }, modifier = Modifier.focusRing(RoundedCornerShape(50))) { Text("Change") }
                }
                problem?.let { Text(it, color = Color(0xFFFF8A8A), style = MaterialTheme.typography.bodySmall) }

                Spacer(Modifier.height(12.dp))
                if (played != null) {
                    Fact("Played", Playtime.format(played.seconds) + " · " + played.plays + if (played.plays == 1) " session" else " sessions")
                    Fact("Last", android.text.format.DateUtils.getRelativeTimeSpanString(played.lastPlayed).toString())
                }
                Fact("File", g.fileName)
                Fact("Size", size(g.size))
                if (g.regions.isNotEmpty()) Fact("Region", g.regions.joinToString("  ") { "${flag(it)} $it" })

                AchievementsBlock(ra)

                g.gameId?.let { id ->
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { onVimm(id) }, modifier = Modifier.focusRing(RoundedCornerShape(50))) { Text("Game page") }
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (com.vaultview.app.Controls.usingController) runCatching { kotlinx.coroutines.delay(300); playFocus.requestFocus() } }

    if (picking && g.system != null) EmulatorPicker(g.system, onDismiss = { picking = false })
}

private sealed interface RaState {
    data object Loading : RaState
    data object Off : RaState
    data object None : RaState
    data class Failed(val message: String) : RaState
    data class Ready(val p: RetroAchievements.Progress) : RaState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementsBlock(state: RaState) {
    when (state) {
        RaState.Off -> return
        RaState.Loading -> {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(220.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
        RaState.None -> {
            Spacer(Modifier.height(14.dp))
            Text("No RetroAchievements set found for this game.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        is RaState.Failed -> {
            Spacer(Modifier.height(14.dp))
            Text(state.message, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        is RaState.Ready -> {
            val p = state.p
            Spacer(Modifier.height(16.dp))
            Surface(color = PanelHi, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("RetroAchievements", fontWeight = FontWeight.Bold, color = VimmTitle, modifier = Modifier.weight(1f))
                        Text("${p.earned} / ${p.total}", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    val frac by animateFloatAsState(if (p.total == 0) 0f else p.earned.toFloat() / p.total, tween(700), label = "ra")
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = VimmTitle, trackColor = Panel)
                    Text("${p.pointsEarned} of ${p.pointsTotal} points" + if (p.earnedHardcore > 0) " · ${p.earnedHardcore} hardcore" else "",
                        color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.achievements.take(24).forEach { a ->
                            coil.compose.AsyncImage(
                                model = a.badge, contentDescription = a.title,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.padding(top = 4.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
