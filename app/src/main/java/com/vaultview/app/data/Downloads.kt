package com.vaultview.app.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/** What the game page asked the site's download server for. */
data class DownloadRequest(
    val url: String,
    val method: String = "GET",
    val form: List<Pair<String, String>> = emptyList(),
    val referer: String,
    val userAgent: String,
)

enum class DownloadState { QUEUED, DOWNLOADING, EXTRACTING, DONE, FAILED, CANCELLED }

class DownloadItem(
    val key: Long,
    val gameId: Int,
    val title: String,
    val systemCode: String?,
    val request: DownloadRequest?,
) {
    var state by mutableStateOf(DownloadState.QUEUED)
    var progress by mutableFloatStateOf(0f)
    var bytes by mutableLongStateOf(0L)
    var total by mutableLongStateOf(-1L)
    var message by mutableStateOf<String?>(null)
    var fileName by mutableStateOf<String?>(null)
    var savedTo by mutableStateOf<String?>(null)
    var files by mutableStateOf<List<String>>(emptyList())
    var finishedAt by mutableLongStateOf(0L)
    @Volatile var cancelRequested = false

    val system get() = Systems.find(systemCode)
    val active get() = state == DownloadState.QUEUED || state == DownloadState.DOWNLOADING || state == DownloadState.EXTRACTING
}

/** The download queue. The work itself runs in [DownloadService]. */
object Downloads {
    val items = mutableStateListOf<DownloadItem>()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("done", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            items += DownloadItem(o.getLong("key"), o.getInt("gameId"), o.getString("title"),
                o.optString("system").ifBlank { null }, null).apply {
                state = DownloadState.DONE; progress = 1f
                fileName = o.optString("file").ifBlank { null }
                savedTo = o.optString("savedTo").ifBlank { null }
                files = o.optJSONArray("files")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
                finishedAt = o.optLong("at")
            }
        }
    }

    fun enqueue(context: Context, gameId: Int, title: String, systemCode: String?, request: DownloadRequest) {
        items.add(0, DownloadItem(System.currentTimeMillis(), gameId, title, systemCode, request))
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }

    fun nextQueued(): DownloadItem? = items.lastOrNull { it.state == DownloadState.QUEUED }

    fun cancel(item: DownloadItem) {
        item.cancelRequested = true
        if (item.state == DownloadState.QUEUED) item.state = DownloadState.CANCELLED
    }

    fun remove(item: DownloadItem) {
        if (item.active) cancel(item)
        items.remove(item)
        persist()
    }

    fun clearFinished() {
        items.removeAll { !it.active }
        persist()
    }

    /** Finished downloads, newest first — the "library" shown on the home screen. */
    fun library() = items.filter { it.state == DownloadState.DONE }.sortedByDescending { it.finishedAt }

    fun persist() {
        val arr = JSONArray()
        items.filter { it.state == DownloadState.DONE }.take(200).forEach { d ->
            arr.put(JSONObject().apply {
                put("key", d.key); put("gameId", d.gameId); put("title", d.title)
                put("system", d.systemCode ?: ""); put("file", d.fileName ?: "")
                put("savedTo", d.savedTo ?: ""); put("at", d.finishedAt)
                put("files", JSONArray(d.files))
            })
        }
        prefs.edit().putString("done", arr.toString()).apply()
    }
}
