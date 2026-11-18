package com.vaultview.app.ui

import com.vaultview.app.data.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class RankTest {
    private fun g(id: Int, t: String, r: String?) = Game(id, t, rating = r)

    @Test fun realGamesBeatLowVoteOddities() {
        val list = listOf(
            g(1, "DSP1 (Enhancement Chip)", "10.0"),
            g(2, "Donkey Kong Country 2: Diddy's Kong Quest (Virtual Console)", "10.0"),
            g(3, "Donkey Kong Country", "8.7"),
            g(4, "Super Metroid", "9.3"),
            g(5, "Dragon Ball Z: Final Bout (Pirate)", "10.0"),
            g(6, "Unrated Thing", null),
            g(7, "Chrono Trigger", "9.4"),
        )
        val order = rankTopRated(list, topIds = listOf(3)).map { it.id }
        assertEquals(listOf(3, 7, 4), order.take(3))            // Top 10 first, then by rating
        assertEquals(6, order.last())                            // unrated at the very end
        assertEquals(setOf(1, 2, 5), order.subList(3, 6).toSet()) // side releases after real games
    }
}
