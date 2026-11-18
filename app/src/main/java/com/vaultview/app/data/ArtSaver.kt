package com.vaultview.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Downloads every box art for a system into the image store, so that system browses offline. */
object ArtSaver {
    /** System code to progress (0..1) while saving. */
    val progress = mutableStateMapOf<String, Float>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isSaved(context: Context, code: String) =
        context.getSharedPreferences("art", Context.MODE_PRIVATE).getBoolean(code, false)

    fun save(context: Context, code: String, games: List<Game>) {
        if (code in progress) return
        progress[code] = 0f
        val app = context.applicationContext
        scope.launch {
            val gate = Semaphore(3) // gentle on the image server
            var done = 0
            games.chunked(60).forEach { chunk ->
                chunk.map { g ->
                    async {
                        gate.withPermit {
                            runCatching {
                                app.imageLoader.execute(
                                    ImageRequest.Builder(app).data(Vimm.boxArt(g.id))
                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .size(1, 1) // only the download matters; skip decoding at full size
                                        .build()
                                )
                            }
                            delay(60)
                        }
                    }
                }.awaitAll()
                done += chunk.size
                kotlinx.coroutines.withContext(Dispatchers.Main) { progress[code] = done.toFloat() / games.size }
            }
            app.getSharedPreferences("art", Context.MODE_PRIVATE).edit().putBoolean(code, true).apply()
            kotlinx.coroutines.withContext(Dispatchers.Main) { progress.remove(code) }
        }
    }
}
