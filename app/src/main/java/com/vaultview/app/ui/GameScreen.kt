package com.vaultview.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.vaultview.app.data.DownloadItem
import com.vaultview.app.data.DownloadRequest
import com.vaultview.app.data.DownloadState
import com.vaultview.app.data.Downloads
import com.vaultview.app.data.Settings
import com.vaultview.app.data.Systems
import com.vaultview.app.data.Vimm
import org.json.JSONObject

/** What the app reads off a vimm.net game page. */
data class PageInfo(
    val action: String,
    val method: String,
    val fields: List<Pair<String, String>>,
    val versions: List<Pair<String, String>>, // mediaId to label
    val size: String?,
    val facts: List<Pair<String, String>>,
)

/** Reads the download form and the facts table. Returns {state: "ready"|"check"|"none", ...}. */
private const val READ_JS = """
(function () {
  if (document.querySelector('.cf-turnstile') && !document.getElementById('dl-form')) return JSON.stringify({state: 'check'});
  var f = document.getElementById('dl-form') ||
          [].slice.call(document.forms).filter(function (x) { return /(^|\/\/)dl\d*\./.test(x.getAttribute('action') || ''); })[0];
  if (!f) return JSON.stringify({state: 'none'});
  // The site's own submit handler adjusts the form (it switches it to GET); run it first.
  try { if (typeof window.submitDL === 'function') window.submitDL(f, null); } catch (e) {}
  var fields = [];
  for (var i = 0; i < f.elements.length; i++) {
    var el = f.elements[i];
    if (el.name && !el.disabled && el.type !== 'submit') fields.push([el.name, el.value]);
  }
  var versions = [];
  var sel = document.getElementById('dl-version');
  if (sel) for (var j = 0; j < sel.options.length; j++) versions.push([sel.options[j].value, sel.options[j].text]);
  var size = null, cell = f.closest('tr');
  if (cell) { var m = cell.innerText.match(/\d+(?:\.\d+)?\s*(?:KB|MB|GB)/i); if (m) size = m[0]; }
  var want = ['Players','Year','Publisher','Developer','Serial #','Cart size','Disc size','CRC','Verified','Region'];
  var facts = [];
  document.querySelectorAll('tr').forEach(function (r) {
    if (r.cells.length < 2) return;
    var k = r.cells[0].innerText.trim(), v = '';
    for (var c = r.cells.length - 1; c >= 1 && !v; c--) v = r.cells[c].innerText.trim().split('\n')[0].trim();
    if (want.indexOf(k) >= 0 && v && !facts.some(function (p) { return p[0] === k; })) facts.push([k, v]);
  });
  return JSON.stringify({state: 'ready', action: new URL(f.getAttribute('action'), location.href).href,
    method: (f.method || 'GET').toUpperCase(), fields: fields, versions: versions, size: size, facts: facts});
})()
"""

/** If the person uses the site's own button while the page is showing, catch that too. */
private const val HOOK_JS = """
(function () {
  if (window.__vaultview) return; window.__vaultview = true;
  function isDl(f) { try { return /(^|\.)dl\d*\./.test(new URL(f.getAttribute('action') || location.href, location.href).hostname); } catch (e) { return false; } }
  function send(f) {
    try { if (typeof window.submitDL === 'function') window.submitDL(f, null); } catch (e) {}
    var fields = [];
    for (var i = 0; i < f.elements.length; i++) { var el = f.elements[i];
      if (el.name && !el.disabled && el.type !== 'submit') fields.push([el.name, el.value]); }
    VaultView.download(new URL(f.getAttribute('action'), location.href).href, (f.method || 'GET').toUpperCase(), JSON.stringify(fields));
  }
  document.addEventListener('submit', function (e) { if (isDl(e.target)) { e.preventDefault(); e.stopImmediatePropagation(); send(e.target); } }, true);
  var orig = HTMLFormElement.prototype.submit;
  HTMLFormElement.prototype.submit = function () { if (isDl(this)) { send(this); return; } return orig.call(this); };
})();
"""

private sealed interface Phase {
    data object Loading : Phase
    data object NeedsCheck : Phase
    data class Ready(val info: PageInfo) : Phase
    data class Problem(val message: String) : Phase
}

