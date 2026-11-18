package com.vaultview.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.vaultview.app.Sfx
import com.vaultview.app.data.Game
import com.vaultview.app.data.Installer
import com.vaultview.app.data.Library
import com.vaultview.app.data.Port
import com.vaultview.app.data.Ports
import com.vaultview.app.data.Systems
import kotlinx.coroutines.launch

/** Ports mode: native Android ports from the Quiver catalog, dressed like the Vault. */
@Composable
fun PortsScreen(onGame: (Game) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Ports.load(context) }
    var console by rememberSaveable { mutableStateOf<String?>(null) }
    var vaultOnly by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf<Port?>(null) }
    val grid = rememberLazyGridState()
    val headerVisible = rememberHeaderVisible(grid)

    val consoles = remember(Ports.items) {
        Ports.items.flatMap { it.consoles.take(1) }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
    }
    val shown = remember(Ports.items, console, vaultOnly) {
        Ports.items.filter { (console == null || console in it.consoles) && (!vaultOnly || it.vimmSystem != null) }
    }

    Column(Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(headerVisible) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("Ports", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(12.dp))
                            Text("Classic games rebuilt to run natively on Android", color = Muted,
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    QuiverButton()
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterTag("Uses a Vault game", vaultOnly) { vaultOnly = !vaultOnly } }
                    item { Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.12f))) }
                    item { FilterTag("All", console == null) { console = null } }
                    items(consoles) { c -> FilterTag(Ports.consoleNames[c] ?: c.uppercase(), console == c) { console = if (console == c) null else c } }
                }
            }
        }

        when {
            Ports.items.isEmpty() && Ports.loading -> SkeletonGrid()
            Ports.items.isEmpty() && Ports.error != null -> CenterMessage(Ports.error!!, "Try again") { scope.launch { Ports.load(context, force = true) } }
            shown.isEmpty() -> CenterMessage("No ports here yet")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (LocalWide.current) 136.dp else 116.dp),
                state = grid,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(shown, key = { it.slug }) { p ->
                    PortCard(p, Modifier.animateItem()) { Sfx.tap(Sfx.Cue.SELECT); open = p }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Port list from the Quiver Launcher catalog (quiverlauncher.com). Downloads come from each project's own releases.",
                        color = Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
        }
    }

    open?.let { p -> PortSheet(p, onDismiss = { open = null }, onGame = { open = null; onGame(it) }) }
}

