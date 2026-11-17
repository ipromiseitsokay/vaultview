package com.vaultview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vaultview.app.data.Game
import com.vaultview.app.data.Systems
import com.vaultview.app.data.Vimm

@Composable
fun SearchScreen(onGame: (Game) -> Unit) {
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
        Text("Search", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Game title") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; submitted = "" }) { Icon(Icons.Filled.Close, "Clear") } },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitted = query.trim(); focus.clearFocus(); keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilledTonalButton(onClick = { menu = true }) {
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
            Load.Loading -> Spinner()
            is Load.Err -> CenterMessage(s.message, "Try again") { retry++ }
            is Load.Ok -> if (s.value.isEmpty()) CenterMessage("Nothing found for “$submitted”")
                else GameGrid(s.value, onGame, showSystem = system == null)
        }
    }
}
