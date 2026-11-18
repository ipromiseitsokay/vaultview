package com.vaultview.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.vaultview.app.data.Settings
import kotlinx.coroutines.launch

private enum class Grouping(val label: String) { SYSTEM("Systems"), MAKER("Makers"), RECENT("Recently added") }

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
    val sections: List<Pair<String, List<LibraryGame>>> = remember(Library.games, grouping) {
        val g = Library.games
        when (grouping) {
            Grouping.SYSTEM -> g.groupBy { it.system }.entries
                .sortedWith(compareBy({ it.key == null }, { it.key?.brand?.ordinal }, { it.key?.year }))
                .map { (s, list) -> (s?.name ?: "Other") to list }
            Grouping.MAKER -> g.groupBy { it.system?.brand }.entries
                .sortedWith(compareBy({ it.key == null }, { it.key?.ordinal }))
                .map { (b, list) -> (b?.label ?: "Other") to list.sortedWith(compareBy({ it.system?.year }, { it.title })) }
            Grouping.RECENT -> listOf("Newest first" to g.sortedByDescending { it.modified })
        }
    }
    // Where each section header sits in the grid, for the quick-jump chips.
    val headerIndex = remember(sections) {
        var i = 0
        sections.map { (name, list) -> (name to i).also { i += 1 + list.size } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black)
                val systems = Library.games.mapNotNull { it.system }.distinct().size
                Text(
                    when {
                        Settings.folderUri == null -> "Pick your ROMs folder to see what's on this device"
                        Library.scanning && Library.games.isEmpty() -> "Looking through ${Settings.folderLabel()}…"
                        else -> "${Library.games.size} games · $systems systems · ${Settings.folderLabel()}"
                    },
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (Library.scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else if (Settings.folderUri != null) IconButton(
                onClick = { scope.launch { Library.scan(context) } }, modifier = Modifier.focusRing(CircleShape),
            ) { Icon(Icons.Filled.Refresh, "Rescan folder") }
        }

        if (Settings.folderUri == null) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your library shows every game in your ROMs folder, including ones you didn't get through VaultView.",
                    color = Muted)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { pickFolder.launch(null) }, modifier = Modifier.initialFocus().focusRing(RoundedCornerShape(50))) {
                    Text("Choose ROMs folder")
                }
            }
            return@Column
        }

        // Grouping and quick-jump chips.
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
            if (grouping != Grouping.RECENT && sections.size > 1) {
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

        when {
            Library.error != null -> CenterMessage(Library.error!!, "Try again") { scope.launch { Library.scan(context) } }
            Library.games.isEmpty() && Library.scanning -> SkeletonGrid()
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
            listOfNotNull(g.system?.name, g.regions.take(3).joinToString(" ") { flag(it) }.ifBlank { null }).joinToString("  "),
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

/** Details for a game on the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySheet(g: LibraryGame, onDismiss: () -> Unit, onVimm: (Int) -> Unit) {
    val wide = LocalWide.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        sheetMaxWidth = if (wide) 900.dp else BottomSheetDefaults.SheetMaxWidth,
    ) {
        Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CoverArt(g.gameId, g.title, Modifier.width(if (wide) 160.dp else 120.dp).aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)),
                system = g.system?.code, regions = g.regions, exactName = g.baseName)
            Column(Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                g.system?.let { Text(it.name, color = Accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp)) }
                Spacer(Modifier.height(10.dp))
                Fact("File", g.fileName)
                Fact("Size", size(g.size))
                Fact("Folder", listOfNotNull(Settings.folderLabel(), g.folder.ifBlank { null }).joinToString("/"))
                if (g.regions.isNotEmpty()) Fact("Region", g.regions.joinToString("  ") { "${flag(it)} $it" })
                Spacer(Modifier.height(16.dp))
                Surface(color = PanelHi, shape = RoundedCornerShape(12.dp)) {
                    Text("Launching games from here is coming next.", color = Muted,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
                g.gameId?.let { id ->
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { onVimm(id) }, modifier = Modifier.focusRing(RoundedCornerShape(50))) { Text("Game page") }
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