private fun parseInfo(json: String): Phase = try {
    val o = JSONObject(json)
    when (o.getString("state")) {
        "check" -> Phase.NeedsCheck
        "ready" -> {
            fun pairs(name: String) = o.getJSONArray(name).let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } } }
            Phase.Ready(PageInfo(o.getString("action"), o.getString("method"), pairs("fields"), pairs("versions"),
                o.optString("size").takeIf { it.isNotBlank() && it != "null" }, pairs("facts")))
        }
        else -> Phase.Problem("This game has no download on vimm.net.")
    }
} catch (e: Exception) { Phase.Problem("Couldn't read the game page.") }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GameScreen(id: Int, title: String, system: String?, onBack: () -> Unit, onShowDownloads: () -> Unit) {
    val context = LocalContext.current
    val wide = LocalWide.current
    val pageUrl = Vimm.gamePage(id)
    val sys = Systems.find(system)
    var phase by remember { mutableStateOf<Phase>(Phase.Loading) }
    var showPage by remember { mutableStateOf(false) }
    var checkWaiting by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<DownloadRequest?>(null) }
    var askFolder by remember { mutableStateOf(false) }
    val ua = WebHost.userAgent
    val active = Downloads.items.count { it.active }
    val mine = Downloads.items.firstOrNull { it.gameId == id }
    val downloadFocus = remember { FocusRequester() }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun start(req: DownloadRequest) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        Downloads.enqueue(context, id, title, sys?.code ?: system, req)
    }
    fun request(req: DownloadRequest) {
        if (Settings.folderUri == null) { pending = req; askFolder = true } else start(req)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { Settings.setFolder(context, uri); pending?.let { start(it) } }
        pending = null
    }
    fun readPage(view: WebView) = view.evaluateJavascript(READ_JS) { raw ->
        val json = runCatching { JSONObject("{\"v\":$raw}").getString("v") }.getOrNull() ?: return@evaluateJavascript
        val p = parseInfo(json)
        when {
            p is Phase.Ready -> {
                phase = p
                showPage = false
                checkWaiting = false
                version = version ?: p.info.fields.firstOrNull { it.first == "mediaId" }?.second
            }
            // Once the button is showing, a background reload must not make the screen jump back.
            phase is Phase.Ready -> Unit
            else -> phase = p
        }
    }
    fun reload() { phase = Phase.Loading; WebHost.reload() }

    BackHandler { if (showPage && phase is Phase.Ready) showPage = false else onBack() }

    // The real page lives in WebHost, behind this screen. Hook this game page up to it.
    val onRequest by rememberUpdatedState<(DownloadRequest) -> Unit>({ request(it) })
    val onRead by rememberUpdatedState<(WebView) -> Unit>({ readPage(it) })
    val listener = remember(pageUrl) {
        object : WebHost.Listener {
            override fun onPageFinished(view: WebView) {
                view.evaluateJavascript(HOOK_JS, null)
                onRead(view)
                view.postDelayed({ if (WebHost.view === view && phase !is Phase.Ready) onRead(view) }, 2500)
                // Never pop the page up by itself (that was the flash); offer a button instead.
                view.postDelayed({ if (phase is Phase.NeedsCheck || phase is Phase.Loading) checkWaiting = true }, 7000)
            }
            override fun onFormDownload(url: String, method: String, fieldsJson: String) {
                val a = org.json.JSONArray(fieldsJson)
                val fields = (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } }
                onRequest(DownloadRequest(url, method, fields, pageUrl, WebHost.userAgent))
            }
            override fun onFileDownload(url: String, userAgent: String?) {
                onRequest(DownloadRequest(url, "GET", emptyList(), pageUrl, userAgent ?: WebHost.userAgent))
            }
            override fun onExternalLink(url: String) = openOnSite(context, url)
        }
    }
    DisposableEffect(listener) {
        WebHost.open(pageUrl, listener)
        onDispose { WebHost.release(listener) }
    }
    SideEffect { WebHost.showing = showPage }

    // Transparent while the real page is showing, so it can be seen behind this screen.
    Box(Modifier.fillMaxSize().background(if (showPage) Color.Transparent else Bg)) {
        AnimatedVisibility(visible = !showPage, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
            Box(Modifier.fillMaxSize().background(Bg)) {
                GameDetails(
                    id, title, sys?.name ?: system, phase, version, wide, downloadFocus, mine, checkWaiting,
                    onVersion = { version = it },
                    onDownload = { info ->
                        val fields = info.fields.map { if (it.first == "mediaId" && version != null) it.first to version!! else it }
                        request(DownloadRequest(info.action, info.method, fields, pageUrl, ua))
                    },
                    onShowPage = { showPage = true },
                    onShowDownloads = onShowDownloads,
                    onRetry = { reload() },
                )
            }
        }

        // Top bar, always on top.
        Row(
            Modifier.fillMaxWidth().height(64.dp).background(Bg).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (showPage && phase is Phase.Ready) showPage = false else onBack() }, modifier = Modifier.focusRing(androidx.compose.foundation.shape.CircleShape)) { Icon(Icons.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showPage) Text(
                    if (phase is Phase.Ready) "vimm.net page" else "Tick the box to prove you're human",
                    color = Muted, style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = { reload() }) { Icon(Icons.Filled.Refresh, "Reload") }
            IconButton(onClick = { openOnSite(context, pageUrl) }) { Icon(Icons.Filled.Share, "Open in browser") }
            BadgedBox(badge = { if (active > 0) Badge { Text("$active") } }) {
                IconButton(onClick = onShowDownloads) { Icon(DownloadIcon, "Downloads") }
            }
        }
    }

    LaunchedEffect(phase is Phase.Ready, checkWaiting) {
        if ((phase is Phase.Ready || checkWaiting) && com.vaultview.app.Controls.usingController)
            runCatching { kotlinx.coroutines.delay(300); downloadFocus.requestFocus() }
    }

    if (askFolder) {
        AlertDialog(
            onDismissRequest = { askFolder = false; pending = null },
            title = { Text("Where should games go?") },
            text = {
                Text("Pick a folder once, for example a “ROMs” folder on your SD card. " +
                    (if (Settings.systemFolders) "Games are sorted into system folders like snes and psx" else "Games are saved there") +
                    (if (Settings.autoExtract) " and unzipped automatically." else "."))
            },
            confirmButton = { Button(onClick = { askFolder = false; pickFolder.launch(null) }) { Text("Choose folder") } },
            dismissButton = { TextButton(onClick = { askFolder = false; pending = null }) { Text("Cancel") } },
        )
    }

}

