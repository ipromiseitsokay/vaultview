package com.vaultview.app.ui

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaultview.app.MainActivity
import com.vaultview.app.Sfx
import com.vaultview.app.data.DownloadState
import com.vaultview.app.data.Downloads
import com.vaultview.app.data.Favorites
import com.vaultview.app.data.Game
import com.vaultview.app.data.Settings

/*
 * Second-screen deck for dual-screen handhelds such as the AYN Thor. The big top screen keeps the
 * box art and browsing; the small bottom touchscreen gets the buttons you'd otherwise hunt for:
 * Download for whatever game is open, letter jumps for the system you're in, and the download queue.
 *
 * It's a second Activity on the other display (Android 13 won't put a Presentation on a built-in
 * display), and its window never takes focus, so the controller always drives the top screen.
 */

/** A game the top screen is showing, and what its main button does. */
data class DeckGame(
    val game: Game,
    val label: String,          // "Get game", "Download", "Getting download info…"
    val detail: String? = null, // e.g. "680 MB"
    val action: (() -> Unit)?,  // null while it can't be pressed yet
)

/** Letter jumps for the system screen. */
data class DeckLetters(val sections: List<String>, val current: String, val pick: (String) -> Unit)

/** What the screens publish for the deck. Everything runs on the main thread. */
object Deck {
    var route by mutableStateOf<String?>(null)
    var ports by mutableStateOf(false)
    /** The game sheet's game (set while the sheet is open). */
    var sheet by mutableStateOf<DeckGame?>(null)
    /** The game page's game (set while the page is open). */
    var page by mutableStateOf<DeckGame?>(null)
    var letters by mutableStateOf<DeckLetters?>(null)

    var onBack: () -> Unit = {}
    var onSearch: () -> Unit = {}
    var onSurprise: () -> Unit = {}
    var onSwitch: () -> Unit = {}
    var onTab: (String) -> Unit = {}

    /** True while the deck is up on a second screen. */
    var showing by mutableStateOf(false)
        internal set
}

/** Finds the second screen and opens or closes the deck on it. */
object DeckController {
    private var deck: DeckActivity? = null

    /** The display the app itself is on (a launcher like iiSU may not use display 0 for it). */
    @Suppress("DEPRECATION")
    fun homeDisplayId(context: Context): Int = runCatching {
        if (context is Activity) {
            if (android.os.Build.VERSION.SDK_INT >= 30) context.display?.displayId ?: Display.DEFAULT_DISPLAY
            else context.windowManager.defaultDisplay.displayId
        } else Display.DEFAULT_DISPLAY
    }.getOrDefault(Display.DEFAULT_DISPLAY)

    /** A public, switched-on display other than the app's own: the Thor's bottom screen. */
    fun secondDisplay(context: Context): Display? = runCatching {
        val home = homeDisplayId(context)
        val dm = context.getSystemService(DisplayManager::class.java) ?: return@runCatching null
        dm.displays.firstOrNull {
            it.displayId != home && it.state == Display.STATE_ON && (it.flags and Display.FLAG_PRIVATE) == 0
        }
    }.getOrNull()

    fun show(activity: Activity) {
        if (!Settings.secondScreen || deck != null) return
        val display = secondDisplay(activity) ?: return
        runCatching {
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
            activity.startActivity(
                Intent(activity, DeckActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                opts.toBundle(),
            )
            // Opening an activity on the bottom screen can make that display the focused one. If the top
            // screen lost focus, bring its task back so the controller keeps steering the main app.
            val home = homeDisplayId(activity)
            activity.window.decorView.postDelayed({ refocus(activity, home) }, 400)
        }
    }

    private fun refocus(activity: Activity, home: Int) {
        if (activity.isFinishing || activity.isDestroyed || activity.hasWindowFocus()) return
        runCatching {
            // Bring the existing top-screen activity forward (not a new copy); starting it moves focus.
            activity.startActivity(
                Intent(activity, activity.javaClass)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                ActivityOptions.makeBasic().setLaunchDisplayId(home).toBundle(),
            )
        }
    }

    fun hide() { runCatching { deck?.finish() }; deck = null }

    internal fun attached(a: DeckActivity) { deck = a; Deck.showing = true; com.vaultview.app.data.CrashLog.deckShowing = true }
    internal fun detached(a: DeckActivity) {
        if (deck === a) { deck = null; Deck.showing = false; com.vaultview.app.data.CrashLog.deckShowing = false }
    }
}

class DeckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never take focus from the top screen: the controller keeps driving the main app.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.decorView.isSoundEffectsEnabled = false
        DeckController.attached(this)
        setContent { VaultTheme { Surface(color = Bg, contentColor = Color.White) { DeckScreen() } } }
        applyFullscreen()
    }

    override fun onResume() { super.onResume(); applyFullscreen() }

    override fun onDestroy() { DeckController.detached(this); super.onDestroy() }

    // If a button press does land here, hand it to the top screen.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        MainActivity.current?.dispatchKeyEvent(event) ?: super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean =
        MainActivity.current?.dispatchGenericMotionEvent(ev) ?: super.dispatchGenericMotionEvent(ev)
}

