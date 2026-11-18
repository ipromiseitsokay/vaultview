package com.vaultview.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortsParseTest {
    private val sample = """
    [{"slug":"supermario64-ghostship","name":"Super Mario 64","projectName":"Ghostship",
      "developer":{"name":"HarbourMasters"},"consoles":["n64"],"lastReleaseVersion":"3.0.0","reviewCount":12,
      "games":[{"title":"Super Mario 64","libraryArt":{"capsule":"https://x/c.png","hero":"https://x/h.jpg"}}],
      "launcher":{"setupFiles":[{"id":"rom","label":"Super Mario 64 ROM","accepted":[]},
                                {"id":"opt","label":"Optional pack","required":false,"accepted":[]}]}},
     {"slug":"diablo-devilutionx","name":"Diablo","consoles":["pc"],"games":[{"title":"Diablo"}],
      "android":{"url":"https://x/d.zip","filename":"devilutionx-android.zip","sha256":"ab","apk":"devilutionx.apk"}}]
    """

    @Test fun parsesPorts() {
        val ports = Ports.parse(JSONArray(sample))
        assertEquals(2, ports.size)
        val sm = ports[0]
        assertEquals("Ghostship", sm.project)
        assertEquals("HarbourMasters", sm.developer)
        assertEquals("https://x/c.png", sm.capsule)
        assertEquals(listOf("Super Mario 64 ROM"), sm.needs) // optional files are left out
        assertEquals("N64", sm.vimmSystem)
        assertNull(ports[1].vimmSystem) // PC games aren't on the Vault
        assertNull(sm.android)
        assertEquals("devilutionx.apk", ports[1].android?.apk)
        assertEquals("ghostship", Ports.parseListSlug("https://quiverlauncher.com/lists/ghostship/"))
        assertEquals("my-list", Ports.parseListSlug("my-list"))
        assertNull(Ports.parseListSlug("https://example.com/nope"))
    }
}