@Composable
private fun GameDetails(
    id: Int, title: String, system: String?, phase: Phase, version: String?, wide: Boolean, downloadFocus: FocusRequester,
    mine: DownloadItem?, checkWaiting: Boolean,
    onVersion: (String) -> Unit, onDownload: (PageInfo) -> Unit, onShowPage: () -> Unit, onShowDownloads: () -> Unit, onRetry: () -> Unit,
) {
    // Entrance: art slides in, text rises, after the screen transition settles.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = Vimm.boxArt(id), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().blur(40.dp), alpha = 0.28f,
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Bg.copy(alpha = 0.2f), Bg.copy(alpha = 0.85f), Bg))))

        val body: @Composable ColumnScope.() -> Unit = {
            Text(title, style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black)
            system?.let { Text(it, color = Accent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 2.dp)) }
            Spacer(Modifier.height(18.dp))
            AnimatedContent(
                targetState = phase,
                contentKey = { it::class },
                transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 }) togetherWith fadeOut(tween(150)) },
                label = "phase",
            ) { p ->
                Column {
                    when (p) {
                        Phase.Loading, Phase.NeedsCheck -> if (checkWaiting) {
                            Text("vimm.net wants a quick human check before it shows the download.", color = Muted)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onShowPage,
                                modifier = Modifier.height(52.dp).focusRing(RoundedCornerShape(16.dp), 1.03f).focusRequester(downloadFocus),
                                shape = RoundedCornerShape(16.dp),
                            ) { Text("Do the check", fontWeight = FontWeight.Bold) }
                        } else LoadingButton()
                        is Phase.Problem -> {
                            Text(p.message, color = Muted)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = onRetry) { Text("Try again") }
                                OutlinedButton(onClick = onShowPage) { Text("Show vimm.net page") }
                            }
                        }
                        is Phase.Ready -> ReadyBlock(p.info, version, mine, downloadFocus, onVersion, onDownload, onShowPage, onShowDownloads)
                    }
                }
            }
        }

        val artEnter = fadeIn(tween(400)) + slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { -it / 5 }
        val textEnter = fadeIn(tween(400, delayMillis = 80)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) { it / 8 }
        val shotEnter = fadeIn(tween(500, delayMillis = 160)) + slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) { it / 5 }

        if (wide) {
            Row(Modifier.fillMaxSize().padding(top = 64.dp).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                AnimatedVisibility(shown, enter = artEnter) {
                    CoverArt(id, title, Modifier.fillMaxHeight().aspectRatio(0.75f).clip(RoundedCornerShape(18.dp)), system)
                }
                AnimatedVisibility(shown, enter = textEnter, modifier = Modifier.weight(1.25f)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { body() }
                }
                AnimatedVisibility(shown, enter = shotEnter, modifier = Modifier.weight(0.75f)) {
                    AsyncImage(
                        model = Vimm.screenshot(id), contentDescription = "Screenshot", contentScale = ContentScale.Fit,
                        modifier = Modifier.aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(Color.Black),
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = 64.dp).verticalScroll(rememberScrollState()).padding(20.dp)) {
                AnimatedVisibility(shown, enter = artEnter) {
                    Row {
                        CoverArt(id, title, Modifier.width(130.dp).aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)), system)
                        Spacer(Modifier.width(16.dp))
                        AsyncImage(
                            model = Vimm.screenshot(id), contentDescription = "Screenshot", contentScale = ContentScale.Fit,
                            modifier = Modifier.weight(1f).aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp)).background(Color.Black),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                AnimatedVisibility(shown, enter = textEnter) { Column { body() } }
            }
        }
    }
}

