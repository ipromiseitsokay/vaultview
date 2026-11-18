package com.vaultview.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject

/** Time spent in each game, measured from launch until you come back to VaultView. */
object Playtime {
    data class Record(val seconds: Long, val lastPlayed: Long, val plays: Int)

    private lateinit var prefs: SharedPreferences
    val records = mutableStateMapOf<String, Record>()

    private const val MIN_SESSION = 15L          // shorter than this was probably a misfire
    private const val MAX_SESSION = 6 * 3600L    // longer than this, the game was probably left running

    fun key(g: LibraryGame) = "${g.system?.code ?: "?"}|${g.baseName.lowercase()}"

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playtime", Context.MODE_PRIVATE)
        val o = JSONObject(prefs.getString("records", "{}") ?: "{}")
        o.keys().forEach { k ->
            val r = o.getJSONObject(k)
            records[k] = Record(r.getLong("s"), r.getLong("l"), r.optInt("p", 1))
        }
    }

    /** Called right before an emulator starts. Stored, so it survives Android closing this app meanwhile. */
    fun start(g: LibraryGame) {
        prefs.edit().putString("session", key(g)).putLong("since", System.currentTimeMillis()).apply()
    }

    /** Called when VaultView comes back to the front. Returns the key that was credited, if any. */
    fun finish(): String? {
        val k = prefs.getString("session", null) ?: return null
        val since = prefs.getLong("since", 0L)
        prefs.edit().remove("session").remove("since").apply()
        val secs = ((System.currentTimeMillis() - since) / 1000).coerceAtMost(MAX_SESSION)
        if (secs < MIN_SESSION) return null
        val old = records[k]
        records[k] = Record((old?.seconds ?: 0) + secs, System.currentTimeMillis(), (old?.plays ?: 0) + 1)
        save()
        return k
    }

    fun of(g: LibraryGame): Record? = records[key(g)]

    private fun save() {
        val o = JSONObject()
        records.forEach { (k, r) -> o.put(k, JSONObject().put("s", r.seconds).put("l", r.lastPlayed).put("p", r.plays)) }
        prefs.edit().putString("records", o.toString()).apply()
    }

    fun format(seconds: Long): String = when {
        seconds < 60 -> "<1m"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
