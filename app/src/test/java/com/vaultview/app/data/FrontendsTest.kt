package com.vaultview.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrontendsTest {
    @Test fun picksTheFileAFrontendShouldList() {
        assertEquals("Tetris DS (USA).nds", Frontends.pickMain(listOf("Vimm's Lair.txt", "Tetris DS (USA).nds")))
        assertEquals("Crash (USA).cue", Frontends.pickMain(listOf("Crash (USA).bin", "Crash (USA).cue")))
        assertEquals("FF7/FF7.m3u", Frontends.pickMain(listOf("FF7/FF7 (Disc 1).chd", "FF7/FF7 (Disc 2).chd", "FF7/FF7.m3u")))
        assertNull(Frontends.pickMain(listOf("readme.txt", "cover.jpg")))
    }
}
