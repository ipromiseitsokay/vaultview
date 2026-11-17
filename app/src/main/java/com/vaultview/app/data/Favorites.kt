package com.vaultview.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** Games the user starred, kept on the phone only. */
object Favorites {
    private lateinit var prefs: SharedPreferences
    val items = mutableStateListOf<Game>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("games", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            items += Game(
                id = o.getInt("id"),
                title = o.getString("title"),
                system = o.optString("system").ifBlank { null },
                regions = o.optJSONArray("regions")?.let { r -> List(r.length()) { r.getString(it) } } ?: emptyList(),
                version = o.optString("version").ifBlank { null },
                rating = o.optString("rating").ifBlank { null },
            )
        }
    }

    fun contains(id: Int) = items.any { it.id == id }

    fun toggle(game: Game) {
        if (contains(game.id)) items.removeAll { it.id == game.id } else items.add(0, game)
        val arr = JSONArray()
        items.forEach { g ->
            arr.put(JSONObject().apply {
                put("id", g.id); put("title", g.title)
                put("system", g.system ?: ""); put("version", g.version ?: ""); put("rating", g.rating ?: "")
                put("regions", JSONArray(g.regions))
            })
        }
        prefs.edit().putString("games", arr.toString()).apply()
    }
}