/** Placeholder the same size as the Download button, with a moving shine. */
@Composable
private fun LoadingButton() {
    Box(Modifier.width(240.dp).height(60.dp).clip(RoundedCornerShape(16.dp)).shimmer())
    Text("Getting download info from vimm.net…", color = Muted, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ReadyBlock(
    info: PageInfo, version: String?, mine: DownloadItem?, downloadFocus: FocusRequester,
    onVersion: (String) -> Unit, onDownload: (PageInfo) -> Unit, onShowPage: () -> Unit, onShowDownloads: () -> Unit,
) {
    if (info.versions.size > 1) {
        VersionPicker(info.versions, version, onVersion)
        Spacer(Modifier.height(12.dp))
    }
    val shape = RoundedCornerShape(16.dp)
    val busy = mine?.active == true
    val progress by animateFloatAsState(mine?.progress ?: 0f, tween(400), label = "dl")
    Box(Modifier.focusRing(shape, 1.03f).clip(shape)) {
        Button(
            onClick = { if (busy) onShowDownloads() else { com.vaultview.app.Sfx.tap(com.vaultview.app.Sfx.Cue.SELECT); onDownload(info) } },
            modifier = Modifier.widthIn(min = 240.dp).height(60.dp).focusRequester(downloadFocus),
            contentPadding = PaddingValues(horizontal = 28.dp),
            shape = shape,
            colors = if (busy) ButtonDefaults.buttonColors(containerColor = PanelHi, contentColor = Color.White) else ButtonDefaults.buttonColors(),
        ) {
            Icon(DownloadIcon, null)
            Spacer(Modifier.width(12.dp))
            val (label, sub) = when {
                mine?.state == DownloadState.QUEUED -> "Waiting…" to null
                mine?.state == DownloadState.DOWNLOADING -> "Downloading" to "${(mine.progress * 100).toInt()}%"
                mine?.state == DownloadState.EXTRACTING -> "Unzipping" to "${(mine.progress * 100).toInt()}%"
                mine?.state == DownloadState.DONE -> "Downloaded ✓" to "Tap to get it again"
                else -> "Download" to info.size
            }
            Column {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
        if (busy) {
            // Progress fills the button from the left.
            Box(Modifier.matchParentSize()) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Accent.copy(alpha = 0.35f)))
            }
        }
    }
    val note = when (mine?.state) {
        DownloadState.FAILED -> "Last try failed: ${mine.message}"
        DownloadState.DONE -> "Saved to ${mine.savedTo}"
        else -> listOfNotNull(
            Settings.folderLabel()?.let { "Saves to $it" } ?: "You'll pick a download folder first",
            if (Settings.autoExtract) "unzips automatically" else null,
        ).joinToString(" · ")
    }
    Text(note, color = if (mine?.state == DownloadState.FAILED) Color(0xFFFF8A8A) else Muted,
        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
    if (info.facts.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        Surface(color = Panel.copy(alpha = 0.85f), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp).widthIn(min = 260.dp)) {
                info.facts.forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(k, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
                        Text(v, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onShowPage) { Text("Show vimm.net page") }
}

@Composable
private fun VersionPicker(versions: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { open = true }) {
            Text("Version " + (versions.firstOrNull { it.first == selected }?.second ?: versions.first().second))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            versions.forEach { (mediaId, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onPick(mediaId); open = false })
            }
        }
    }
}
