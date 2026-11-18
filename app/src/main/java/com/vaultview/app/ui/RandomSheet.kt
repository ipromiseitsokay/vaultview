package com.vaultview.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaultview.app.Sfx
import com.vaultview.app.data.Brand
import com.vaultview.app.data.Game
import com.vaultview.app.data.GameSystem
import com.vaultview.app.data.Library
import com.vaultview.app.data.LibraryGame
import com.vaultview.app.data.Systems
import com.vaultview.app.data.Vimm
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One result from the roller: something to download, or something already on the device. */
private sealed interface Pick {
    val id: Int?; val title: String; val system: String?; val regions: List<String>; val exact: String?
    data class Vault(val g: Game) : Pick {
        override val id get() = g.id; override val title get() = g.title; override val system get() = g.system
        override val regions get() = g.regions; override val exact: String? get() = null
    }
    data class Owned(val g: LibraryGame) : Pick {
        override val id get() = g.gameId; override val title get() = g.title; override val system get() = g.system?.code
        override val regions get() = g.regions; override val exact: String? get() = g.baseName
    }
}

/** "Surprise me": spins through box art and lands on a random game, from the Vault or your Library. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RandomSheet(onDismiss: () -> Unit, onGame: (Game) -> Unit, onOwned: (LibraryGame) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val wide = LocalWide.current
    var fromLibrary by rememberSaveable { mutableStateOf(false) }
    var topOnly by rememberSaveable { mutableStateOf(true) }
    var brands by remember { mutableStateOf(setOf<Brand>()) }
    var systems by remember { mutableStateOf(setOf<String>()) }
    var frame by remember { mutableStateOf<Pick?>(null) }
    var result by remember { mutableStateOf<Pick?>(null) }
    var rolling by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val land = remember { Animatable(1f) }
    val rollFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val takeFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    // Which systems the roll can come from.
    fun allowed(): List<GameSystem> {
        val owned = if (fromLibrary) Library.games.mapNotNull { it.system }.toSet() else null
        return Systems.all.filter { s ->
            (owned == null || s in owned) &&
                (systems.isEmpty() || s.code in systems) &&
                (brands.isEmpty() || s.brand in brands || s.code in systems)
        }
    }

    suspend fun pickOne(): Pick? {
        val pool = allowed()
        if (pool.isEmpty()) return null
        if (fromLibrary) {
            val codes = pool.map { it.code }.toSet()
            return Library.games.filter { it.system?.code in codes }.randomOrNull()?.let { Pick.Owned(it) }
        }
        repeat(6) {
            val sys = pool.random()
            val list = if (topOnly) Vimm.topGames(sys.code)
                else Vimm.section(sys.code, Vimm.sections.random())
            val good = list.filter { g -> g.tags.none { it == "Unlicensed" || it == "Hack" } }
            good.randomOrNull()?.let { return Pick.Vault(it.copy(system = it.system ?: sys.name)) }
        }
        return null
    }

    fun roll() {
        if (rolling) return
        rolling = true; problem = null; result = null
        scope.launch {
            val answer = async { runCatching { pickOne() }.getOrNull() }
            // Faces for the reel: anything already loaded, so the spin starts instantly.
            val faces: List<Pick> = if (fromLibrary) Library.games.shuffled().take(30).map { Pick.Owned(it) }
                else allowed().flatMap { s -> Vimm.savedNow("top:${s.code}").orEmpty() }
                    .shuffled().take(30).map { Pick.Vault(it) }
            var wait = 55L
            var spins = 0
            while (spins < 18 || !answer.isCompleted) {
                if (faces.isNotEmpty()) frame = faces[spins % faces.size]
                Sfx.play(Sfx.Cue.MOVE)
                delay(wait)
                spins++
                if (spins > 10) wait = (wait * 1.12).toLong().coerceAtMost(240)
                if (spins > 80) break
            }
            val p = answer.await()
            if (p == null) {
                problem = if (allowed().isEmpty()) "Nothing matches those choices." else "Couldn't reach vimm.net. Try again."
                frame = null
            } else {
                frame = p; result = p
                land.snapTo(1.18f)
                Sfx.play(Sfx.Cue.SUCCESS)
                land.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            }
            rolling = false
            if (result != null && com.vaultview.app.Controls.usingController) runCatching { delay(120); takeFocus.requestFocus() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        sheetMaxWidth = if (wide) 1100.dp else BottomSheetDefaults.SheetMaxWidth,
    ) {
        val content: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Text("Surprise me", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Pick where to look, then roll.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(!fromLibrary, { fromLibrary = false }, SegmentedButtonDefaults.itemShape(0, 2),
                        Modifier.focusRing(RoundedCornerShape(50), 1.04f)) { Text("The Vault") }
                    SegmentedButton(fromLibrary, { fromLibrary = true }, SegmentedButtonDefaults.itemShape(1, 2),
                        Modifier.focusRing(RoundedCornerShape(50), 1.04f)) { Text("My Library") }
                }
                Spacer(Modifier.height(12.dp))
                Text("MANUFACTURERS", color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(Brand.entries) { b ->
                        FilterChip(
                            selected = b in brands, onClick = { brands = if (b in brands) brands - b else brands + b },
                            label = { Text(b.label) }, modifier = Modifier.focusRing(RoundedCornerShape(8.dp), 1.06f),
                        )
                    }
                }
                Text("SYSTEMS", color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                val shownSystems = Systems.all.filter { brands.isEmpty() || it.brand in brands }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(shownSystems, key = { it.code }) { s ->
                        FilterChip(
                            selected = s.code in systems, onClick = { systems = if (s.code in systems) systems - s.code else systems + s.code },
                            label = { Text(s.name) }, modifier = Modifier.focusRing(RoundedCornerShape(8.dp), 1.06f),
                        )
                    }
                }
                if (!fromLibrary) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Top rated only", modifier = Modifier.weight(1f))
                    Switch(topOnly, { topOnly = it }, Modifier.focusRing(RoundedCornerShape(50), 1.04f))
                }
            }
        }
        val rollButton: @Composable () -> Unit = {
            val label = if (result == null) "🎲  Roll" else "🎲  Roll again"
            val mod = Modifier.fillMaxWidth().height(if (result == null) 52.dp else 44.dp).focusRing(RoundedCornerShape(16.dp), 1.02f).focusRequester(rollFocus)
            if (result == null) Button(onClick = { roll() }, enabled = !rolling, modifier = mod, shape = RoundedCornerShape(16.dp)) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            } else FilledTonalButton(onClick = { roll() }, enabled = !rolling, modifier = mod, shape = RoundedCornerShape(16.dp)) {
                Text(label, fontWeight = FontWeight.Bold)
            }
            problem?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        }

        val reel: @Composable (Modifier) -> Unit = { m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                val f = frame
                Box(
                    Modifier.width(if (wide) 136.dp else 200.dp).aspectRatio(0.75f)
                        .graphicsLayer { scaleX = land.value; scaleY = land.value }
                        .clip(RoundedCornerShape(18.dp))
                        .then(if (f == null) Modifier.shimmer() else Modifier),
                ) {
                    if (f != null) CoverArt(f.id, f.title, Modifier.matchParentSize(), system = f.system, regions = f.regions, exactName = f.exact)
                    else Text("?", style = MaterialTheme.typography.displayLarge, color = Muted, modifier = Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.height(8.dp))
                Text(f?.title ?: " ", fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = if (wide) 1 else 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(f?.system?.let { Systems.find(it)?.name ?: it } ?: " ", color = Accent, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                val r = result
                if (r != null && !rolling) {
                    Button(
                        onClick = {
                            onDismiss()
                            when (r) { is Pick.Vault -> onGame(r.g); is Pick.Owned -> onOwned(r.g) }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).focusRing(RoundedCornerShape(16.dp), 1.02f).focusRequester(takeFocus),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(if (r is Pick.Owned) "▶  Play" else "Get game", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(8.dp))
                }
                rollButton()
            }
        }

        if (wide) {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                content(Modifier.weight(1.4f).verticalScroll(androidx.compose.foundation.rememberScrollState()))
                reel(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                reel(Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                content(Modifier.fillMaxWidth())
            }
        }
    }
    LaunchedEffect(Unit) { if (com.vaultview.app.Controls.usingController) runCatching { delay(300); rollFocus.requestFocus() } }
    // Load a few Top 10 lists (saved on disk after the first time) so the reel has covers to spin through.
    LaunchedEffect(brands, systems, fromLibrary) {
        if (!fromLibrary) allowed().shuffled().take(5).forEach { s ->
            runCatching { prefetchArt(context, Vimm.topGames(s.code), 10) }
        }
    }
}
