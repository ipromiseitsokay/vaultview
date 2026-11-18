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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.vaultview.app.R
import com.vaultview.app.Sfx
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    Tab("home", "Browse", Icons.Filled.Home),
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
    val onGame: (Game) -> Unit = { Sfx.tap(Sfx.Cue.SELECT); selected = it }
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp > config.screenHeightDp

    LaunchedEffect(Unit) { Updater.check(quiet = true) }

    val activeDownloads = Downloads.items.count { it.active }
    fun isActive(t: Tab) = route?.substringBefore('/')?.substringBefore('?') == t.route ||
        (t.route == "home" && (route?.startsWith("system/") == true || route?.startsWith("game/") == true || route == "search"))
    fun go(t: Tab) = nav.navigate(t.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    CompositionLocalProvider(LocalWide provides wide) {
        if (wide) {
            Surface(color = Bg, contentColor = MaterialTheme.colorScheme.onBackground) {
            Row(Modifier.fillMaxSize().systemBarsPadding()) {
                NavigationRail(
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxHeight()
                        .background(Brush.verticalGradient(listOf(VimmMaroon, VimmMaroonDeep)))
                        .drawBehind { drawRect(VimmRed.copy(alpha = 0.6f), topLeft = Offset(size.width - 2.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height)) },
                ) {
                    Spacer(Modifier.height(6.dp))
                    Logo()
                    Spacer(Modifier.weight(1f).heightIn(min = 6.dp))
                    tabs.forEach { t ->
                        NavigationRailItem(
                            selected = isActive(t), onClick = { Sfx.tap(Sfx.Cue.SELECT); go(t) },
                            icon = { TabIcon(t, activeDownloads) },
                            label = { Text(t.label, maxLines = 1, fontWeight = FontWeight.Bold) },
                            colors = vimmRailColors(),
                            modifier = Modifier.focusGlow(),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f)) {
                    UpdateBanner()
                    Host(nav, onGame, { owned = it }, Modifier.weight(1f))
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
                                icon = { TabIcon(t, activeDownloads) },
                                label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                },
            ) { pad ->
                Column(Modifier.padding(pad)) {
                    UpdateBanner()
                    Host(nav, onGame, { owned = it }, Modifier.weight(1f))
                }
            }
        }

        selected?.let { g ->
            GameSheet(g, onDismiss = { selected = null }, onGet = { Sfx.tap(Sfx.Cue.SELECT); selected = null; nav.navigate(gameRoute(g)) })
        }
        owned?.let { g ->
            LibrarySheet(g, onDismiss = { owned = null }, onVimm = { id ->
                owned = null; nav.navigate(gameRoute(Game(id, g.title, g.system?.name)))
            })
        }
    }
}

@Composable
private fun Host(nav: NavHostController, onGame: (Game) -> Unit, onOwned: (com.vaultview.app.data.LibraryGame) -> Unit, modifier: Modifier) {
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
            )
        }
        composable("system/{code}") { e ->
            SystemScreen(e.arguments?.getString("code").orEmpty(), onBack = { nav.popBackStack() }, onGame = onGame)
        }
        composable("search") { SearchScreen(onGame, onBack = { nav.popBackStack() }) }
        composable("library") { LibraryScreen(onOpen = { Sfx.tap(Sfx.Cue.SELECT); onOwned(it) }) }
        composable("saved") { SavedScreen(onGame) }
        composable("downloads") { DownloadsScreen(onGame) }
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
private fun TabIcon(t: Tab, activeDownloads: Int) {
    if (t.route == "downloads" && activeDownloads > 0) {
        BadgedBox(badge = { Badge { Text("$activeDownloads") } }) { Icon(t.icon, null) }
    } else if (t.route == "settings" && Updater.available != null) {
        BadgedBox(badge = { Badge() }) { Icon(t.icon, null) }
    } else Icon(t.icon, null)
}

@Composable
private fun Logo() {
    Image(
        painter = painterResource(R.drawable.vimm_logo),
        contentDescription = "Vimm's Lair",
        modifier = Modifier.size(44.dp).shadow(10.dp, CircleShape, ambientColor = VimmRed, spotColor = VimmRed),
    )
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


