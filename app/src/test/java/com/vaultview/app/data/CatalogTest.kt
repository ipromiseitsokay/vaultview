package com.vaultview.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogTest {
    @Test fun parsesBuilderOutput() {
        // Same shape scripts/build_catalog.py writes.
        val json = """{"built":"2026-11-18T07:00:00Z","systems":{"VB":{"top":[84297],"games":[
            [84280,"3-D Tetris",["USA"],"1.0","9.5","Cartridge",[]],
            [84297,"Galactic Pinball",["USA","Japan"],"1.0","9.2","Cartridge",["Unlicensed"]]]}}}"""
        val vb = Catalog.parse(json).getValue("VB")
        assertEquals(2, vb.games.size)
        assertEquals("Galactic Pinball", vb.top.single().title)
        assertEquals("Virtual Boy", vb.games.first().system)
        assertEquals(listOf("USA", "Japan"), vb.games[1].regions)
        assertEquals("9.5", vb.games[0].rating)
        assertEquals(listOf("Unlicensed"), vb.games[1].tags)
    }
}
