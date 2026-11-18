package com.vaultview.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaultview.app.Sfx
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
