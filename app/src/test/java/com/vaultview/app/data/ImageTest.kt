package com.vaultview.app.data

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hits dl.vimm.net live: missing art must become a 404, real art must pass through. */
class ImageTest {
    private val client = Vimm.buildClient(null)
    private fun code(url: String, accept: String? = null) = client.newCall(
        Request.Builder().url(url).apply { if (accept != null) header("Accept", accept) }.build()
    ).execute().use { it.code }

    @Test fun missingArtIs404() {
        for (accept in listOf(null, "image/*")) {
            assertEquals(404, code(Vimm.boxArt(40457), accept))
            assertEquals(404, code(Vimm.boxArt(39288), accept))
            assertEquals(404, code(Vimm.screenshot(39288), accept))
        }
    }

    @Test fun realArtPasses() {
        for (accept in listOf(null, "image/*")) {
            assertEquals(200, code(Vimm.boxArt(1551), accept))
            assertEquals(200, code(Vimm.screenshot(40457), accept))
        }
    }
}
