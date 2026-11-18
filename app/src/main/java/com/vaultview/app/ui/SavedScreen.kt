package com.vaultview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaultview.app.data.Favorites
import com.vaultview.app.data.Game

@Composable
fun SavedScreen(onGame: (Game) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Saved", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp))
        if (Favorites.items.isEmpty()) CenterMessage("Tap ♥ on a game to keep it here")
        else GameGrid(Favorites.items.toList(), onGame, showSystem = true)
    }
}