@Composable
private fun FilterTag(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.height(36.dp).focusRing(shape, 1.05f).clip(shape)
            .background(if (on) PanelHi else Panel)
            .border(1.dp, if (on) VimmTitle.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else Muted,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/** Cover art for a port: its 2:3 capsule, else its icon, else a plain title card. */
@Composable
private fun PortArt(p: Port, modifier: Modifier) {
    val model = p.capsule ?: p.icon
    val card: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(VimmNavy, PanelHi))).padding(10.dp), contentAlignment = Alignment.Center) {
            Text(p.gameTitle ?: p.name, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
    }
    Box(modifier.background(PanelHi)) {
        if (model == null) card() else SubcomposeAsyncImage(
            model = model, contentDescription = p.name,
            contentScale = if (p.capsule != null) ContentScale.Crop else ContentScale.Fit,
            loading = { Box(Modifier.fillMaxSize().shimmer()) },
            error = { card() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PortCard(p: Port, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val installed = remember(p.slug) { Ports.packageOf(context, p.slug) != null }
    Column(modifier.focusRing(RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(bottom = 4.dp)) {
        Box {
            PortArt(p, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
            if (installed) Text("INSTALLED", color = Color.Black, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(VimmTitle).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(p.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(
            listOfNotNull(p.consoles.firstOrNull()?.let { Ports.consoleNames[it] ?: it }, p.project).joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Open Quiver Launcher if it's installed, or get the official build. */
@Composable
fun QuiverButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(Ports.isInstalled(context, Ports.QUIVER_PACKAGE)) }
    LifecycleResumed { installed = Ports.isInstalled(context, Ports.QUIVER_PACKAGE) }
    val busy = Installer.progress["quiver"]
    ToolButton(
        when {
            busy != null -> "Downloading Quiver ${(busy * 100).toInt()}%"
            installed -> "Open Quiver Launcher"
            else -> "Get Quiver Launcher"
        },
        {
            if (installed) Ports.launch(context, Ports.QUIVER_PACKAGE)
            else scope.launch {
                Installer.installGitHub(context, "quiver", Ports.QUIVER_REPO) { names ->
                    names.firstOrNull { it.equals("QuiverLauncher-android.apk", true) } ?: names.firstOrNull { it.endsWith(".apk", true) }
                }
            }
        },
        modifier = modifier, icon = if (installed) SwapIcon else DownloadIcon, enabled = busy == null,
    )
}

/** Runs [block] every time the app comes back to the front (e.g. after an install prompt). */
@Composable
fun LifecycleResumed(block: () -> Unit) {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val current by rememberUpdatedState(block)
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) current() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortSheet(p: Port, onDismiss: () -> Unit, onGame: (Game) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wide = LocalWide.current
    val focus = remember { FocusRequester() }
    var pkg by remember { mutableStateOf(Ports.packageOf(context, p.slug)) }
    LifecycleResumed { pkg = Ports.packageOf(context, p.slug) }
    val vimm by produceState<Game?>(null, p.slug) { value = Ports.vimmGame(p) }
    // Already have the original? (matched by title in the scanned ROMs folder)
    val owned = remember(vimm, Library.games) {
        vimm?.let { v -> Library.games.firstOrNull { it.gameId == v.id || it.title.equals(v.title, true) } }
    }
    val busy = Ports.progress[p.slug]
    val sysName = p.vimmSystem?.let { Systems.byCode(it)?.name } ?: p.consoles.firstOrNull()?.let { Ports.consoleNames[it] ?: it }

    val info: @Composable ColumnScope.() -> Unit = {
        Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(listOfNotNull(p.project, p.developer?.let { "by $it" }).joinToString("  ·  "),
            color = Accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        sysName?.let { PortFact("Original", "${p.gameTitle ?: p.name} · $it") }
        p.version?.let { PortFact("Version", it) }
        if (p.reviews > 0) PortFact("Reviews", "${p.reviews} on Quiver")
        val needs = p.needs.ifEmpty { if (p.vimmSystem != null) listOf("Your copy of ${p.gameTitle ?: "the original game"}") else emptyList() }
        if (needs.isNotEmpty()) {
            Text("NEEDS", color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            needs.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
        }
    }

    val buttons: @Composable ColumnScope.() -> Unit = {
        val shape = RoundedCornerShape(12.dp)
        Button(
            onClick = {
                val installed = pkg
                if (installed != null) Ports.launch(context, installed) else scope.launch { Ports.install(context, p) }
            },
            enabled = busy == null,
            modifier = Modifier.fillMaxWidth().height(52.dp).focusRing(shape, 1.02f).focusRequester(focus),
            shape = shape,
        ) {
            when {
                busy != null -> Text("Downloading ${(busy * 100).toInt()}%", fontWeight = FontWeight.Bold)
                pkg != null -> Text("Play", fontWeight = FontWeight.Bold)
                else -> { Icon(DownloadIcon, null); Spacer(Modifier.width(8.dp)); Text("Install port", fontWeight = FontWeight.Bold) }
            }
        }
        Ports.messages[p.slug]?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        val v = vimm
        when {
            owned != null -> Text("You already have ${owned.title} in your ROMs folder.", color = Color(0xFF7BD88F),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
            v != null -> {
                ToolButton("Get the original from the Vault", { onGame(v) }, icon = BrowseIcon, modifier = Modifier.fillMaxWidth())
                Text(listOfNotNull(v.title, v.system).joinToString("  ·  "), color = Muted, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        QuiverButton(Modifier.fillMaxWidth())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        sheetMaxWidth = if (wide) 1000.dp else BottomSheetDefaults.SheetMaxWidth,
    ) {
        if (wide) {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PortArt(p, Modifier.width(170.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { info() }
                Column(Modifier.weight(1f)) {
                    p.hero?.let {
                        coil.compose.AsyncImage(it, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2.6f).clip(RoundedCornerShape(12.dp)).alpha(0.9f))
                        Spacer(Modifier.height(14.dp))
                    }
                    buttons()
                }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Row {
                    PortArt(p, Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { info() }
                }
                Spacer(Modifier.height(16.dp))
                buttons()
            }
        }
    }
    LaunchedEffect(Unit) { if (com.vaultview.app.Controls.usingController) runCatching { kotlinx.coroutines.delay(350); focus.requestFocus() } }
}

@Composable
private fun PortFact(label: String, value: String) {
    Row(Modifier.padding(top = 4.dp)) {
        Text("$label  ", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
