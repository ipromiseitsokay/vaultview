package com.vaultview.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Read-only RetroAchievements: your profile and per-game progress, via retroachievements.org's Web API.
 * Unlocking happens in the emulator (RetroArch, DuckStation, PPSSPP, Dolphin… have their own login).
 */
object RetroAchievements {
    data class Profile(val user: String, val points: Int, val softcorePoints: Int, val avatar: String?, val motto: String?)
    data class Achievement(val title: String, val description: String, val points: Int, val badge: String, val earned: Boolean, val hardcore: Boolean)
    data class Progress(
        val gameId: Int, val title: String, val icon: String?, val total: Int, val earned: Int, val earnedHardcore: Int,
        val pointsEarned: Int, val pointsTotal: Int, val achievements: List<Achievement>,
    )

    private const val API = "https://retroachievements.org/API/"
    private const val MEDIA = "https://media.retroachievements.org"

    /** RetroAchievements console ids for the systems VaultView knows. */
    val consoleIds = mapOf(
        "Genesis" to 1, "N64" to 2, "SNES" to 3, "GB" to 4, "GBA" to 5, "GBC" to 6, "NES" to 7, "TG16" to 8,
        "SegaCD" to 9, "32X" to 10, "SMS" to 11, "PS1" to 12, "Lynx" to 13, "GG" to 15, "GameCube" to 16,
        "Jaguar" to 17, "DS" to 18, "Wii" to 19, "WiiU" to 20, "PS2" to 21, "Xbox" to 22, "Atari2600" to 25,
        "VB" to 28, "Saturn" to 39, "Dreamcast" to 40, "PSP" to 41, "CDi" to 42, "Atari5200" to 50,
        "Atari7800" to 51, "3DS" to 62, "TGCD" to 76, "JaguarCD" to 77,
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var cacheDir: File
    var user by mutableStateOf<String?>(null); private set
    private var key: String? = null
    var profile by mutableStateOf<Profile?>(null); private set

    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).addInterceptor { c ->
        c.proceed(c.request().newBuilder().header("User-Agent", "VaultView/1.0 (Android frontend)").build())
    }.build()
    private val progressCache = mutableMapOf<String, Progress?>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("retroachievements", Context.MODE_PRIVATE)
        cacheDir = File(context.cacheDir, "ra").apply { mkdirs() }
        user = prefs.getString("user", null)
        key = prefs.getString("key", null)
    }

    val connected get() = user != null && key != null

    private suspend fun get(endpoint: String, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        val u = user ?: throw IOException("Not connected")
        val k = key ?: throw IOException("Not connected")
        val url = "$API$endpoint".toHttpUrl().newBuilder().apply {
            addQueryParameter("y", k)
            if ("u" !in params) addQueryParameter("u", u)
            params.forEach { (a, b) -> addQueryParameter(a, b) }
        }.build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw IOException("RetroAchievements didn't accept that username and key")
            if (!r.isSuccessful) throw IOException("RetroAchievements said HTTP ${r.code}")
            r.body!!.string()
        }
    }

    /** Checks the username and key, and keeps them if they work. */
    suspend fun connect(username: String, apiKey: String) {
        user = username.trim(); key = apiKey.trim()
        try {
            profile = parseProfile(get("API_GetUserProfile.php", mapOf("u" to user!!)))
            prefs.edit().putString("user", user).putString("key", key).apply()
            progressCache.clear()
        } catch (e: Exception) {
            user = null; key = null; profile = null
            throw e
        }
    }

    fun disconnect() {
        prefs.edit().clear().apply()
        user = null; key = null; profile = null; progressCache.clear()
    }

    suspend fun refreshProfile() { if (connected) runCatching { profile = parseProfile(get("API_GetUserProfile.php", mapOf("u" to user!!))) } }

    fun parseProfile(json: String): Profile {
        val o = JSONObject(json)
        if (!o.has("User")) throw IOException("RetroAchievements didn't recognise that user")
        return Profile(
            o.getString("User"), o.optInt("TotalPoints"), o.optInt("TotalSoftcorePoints"),
            o.optString("UserPic").ifBlank { null }?.let { MEDIA + it }, o.optString("Motto").ifBlank { null },
        )
    }

    /** Titles compared without punctuation, articles or tags: "Legend of Zelda, The - A Link to the Past" == "The Legend of Zelda: A Link to the Past". */
    fun normalize(title: String): String = title.lowercase()
        .replace(Regex("""\s*[\(\[][^)\]]*[\)\]]"""), "")
        .replace("&", " and ")
        .replace(Regex("""[^a-z0-9 ]"""), " ")
        .split(' ').filter { it.isNotBlank() && it != "the" && it != "a" }
        .joinToString(" ")

    /** Every game with achievements on a console, cached on disk for a week. */
    private suspend fun gameList(consoleId: Int): JSONArray {
        val f = File(cacheDir, "games_$consoleId.json")
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 7L * 24 * 3600 * 1000) {
            return JSONArray(f.readText())
        }
        val body = get("API_GetGameList.php", mapOf("i" to consoleId.toString(), "f" to "1"))
        f.writeText(body)
        return JSONArray(body)
    }

    fun findGameId(list: JSONArray, title: String): Int? {
        val want = normalize(title)
        if (want.isEmpty()) return null
        for (i in 0 until list.length()) {
            val g = list.getJSONObject(i)
            val t = g.optString("Title")
            if (t.startsWith("~")) continue // hacks, homebrew, test kits
            if (normalize(t) == want) return g.optInt("ID")
        }
        return null
    }

    fun parseProgress(json: String): Progress {
        val o = JSONObject(json)
        val list = mutableListOf<Achievement>()
        o.optJSONObject("Achievements")?.let { a ->
            a.keys().forEach { k ->
                val x = a.getJSONObject(k)
                val hard = x.optString("DateEarnedHardcore").isNotBlank()
                val earned = hard || x.optString("DateEarned").isNotBlank()
                list += Achievement(
                    x.optString("Title"), x.optString("Description"), x.optInt("Points"),
                    "$MEDIA/Badge/${x.optString("BadgeName")}${if (earned) "" else "_lock"}.png", earned, hard,
                )
            }
        }
        list.sortWith(compareByDescending<Achievement> { it.earned }.thenByDescending { it.points })
        return Progress(
            gameId = o.optInt("ID"), title = o.optString("Title"),
            icon = o.optString("ImageIcon").ifBlank { null }?.let { MEDIA + it },
            total = o.optInt("NumAchievements", list.size),
            earned = o.optInt("NumAwardedToUser", list.count { it.earned }),
            earnedHardcore = o.optInt("NumAwardedToUserHardcore", list.count { it.hardcore }),
            pointsEarned = list.filter { it.earned }.sumOf { it.points }, pointsTotal = list.sumOf { it.points },
            achievements = list,
        )
    }

    /** Your progress in a library game, or null if RetroAchievements has no set for it (or it couldn't be matched). */
    suspend fun progressFor(g: LibraryGame): Progress? {
        if (!connected) return null
        val k = Playtime.key(g)
        if (progressCache.containsKey(k)) return progressCache[k]
        val console = consoleIds[g.system?.code] ?: return null.also { progressCache[k] = null }
        val id = findGameId(gameList(console), g.title)
        val p = id?.let { parseProgress(get("API_GetGameInfoAndUserProgress.php", mapOf("g" to it.toString()))) }
        progressCache[k] = p
        return p
    }
}
