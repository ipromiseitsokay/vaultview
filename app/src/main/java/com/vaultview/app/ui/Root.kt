package com.vaultview.app.ui

import android.net.Uri
import androidx.compose.foundation.background
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
    Tab("search", "Search", Icons.Filled.Search),
    Tab("library", "Saved", Icons.Filled.Favorite),
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
    val onGame: (Game) -> Unit = { selected = it }
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp > config.screenHeightDp

    LaunchedEffect(Unit) { Updater.check(quiet = true) }

    val activeDownloads = Downloads.items.count { it.active }
    fun isActive(t: Tab) = route?.substringBefore('/')?.substringBefore('?') == t.route ||
        (t.route == "home" && (route?.startsWith("system/") == true || route?.startsWith("game/") == true))
    fun go(t: Tab) = nav.navigate(t.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    CompositionLocalProvider(LocalWide provides wide) {
        if (wide) {
            Surface(color = Bg, contentColor = MaterialTheme.colorScheme.onBackground) {
            Row(Modifier.fillMaxSize().systemBarsPadding()) {
                NavigationRail(containerColor = Panel, modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(8.dp))
                    Logo()
                    Spacer(Modifier.weight(1f))
                    tabs.forEach { t ->
                        NavigationRailItem(
                            selected = isActive(t), onClick = { go(t) },
                            icon = { TabIcon(t, activeDownloads) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f)) {
                    UpdateBanner()
                    Host(nav, onGame, Modifier.weight(1f))
                }
            }
            }
        } else {
            Scaffold(
                containerColor = Bg,
                bottomBar = {
                    NavigationBar(containerColor = Panel) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = isActive(t), onClick = { go(t) },
                                icon = { TabIcon(t, activeDownloads) },
                                label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                },
            ) { pad ->
                Column(Modifier.padding(pad)) {
                    UpdateBanner()
                    Host(nav, onGame, Modifier.weight(1f))
                }
            }
        }

        selected?.let { g ->
            GameSheet(g, onDismiss = { selected = null }, onGet = { selected = null; nav.navigate(gameRoute(g)) })
        }
    }
}

@Composable
private fun Host(nav: NavHostController, onGame: (Game) -> Unit, modifier: Modifier) {
    NavHost(nav, startDestination = "home", modifier = modifier) {
        composable("home") { HomeScreen(onSystem = { nav.navigate("system/$it") }, onGame = onGame) }
        composable("system/{code}") { e ->
            SystemScreen(e.arguments?.getString("code").orEmpty(), onBack = { nav.popBackStack() }, onGame = onGame)
        }
        composable("search") { SearchScreen(onGame) }
        composable("library") { LibraryScreen(onGame) }
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
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Accent),
        contentAlignment = Alignment.Center,
    ) { Text("V", fontWeight = FontWeight.Black, color = Bg, style = MaterialTheme.typography.titleLarge) }
}

@Composable
private fun UpdateBanner() {
    val rel = Updater.available ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Surface(color = Color2, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Update ready: ${rel.name}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (Updater.busy) Text("${(Updater.progress * 100).toInt()}%", color = Muted)
            else Button(onClick = { scope.launch { Updater.install(ctx) } }) { Text("Install") }
        }
    }
}

private val Color2 = androidx.compose.ui.graphics.Color(0xFF26305C)

