package com.vaultview.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Every game on vimm.net (name, regions, version, rating), built weekly by the repo's
 * "Game catalog" workflow. Bundled in the APK and refreshed from GitHub, so lists and search
 * are instant and work offline.
 */
object Catalog {
    class SystemData(val top: List<Game>, val games: List<Game>)

    private var systems: Map<String, SystemData> = emptyMap()
    private val loaded = CompletableDeferred<Boolean>()
    var built by mutableStateOf<String?>(null); private set
    private lateinit var file: File
    private const val REPO_ASSET = "https://api.github.com/repos/ipromiseitsokay/vaultview/releases/tags/catalog"

    /** Starts loading in the background. */
    fun init(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
        file = File(context.filesDir, "catalog.json.gz")
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val stream = if (file.exists()) file.inputStream() else context.assets.open("catalog.json.gz")
                stream.use { load(it.readBytes()) }
            }.isSuccess
            loaded.complete(ok)
            refresh(context)
        }
    }

    private fun load(gz: ByteArray) {
        val json = GZIPInputStream(gz.inputStream()).readBytes().toString(Charsets.UTF_8)
        systems = parse(json)
        built = JSONObject(json.take(200).substringBefore(",\"systems\"") + "}").optString("built").ifBlank { null }
    }

    /** Parses the catalog JSON (see scripts/build_catalog.py for the format). */
    fun parse(json: String): Map<String, SystemData> {
        val root = JSONObject(json).getJSONObject("systems")
        val out = HashMap<String, SystemData>()
        root.keys().forEach { code ->
            val s = root.getJSONObject(code)
            val name = Systems.byCode(code)?.name ?: code
            val arr = s.getJSONArray("games")
            val games = ArrayList<Game>(arr.length())
            for (i in 0 until arr.length()) {
                val g = arr.getJSONArray(i)
                fun list(a: JSONArray) = List(a.length()) { a.getString(it) }
                games += Game(
                    id = g.getInt(0), title = g.getString(1), system = name, regions = list(g.getJSONArray(2)),
                    version = g.optString(3).ifBlank { null }, rating = g.optString(4).ifBlank { null },
                    media = g.optString(5).ifBlank { null }, tags = list(g.getJSONArray(6)),
                )
            }
            val byId = games.associateBy { it.id }
            val topIds = s.getJSONArray("top")
            val top = (0 until topIds.length()).mapNotNull { byId[topIds.getInt(it)] }
            out[code] = SystemData(top, games)
        }
        return out
    }

    /** The catalog for a system, waiting briefly for the startup load. Null means "ask vimm.net". */
    suspend fun system(code: String): SystemData? {
        withTimeoutOrNull(4000) { loaded.await() }
        return systems[code]
    }

    fun systemNow(code: String): SystemData? = systems[code]

    val available get() = systems.isNotEmpty()

    /** Every word in the query appears in the title (case and punctuation ignored). */
    suspend fun search(query: String, code: String?): List<Game>? {
        withTimeoutOrNull(4000) { loaded.await() }
        if (systems.isEmpty()) return null
        val words = norm(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val pool = if (code != null) systems[code]?.games ?: return null else systems.values.flatMap { it.games }
        return withContext(Dispatchers.Default) {
            pool.filter { g -> norm(g.title).let { t -> words.all { it in t } } }
                .sortedWith(compareBy<Game> { !norm(it.title).startsWith(words.first()) }.thenBy { it.title.lowercase() })
                .take(400)
        }
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Once a day: if the catalog on GitHub is newer than ours, download it. */
    private suspend fun refresh(context: Context) = runCatching {
        val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("checked", 0) < 24 * 3600 * 1000L) return@runCatching
        val http = OkHttpClient()
        val rel = http.newCall(Request.Builder().url(REPO_ASSET).header("Accept", "application/vnd.github+json").build())
            .execute().use { if (it.isSuccessful) JSONObject(it.body!!.string()) else null } ?: return@runCatching
        val assets = rel.getJSONArray("assets")
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name") == "catalog.json.gz" }
            ?: return@runCatching
        val stamp = asset.getString("updated_at")
        if (stamp != prefs.getString("stamp", null) || !file.exists()) {
            val bytes = http.newCall(Request.Builder().url(asset.getString("browser_download_url")).build())
                .execute().use { if (it.isSuccessful) it.body!!.bytes() else null } ?: return@runCatching
            load(bytes)                       // only keep it if it parses
            File(file.path + ".tmp").apply { writeBytes(bytes); renameTo(file) }
            prefs.edit().putString("stamp", stamp).apply()
        }
        prefs.edit().putLong("checked", System.currentTimeMillis()).apply()
    }
}
