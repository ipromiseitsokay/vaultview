package com.vaultview.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vaultview.app.data.Game
import com.vaultview.app.data.Vimm

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

private val regionFlags = mapOf(
    "USA" to "🇺🇸", "Japan" to "🇯🇵", "Europe" to "🇪🇺", "World" to "🌐", "France" to "🇫🇷",
    "Germany" to "🇩🇪", "Spain" to "🇪🇸", "Italy" to "🇮🇹", "UK" to "🇬🇧", "United Kingdom" to "🇬🇧",
    "Australia" to "🇦🇺", "Korea" to "🇰🇷", "Brazil" to "🇧🇷", "China" to "🇨🇳", "Canada" to "🇨🇦",
    "Netherlands" to "🇳🇱", "Sweden" to "🇸🇪", "Asia" to "🌏", "Taiwan" to "🇹🇼", "Hong Kong" to "🇭🇰",
    "Russia" to "🇷🇺", "Scandinavia" to "🇸🇪", "Portugal" to "🇵🇹", "Denmark" to "🇩🇰", "Norway" to "🇳🇴",
    "Finland" to "🇫🇮", "Poland" to "🇵🇱", "Greece" to "🇬🇷", "Belgium" to "🇧🇪", "Mexico" to "🇲🇽",
)

fun flag(region: String) = regionFlags[region] ?: region.take(3).uppercase()

/** Box art drawn over a blurred copy of itself, so landscape and portrait boxes both fill the card.
 *  Falls back to the screenshot, then to a title card, when the site has no image. */
@Composable
fun CoverArt(id: Int, title: String?, modifier: Modifier = Modifier) {
    // 0 = box art, 1 = screenshot, 2 = nothing available
    var stage by remember(id) { mutableIntStateOf(0) }
    var loaded by remember(id) { mutableStateOf(false) }
    Box(modifier.then(if (loaded) Modifier.background(PanelHi) else Modifier.shimmer())) {
        if (stage == 2) {
            Box(
                Modifier.matchParentSize().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2A3360), PanelHi))
                ).padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title ?: "No image", color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 5, overflow = TextOverflow.Ellipsis,
                )
            }
            return@Box
        }
        val model = if (stage == 0) Vimm.boxArt(id) else Vimm.screenshot(id)
        AsyncImage(
            model = model, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().blur(18.dp).alpha(0.55f),
        )
        AsyncImage(
            model = model, contentDescription = title, contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
            onSuccess = { loaded = true },
            onError = { stage++ },
        )
    }
}

@Composable
fun GameCard(game: Game, showSystem: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .focusRing(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        CoverArt(game.id, game.title, Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(6.dp))
        Text(
            game.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp),
        )
        Row(Modifier.padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val sub = buildString {
                if (showSystem && game.system != null) append(game.system).append("  ")
                append(game.regions.take(3).joinToString(" ") { flag(it) })
            }
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            game.rating?.let {
                Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(12.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

@Composable
fun GameGrid(
    games: List<Game>,
    onGame: (Game) -> Unit,
    showSystem: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (LocalWide.current) 120.dp else 108.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (header != null) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        items(games, key = { it.id }) { g ->
            Box(Modifier.animateItem(fadeInSpec = tween(250), placementSpec = spring(stiffness = Spring.StiffnessMediumLow))) {
                GameCard(g, showSystem) { onGame(g) }
            }
        }
    }
}

/** Placeholder grid shown while a list loads. */
@Composable
fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (LocalWide.current) 120.dp else 108.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(18) {
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)).shimmer())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.8f).height(10.dp).clip(RoundedCornerShape(5.dp)).shimmer())
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.45f).height(8.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            }
        }
    }
}

@Composable
fun CenterMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun Spinner() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

/** Opens a vimm.net page in a real browser tab, where verification and downloads work as normal. */
fun openOnSite(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder().setToolbarColor(Bg.toArgb()).build()
            )
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
