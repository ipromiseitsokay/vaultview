package com.vaultview.app.ui

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.vaultview.app.R
import com.vaultview.app.Sfx
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vaultview.app.data.DownloadState
import com.vaultview.app.data.Downloads
import com.vaultview.app.data.Game
import com.vaultview.app.data.Updater
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Browse", BrowseIcon),
    Tab("library", "Library", LibraryIcon),
    Tab("saved", "Saved", Icons.Filled.Favorite),
    Tab("downloads", "Downloads", DownloadIcon),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

fun gameRoute(g: Game) = "game/${g.id}?title=${Uri.encode(g.title)}&system=${Uri.encode(g.system ?: "")}"

@Composable
fun VaultRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var selected by remember { mutableStateOf<Game?>(null) }
    var owned by remember { mutableStateOf<com.vaultview.app.data.LibraryGame?>(null) }
    var rolling by remember { mutableStateOf(false) }
    // Start: bring up the game you played last, with Play ready.
    LaunchedEffect(com.vaultview.app.Controls.startPresses) {
        if (com.vaultview.app.Controls.startPresses == 0) return@LaunchedEffect
        com.vaultview.app.data.Library.games
            .mapNotNull { g -> com.vaultview.app.data.Playtime.of(g)?.let { g to it.lastPlayed } }
            .maxByOrNull { it.second }?.first?.let { owned = it }
    }
    val onGame: (Game) -> Unit = { Sfx.tap(Sfx.Cue.SELECT); selected = it }
    // Tapping the logo switches the first tab between the Vault and Ports.
    var ports by rememberSaveable { mutableStateOf(false) }
    val shownTabs = if (ports) listOf(Tab("ports", "Ports", PortsIcon)) + tabs.drop(1) else tabs
    fun switchMode() {
        Sfx.tap(Sfx.Cue.SELECT)
        ports = !ports
        nav.navigate(if (ports) "ports" else "home") {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp > config.screenHeightDp

    val appContext = LocalContext.current
    LaunchedEffect(Unit) { Updater.check(quiet = true) }
    // Read the ROMs folder early so Continue playing and the Library are ready.
    LaunchedEffect(com.vaultview.app.data.Settings.folderUri) {
        if (com.vaultview.app.data.Library.needsRescan()) com.vaultview.app.data.Library.scan(appContext)
    }

    // Only changes when a download starts or ends, not on every progress tick, so the menu doesn't redraw constantly.
    val activeDownloads by remember { derivedStateOf { Downloads.items.count { it.active } } }
    fun isActive(t: Tab) = route?.substringBefore('/')?.substringBefore('?') == t.route ||
        ((t.route == "home" || t.route == "ports") && (route?.startsWith("system/") == true || route?.startsWith("game/") == true || route == "search"))
    fun go(t: Tab) = nav.navigate(t.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    // Second screen (AYN Thor): what's showing, and what its buttons do.
    SideEffect {
        Deck.route = route
        Deck.ports = ports
        Deck.onBack = { if (selected != null) selected = null else nav.popBackStack() }
        Deck.onSearch = { if (!ports) nav.navigate("search") }
        Deck.onSurprise = { rolling = true }
        Deck.onSwitch = { switchMode() }
        Deck.onTab = { r -> (shownTabs.firstOrNull { it.route == r } ?: tabs.firstOrNull { it.route == r })?.let { go(it) } }
    }

    CompositionLocalProvider(LocalWide provides wide) {
        if (wide) {
            Surface(color = Bg, contentColor = MaterialTheme.colorScheme.onBackground) {
            Row(Modifier.fillMaxSize().systemBarsPadding()) {
                NavigationRail(
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxHeight()
                        .background(Brush.verticalGradient(if (ports) listOf(PortsTop, VimmNavy) else listOf(VimmMaroon, VimmMaroonDeep)))
                        .drawBehind { drawRect((if (ports) Accent else VimmRed).copy(alpha = 0.6f), topLeft = Offset(size.width - 2.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height)) },
                ) {
                    Spacer(Modifier.height(6.dp))
                    ModeSwitch(ports) { switchMode() }
                    Spacer(Modifier.weight(1f).heightIn(min = 6.dp))
                    shownTabs.forEach { t ->
                        NavigationRailItem(
                            selected = isActive(t), onClick = { Sfx.tap(Sfx.Cue.SELECT); go(t) },
                            icon = { TabIcon(t, activeDownloads, isActive(t)) },
                            label = { Text(t.label, maxLines = 1, fontWeight = FontWeight.Bold) },
                            colors = vimmRailColors(),
                            modifier = Modifier.focusGlow(),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f)) {
                    UpdateBanner()
                    Box(Modifier.weight(1f)) {
                        // The one vimm.net page, under every screen (64dp leaves the game page's top bar clear).
                        WebHost.Host(Modifier.fillMaxSize().padding(top = 64.dp))
                        Host(nav, ports, onGame, { owned = it }, { rolling = true }, Modifier.fillMaxSize())
                    }
                }
            }
            }
        } else {
            Scaffold(
                containerColor = Bg,
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        modifier = Modifier.background(Brush.verticalGradient(listOf(VimmMaroon, VimmMaroonDeep))),
                    ) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                colors = vimmBarColors(),
                                selected = isActive(t), onClick = { Sfx.tap(Sfx.Cue.SELECT); go(t) },
                                icon = { TabIcon(t, activeDownloads, isActive(t)) },
                                label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                },
            ) { pad ->
                Column(Modifier.padding(pad)) {
                    UpdateBanner()
                    Box(Modifier.weight(1f)) {
                        // The one vimm.net page, under every screen (64dp leaves the game page's top bar clear).
                        WebHost.Host(Modifier.fillMaxSize().padding(top = 64.dp))
                        Host(nav, ports, onGame, { owned = it }, { rolling = true }, Modifier.fillMaxSize())
                    }
                }
            }
        }

        selected?.let { g ->
            GameSheet(g, onDismiss = { selected = null }, onGet = { Sfx.tap(Sfx.Cue.SELECT); selected = null; nav.navigate(gameRoute(g)) })
        }
        if (rolling) RandomSheet(
            onDismiss = { rolling = false },
            onGame = { selected = it },
            onOwned = { owned = it },
        )
        owned?.let { g ->
            LibrarySheet(g, onDismiss = { owned = null }, onVimm = { id ->
                owned = null; nav.navigate(gameRoute(Game(id, g.title, g.system?.name)))
            })
        }
    }
}

@Composable
private fun Host(nav: NavHostController, ports: Boolean, onGame: (Game) -> Unit, onOwned: (com.vaultview.app.data.LibraryGame) -> Unit, onRandom: () -> Unit, modifier: Modifier) {
    // Tabs cross-fade; drilling into a system or game slides in from the right.
    val ease = tween<Float>(320, easing = FastOutSlowInEasing)
    fun deep(route: String?) = route?.startsWith("system/") == true || route?.startsWith("game/") == true || route == "search"
    NavHost(
        nav, startDestination = "home", modifier = modifier,
        enterTransition = {
            if (deep(targetState.destination.route)) fadeIn(ease) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 6 }
            else fadeIn(tween(220, delayMillis = 60))
        },
        exitTransition = {
            if (deep(targetState.destination.route)) fadeOut(tween(200)) + scaleOut(tween(320), targetScale = 0.97f)
            else fadeOut(tween(160))
        },
        popEnterTransition = {
            if (deep(initialState.destination.route)) fadeIn(ease) + scaleIn(tween(320), initialScale = 0.97f)
            else fadeIn(tween(220, delayMillis = 60))
        },
        popExitTransition = {
            if (deep(initialState.destination.route)) fadeOut(tween(200)) + slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 6 }
            else fadeOut(tween(160))
        },
    ) {
        composable("home") {
            HomeScreen(
                onSystem = { Sfx.tap(Sfx.Cue.SELECT); nav.navigate("system/$it") }, onGame = onGame,
                onSearch = { Sfx.tap(Sfx.Cue.SELECT); nav.navigate("search") },
                onOwned = { Sfx.tap(Sfx.Cue.SELECT); onOwned(it) },
                onRandom = { Sfx.tap(Sfx.Cue.SELECT); onRandom() },
            )
        }
        composable("system/{code}") { e ->
            SystemScreen(e.arguments?.getString("code").orEmpty(), onBack = { nav.popBackStack() }, onGame = onGame)
        }
        composable("ports") { PortsScreen(onGame) }
        composable("search") { SearchScreen(onGame, onBack = { nav.popBackStack() }) }
        composable("library") {
            // In Ports mode the Library is your installed ports; in the Vault it's your ROMs folder.
            if (ports) PortsLibraryScreen(onGame) else LibraryScreen(onOpen = { Sfx.tap(Sfx.Cue.SELECT); onOwned(it) })
        }
        composable("saved") { SavedScreen(onGame) }
        composable("downloads") { DownloadsScreen(onGame, onOwned = { Sfx.tap(Sfx.Cue.SELECT); onOwned(it) }) }
        composable("settings") { SettingsScreen() }
        composable("game/{id}?title={title}&system={system}") { e ->
            GameScreen(
                id = e.arguments?.getString("id")?.toIntOrNull() ?: 0,
                title = e.arguments?.getString("title").orEmpty(),
                system = e.arguments?.getString("system")?.ifBlank { null },
                onBack = { nav.popBackStack() },
                onShowDownloads = { nav.navigate("downloads") },
            )
        }
    }
}

