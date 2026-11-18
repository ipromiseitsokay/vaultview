package com.vaultview.app.ui

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import com.vaultview.app.data.Game
import com.vaultview.app.data.Vimm

/** Starts downloading box art for a list right away, so cards are ready by the time you scroll to them. */
fun prefetchArt(context: Context, games: List<Game>, count: Int = 40) {
    val loader = context.imageLoader
    games.take(count).forEach { g ->
        loader.enqueue(ImageRequest.Builder(context).data(Vimm.boxArt(g.id)).size(320, 420).build())
    }
}
