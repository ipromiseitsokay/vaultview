package com.vaultview.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.vaultview.app.data.Installer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaultview.app.data.Emulators
import com.vaultview.app.data.GameSystem

/** Choose which emulator (and RetroArch core) plays a system's games. */
@Composable
fun EmulatorPicker(system: GameSystem, onDismiss: () -> Unit, onPicked: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val v = Emulators.version // re-check what's installed after returning from an install
    val current = Emulators.effectiveChoice(context, system.code)
    val options = remember(system.code, v) {
        Emulators.forSystem(system.code).map { it to (Emulators.installedPackage(context, it) != null) }
            .sortedWith(compareBy({ !it.second }, { it.first.id != current?.emulatorId }, { Emulators.rank(system.code, it.first.id) }))
    }
    fun pick(c: Emulators.Choice) { Emulators.setChoice(system.code, c); onPicked(); onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${system.name} emulator", maxLines = 1) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).widthIn(min = 360.dp)) {
                options.forEachIndexed { i, (emu, installed) ->
                    if (!installed) {
                        InstallRow(emu, first = i == 0) { scope.launch { Installer.install(context, emu.id) } }
                    } else if (emu.id == "retroarch") {
                        val cores = Emulators.cores[system.code].orEmpty()
                        cores.forEachIndexed { ci, (label, core) ->
                            Option(
                                title = "RetroArch · $label", subtitle = if (installed) "Installed" else "Not installed",
                                selected = current?.emulatorId == "retroarch" && current.core == core, enabled = installed,
                                first = i == 0 && ci == 0,
                            ) { pick(Emulators.Choice("retroarch", core)) }
                        }
                    } else {
                        Option(
                            title = emu.name, subtitle = if (installed) "Installed" else "Not installed",
                            selected = current?.emulatorId == emu.id, enabled = installed, first = i == 0,
                        ) { pick(Emulators.Choice(emu.id)) }
                    }
                }
                Option("Other app…", "Let Android ask which app to use", current?.emulatorId == "other", true, options.isEmpty()) {
                    pick(Emulators.Choice("other"))
                }
                if (options.none { it.second }) {
                    Text(
                        "None of the usual ${system.name} emulators are installed yet. Press Install on one above.",
                        color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Option(title: String, subtitle: String, selected: Boolean, enabled: Boolean, first: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .then(if (first) Modifier.initialFocus(150) else Modifier)
            .focusRing(shape, 1.02f).clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) MaterialTheme.colorScheme.onSurface else Muted)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

/** An emulator that isn't installed: what it is, and a button to get it. */
@Composable
private fun InstallRow(emu: Emulators.Emulator, first: Boolean, onInstall: () -> Unit) {
    val source = Emulators.sources[emu.id]
    val p = Installer.progress[emu.id]
    val msg = Installer.messages[emu.id]
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(emu.name, fontWeight = FontWeight.SemiBold, color = Muted)
            Text(
                msg ?: when (source) {
                    is Emulators.Source.PlayStore -> "Not installed · on Google Play"
                    null -> "Not installed"
                    else -> "Not installed · free download"
                },
                style = MaterialTheme.typography.labelSmall, color = Muted,
            )
            if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        Spacer(Modifier.width(10.dp))
        if (source != null) {
            FilledTonalButton(
                onClick = onInstall, enabled = p == null,
                modifier = Modifier.then(if (first) Modifier.initialFocus(150) else Modifier).focusRing(RoundedCornerShape(50), 1.04f),
            ) {
                Text(when {
                    p != null -> "${(p * 100).toInt()}%"
                    source is Emulators.Source.PlayStore -> "Get"
                    else -> "Install"
                })
            }
        }
    }
}