// ---- The deck's screen ------------------------------------------------------------------------

@Composable
private fun DeckScreen() {
    val game = Deck.page ?: Deck.sheet
    val letters = Deck.letters
    val accent = if (Deck.ports) Color(0xFF0B1A5C) else VimmMaroon
    Column(
        Modifier.fillMaxSize().background(Bg).safeDrawingPadding().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Mode strip.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(listOf(accent, Panel))).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (Deck.ports) "PORTS" else "THE VAULT", color = VimmTitle, fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            DeckChip("Switch", SwapIcon) { Deck.onSwitch() }
        }

        // The part that changes with what's on the top screen.
        AnimatedContent(
            targetState = when { game != null -> "game"; letters != null -> "letters"; else -> "home" },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "deck",
        ) { mode ->
            when (mode) {
                "game" -> (Deck.page ?: Deck.sheet)?.let { DeckGamePanel(it) }
                "letters" -> Deck.letters?.let { DeckLettersPanel(it) }
                else -> DeckHomePanel()
            }
        }

        DeckDownloads()

        // Touch navigation, always at the bottom edge where thumbs are.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckNav("Back", Icons.Filled.ArrowBack, Modifier.weight(1f)) { Deck.onBack() }
            DeckNav(if (Deck.ports) "Ports" else "Browse", if (Deck.ports) PortsIcon else BrowseIcon, Modifier.weight(1f)) {
                Deck.onTab(if (Deck.ports) "ports" else "home")
            }
            DeckNav("Library", LibraryIcon, Modifier.weight(1f)) { Deck.onTab("library") }
            DeckNav("Downloads", DownloadIcon, Modifier.weight(1f)) { Deck.onTab("downloads") }
        }
    }
}

@Composable
private fun DeckGamePanel(g: DeckGame) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxSize().clip(shape).background(Panel).padding(14.dp)) {
        Row(Modifier.weight(1f)) {
            CoverArt(g.game.id, g.game.title, Modifier.fillMaxHeight().aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)),
                g.game.system, g.game.regions)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(g.game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                g.game.system?.let { Text(it, color = Accent, style = MaterialTheme.typography.labelLarge) }
                g.game.rating?.let { Text("★ $it", color = Muted, style = MaterialTheme.typography.labelMedium) }
                Spacer(Modifier.weight(1f))
                val saved = Favorites.contains(g.game.id)
                DeckChip(if (saved) "Saved" else "Save", if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder) {
                    Favorites.toggle(g.game)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // The big one: easy to hit with a thumb.
        Button(
            onClick = { Sfx.tap(Sfx.Cue.SELECT); g.action?.invoke() },
            enabled = g.action != null,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(76.dp),
        ) {
            if (g.action == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(DownloadIcon, null, Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(g.label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                g.detail?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun DeckLettersPanel(l: DeckLetters) {
    Column(Modifier.fillMaxSize()) {
        Text("JUMP TO", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(GridCells.Fixed(6), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(l.sections) { s ->
                val on = s == l.current
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier.height(48.dp).clip(shape).background(if (on) PanelHi else Panel)
                        .border(1.dp, if (on) VimmTitle.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f), shape)
                        .clickable { Sfx.tap(Sfx.Cue.MOVE); l.pick(s) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (s == "ALL") "All" else s, fontWeight = FontWeight.Bold, color = if (on) Color.White else Muted)
                }
            }
        }
    }
}

@Composable
private fun DeckHomePanel() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PICK SOMETHING UP TOP", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Open a game and its download button appears here.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!Deck.ports) DeckTile("Search", SearchLineIcon, Modifier.weight(1f)) { Deck.onSearch() }
            DeckTile("Surprise me", DiceIcon, Modifier.weight(1f)) { Deck.onSurprise() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeckTile("Saved", Icons.Filled.Favorite, Modifier.weight(1f)) { Deck.onTab("saved") }
            DeckTile("Settings", Icons.Filled.Settings, Modifier.weight(1f)) { Deck.onTab("settings") }
        }
    }
}

@Composable
private fun DeckDownloads() {
    val active = Downloads.items.filter { it.active }
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (active.isEmpty()) "NO DOWNLOADS RUNNING" else "DOWNLOADING · ${active.size}", color = Muted,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        active.take(3).forEach { d ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (d.state == DownloadState.EXTRACTING) "Unpacking" else "${(d.progress * 100).toInt()}%",
                        color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator({ d.progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Accent, trackColor = PanelHi)
            }
        }
    }
}

@Composable
private fun DeckChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.clip(shape).background(PanelHi).border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .clickable { Sfx.tap(Sfx.Cue.SELECT); onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = VimmTitle)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeckTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.height(96.dp).clip(shape).background(PanelHi).border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .clickable { Sfx.tap(Sfx.Cue.SELECT); onClick() }.padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = VimmTitle)
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeckNav(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.height(64.dp).clip(shape).background(Panel).clickable { Sfx.tap(Sfx.Cue.SELECT); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = VimmSilver)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VimmSilver, maxLines = 1)
    }
}
