package com.vaultview.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hits thumbnails.libretro.com live: vimm-style titles must map to real No-Intro box-art names. */
class LibretroTest {
    private val http = OkHttpClient()
    private fun found(url: String) = http.newCall(Request.Builder().url(url).head().build()).execute().use { it.code == 200 }

    @Test fun vimmTitleMapsToLibretroName() {
        val urls = Vimm.libretroBoxArt("SNES", "Legend of Zelda, The: A Link to the Past", listOf("USA"))
        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/" +
                "Legend%20of%20Zelda%2C%20The%20-%20A%20Link%20to%20the%20Past%20%28USA%29.png",
            urls.first(),
        )
        assertTrue(found(urls.first()))
    }

    @Test fun searchResultSystemCodesWork() {
        // Search results name systems by code ("GBA"), lists by name ("Game Boy Advance").
        assertTrue(Vimm.libretroBoxArt("GBA", "Metroid Fusion", listOf("USA")).any { found(it) })
        assertTrue(Vimm.libretroBoxArt("Game Boy Advance", "Metroid Fusion", listOf("USA", "Australia")).any { found(it) })
    }
}
