package com.vaultview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vaultview.app.data.Game
import com.vaultview.app.data.Systems
import com.vaultview.app.data.Vimm

@Composable
fun SearchScreen(onGame: (Game) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val state by produceState<Load<List<Game>>?>(null, submitted, system, retry) {
        if (submitted.isBlank()) { value = null; return@produceState }
        value = Load.Loading
        value = try { Load.Ok(Vimm.search(submitted, system)) } catch (e: Exception) { Load.Err(e.message ?: "Search failed") }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.focusRing(androidx.compose.foundation.shape.CircleShape)) {
                Icon(Icons.Filled.ArrowBack, "Back")
            }
            Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
        SearchBox(
            query = query, onQueryChange = { query = it }, placeholder = "Game title",
            onSubmit = { submitted = query.trim() }, onClear = { query = ""; submitted = "" },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            autoFocus = true,
        )
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilledTonalButton(onClick = { menu = true }, modifier = Modifier.focusRing(androidx.compose.foundation.shape.RoundedCornerShape(50))) {
                Text(system?.let { Systems.byCode(it)?.name } ?: "All systems")
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("All systems") }, onClick = { system = null; menu = false })
                Systems.all.forEach { s ->
                    DropdownMenuItem(text = { Text("${s.name}  ·  ${s.brand.label}") }, onClick = { system = s.code; menu = false })
                }
            }
        }
        when (val s = state) {
            null -> CenterMessage("Search every system on the Vault")
            Load.Loading -> SkeletonGrid()
            is Load.Err -> CenterMessage(s.message, "Try again") { retry++ }
            is Load.Ok -> if (s.value.isEmpty()) CenterMessage("Nothing found for “$submitted”")
                else GameGrid(s.value, onGame, showSystem = system == null)
        }
    }
}