@Composable
private fun TabIcon(t: Tab, activeDownloads: Int, selected: Boolean = false) {
    // The selected tab's icon pops slightly larger.
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (selected) 1.15f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "tab",
    )
    Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { TabIconInner(t, activeDownloads) }
}

@Composable
private fun TabIconInner(t: Tab, activeDownloads: Int) {
    if (t.route == "downloads" && activeDownloads > 0) {
        BadgedBox(badge = { Badge { Text("$activeDownloads") } }) { Icon(t.icon, null) }
    } else if (t.route == "settings" && Updater.available != null) {
        BadgedBox(badge = { Badge() }) { Icon(t.icon, null) }
    } else Icon(t.icon, null)
}

private val PortsTop = Color(0xFF0B1A5C)

/** The Vimm's Lair logo, which also flips between the Vault and Ports. */
@Composable
private fun ModeSwitch(ports: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.focusRing(shape, 1.05f).clip(shape).clickable(onClickLabel = if (ports) "Switch to the Vault" else "Switch to Ports", onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.vimm_logo),
            contentDescription = "Vimm's Lair",
            modifier = Modifier.size(40.dp).shadow(10.dp, CircleShape, ambientColor = if (ports) Accent else VimmRed, spotColor = if (ports) Accent else VimmRed),
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SwapIcon, null, Modifier.size(12.dp), tint = VimmSilver)
            Spacer(Modifier.width(3.dp))
            Text(if (ports) "PORTS" else "VAULT", color = VimmTitle, fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelSmall, letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp))
        }
    }
}

/** Navy pill with a white edge for the selected tab, like the site's rounded panels; silver text otherwise. */
@Composable
private fun vimmRailColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = Color.White, selectedTextColor = VimmTitle,
    unselectedIconColor = VimmSilver, unselectedTextColor = VimmSilver,
    indicatorColor = VimmNavy,
)

@Composable
private fun vimmBarColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color.White, selectedTextColor = VimmTitle,
    unselectedIconColor = VimmSilver, unselectedTextColor = VimmSilver,
    indicatorColor = VimmNavy,
)

@Composable
private fun UpdateBanner() {
    AnimatedVisibility(Updater.available != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        UpdateBannerContent()
    }
}

@Composable
private fun UpdateBannerContent() {
    val rel = Updater.available ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Surface(color = VimmNavy, modifier = Modifier.fillMaxWidth().drawBehind {
        drawRect(VimmRed.copy(alpha = 0.7f), topLeft = Offset(0f, size.height - 2.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
    }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Update ready: ${rel.name}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (Updater.busy) Text("${(Updater.progress * 100).toInt()}%", color = Muted)
            else Button(onClick = { scope.launch { Updater.install(ctx) } }) { Text("Install") }
        }
    }
}



