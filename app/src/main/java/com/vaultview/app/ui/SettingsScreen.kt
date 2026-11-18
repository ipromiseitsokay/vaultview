package com.vaultview.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaultview.app.Sfx
import com.vaultview.app.data.Emulators
import com.vaultview.app.data.Library
import com.vaultview.app.data.RetroAchievements
import com.vaultview.app.data.Settings
import com.vaultview.app.data.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) Settings.setFolder(context, uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)

        Section("Downloads") {
            Text("Download folder", fontWeight = FontWeight.SemiBold)
            Text(Settings.folderLabel() ?: "Not chosen yet", color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { pickFolder.launch(null) }, modifier = Modifier.initialFocus().focusRing(RoundedCornerShape(50))) {
                Text(if (Settings.folderUri == null) "Choose folder" else "Change folder")
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Toggle("Unzip automatically", "Unpack .zip and .7z downloads and remove the archive", Settings.autoExtract) {
                Settings.updateAutoExtract(it)
            }
            Spacer(Modifier.height(8.dp))
            Toggle("Sort into system folders", "snes, psx, gba… the folder names ES-DE and RetroArch expect", Settings.systemFolders) {
                Settings.updateSystemFolders(it)
            }
        }

        Section("Second screen") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val found = remember { DeckController.secondDisplay(context) != null }
            Toggle("Use the bottom screen", if (found) "Download button, letter jumps and the download queue go on your second screen"
                else "For dual-screen handhelds like the AYN Thor. No second screen found on this device.", Settings.secondScreen) {
                Settings.updateSecondScreen(it)
                val act = context as? android.app.Activity
                if (it && act != null) DeckController.show(act) else DeckController.hide()
            }
        }
        Section("Emulators") {
            val systems = Library.games.mapNotNull { it.system }.distinct().sortedWith(compareBy({ it.brand.ordinal }, { it.year }))
            var picking by remember { mutableStateOf<com.vaultview.app.data.GameSystem?>(null) }
            val v = Emulators.version
            if (systems.isEmpty()) {
                Text("Once your Library has games, pick which emulator plays each system here.", color = Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
            systems.forEach { sys ->
                val choice = remember(sys.code, v) { Emulators.effectiveChoice(context, sys.code) }
                Row(
                    Modifier.fillMaxWidth().focusRing(RoundedCornerShape(12.dp), 1.01f)
                        .clip(RoundedCornerShape(12.dp)).clickable { picking = sys }.padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(sys.brand.color))
                    Spacer(Modifier.width(12.dp))
                    Text(sys.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(Emulators.label(choice, sys.code), color = if (choice == null) Color(0xFFFF8A8A) else Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            picking?.let { EmulatorPicker(it, onDismiss = { picking = null }) }
        }

        Section("RetroAchievements") { RetroAchievementsSettings() }

        Section("Sounds") {
            Text("Menu sounds", fontWeight = FontWeight.SemiBold)
            Text("Quiet clicks for moving, selecting and going back, plus a chime when a download finishes",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sfx.themes.forEach { (key, label) ->
                    FilterChip(
                        selected = Settings.soundTheme == key,
                        onClick = {
                            Settings.updateSoundTheme(key)
                            Sfx.play(Sfx.Cue.SUCCESS, key) // preview
                        },
                        label = { Text(label, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp), 1.04f),
                    )
                }
            }
            if (Settings.soundTheme != "off") {
                Spacer(Modifier.height(12.dp))
                Text("Volume", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = Settings.soundVolume,
                    onValueChange = { Settings.updateSoundVolume(it) },
                    onValueChangeFinished = { Sfx.play(Sfx.Cue.SELECT) },
                    valueRange = 0.1f..1f,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }
        }

        Section("Updates") {
            Text("VaultView ${Updater.currentVersion}", fontWeight = FontWeight.SemiBold)
            Text(
                if (Updater.repo.isBlank()) "Built without an update source" else "Updates come from github.com/${Updater.repo}",
                color = Muted, style = MaterialTheme.typography.bodySmall,
            )
            Updater.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = !Updater.busy, onClick = { scope.launch { Updater.check() } }) { Text("Check for updates") }
                if (Updater.available != null) {
                    Button(enabled = !Updater.busy, onClick = { scope.launch { Updater.install(context) } }) {
                        Text(if (Updater.busy) "${(Updater.progress * 100).toInt()}%" else "Install ${Updater.available!!.name}")
                    }
                }
            }
        }

        Section("About") {
            Text("An unofficial, nicer-looking way to browse vimm.net. Game pages, the human check and the files all come from vimm.net itself.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RetroAchievementsSettings() {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(RetroAchievements.connected) { if (RetroAchievements.connected) RetroAchievements.refreshProfile() }

    if (RetroAchievements.connected) {
        val p = RetroAchievements.profile
        Row(verticalAlignment = Alignment.CenterVertically) {
            coil.compose.AsyncImage(
                model = p?.avatar, contentDescription = null,
                modifier = Modifier.size(56.dp).clip(CircleShape).background(PanelHi),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(RetroAchievements.user ?: "", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(p?.let { "${it.points} points" + if (it.softcorePoints > 0) " · ${it.softcorePoints} softcore" else "" } ?: "Loading…",
                    color = VimmTitle, style = MaterialTheme.typography.bodySmall)
                p?.motto?.let { Text("“$it”", color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
            OutlinedButton(onClick = { RetroAchievements.disconnect() }, modifier = Modifier.focusRing(RoundedCornerShape(50))) { Text("Disconnect") }
        }
        Text("Your progress shows on each game in your Library. Log in to RetroAchievements inside your emulator too (RetroArch, DuckStation, PPSSPP, Dolphin…) to earn them.",
            color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
    } else {
        Text("See your achievement progress for each game. Your Web API key is on retroachievements.org under Settings → Keys.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        TapField(user, { user = it }, "Username", modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.height(8.dp))
        TapField(key, { key = it }, "Web API key", secret = true, modifier = Modifier.widthIn(max = 420.dp))
        error?.let { Text(it, color = Color(0xFFFF8A8A), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(10.dp))
        Button(
            enabled = !busy && user.isNotBlank() && key.isNotBlank(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    try { RetroAchievements.connect(user, key); key = "" }
                    catch (e: Exception) { error = e.message ?: "Couldn't connect" }
                    finally { busy = false }
                }
            },
            modifier = Modifier.focusRing(RoundedCornerShape(50)),
        ) { Text(if (busy) "Checking…" else "Connect") }
    }
}
