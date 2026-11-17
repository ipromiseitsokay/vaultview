package com.vaultview.app.data

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ParseTest {
    private fun doc(name: String, url: String) =
        Jsoup.parse(javaClass.classLoader!!.getResource(name)!!.readText(), url)

    @Test fun topTen() {
        val games = Vimm.parseTop(doc("system.html", "https://vimm.net/vault/SNES"), "Super Nintendo")
        assertEquals(10, games.size)
        assertEquals(Game(1652, "Super Mario World", "Super Nintendo"), games.first())
    }

    @Test fun letterList() {
        val games = Vimm.parseRows(doc("letter.html", "https://vimm.net/vault/SNES/S"), "Super Nintendo")
        assertTrue(games.size > 100)
        val mana = games.first { it.id == 1551 }
        assertEquals("Secret of Mana", mana.title)
        assertEquals(listOf("USA"), mana.regions)
        assertEquals("1.0", mana.version)
        assertEquals("8.5", mana.rating)
        assertEquals("Cartridge", mana.media)
        assertEquals("Super Nintendo", mana.system)
        println("letter S: ${games.size} games; sample ${games.take(3)}")
    }

    @Test fun searchResultsCarrySystem() {
        val games = Vimm.parseRows(doc("search.html", "https://vimm.net/vault/?p=list&q=zelda"), null)
        assertTrue(games.isNotEmpty())
        val first = games.first()
        assertEquals("GameCube", first.system)
        assertEquals(listOf("Unlicensed"), first.tags)
        println("search: ${games.size} results; systems ${games.mapNotNull { it.system }.distinct()}")
    }
}
