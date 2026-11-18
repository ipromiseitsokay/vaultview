package com.vaultview.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmulatorsTest {
    @Test fun everyEmulatorPackageIsVisibleToTheApp() {
        // Android 11+ hides other apps unless the manifest lists them; a missing entry means "not installed".
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map { File(it) }.first { it.exists() }.readText()
        val missing = Emulators.packages.filter { "android:name=\"$it\"" !in manifest }
        assertTrue("Add to <queries>: $missing", missing.isEmpty())
    }

    @Test fun everySystemWithACoreHasADefault() {
        Emulators.cores.forEach { (system, list) -> assertTrue("$system has no cores", list.isNotEmpty()) }
        assertTrue(Emulators.forSystem("PS1").any { it.id == "duckstation" })
        assertTrue(Emulators.forSystem("SNES").any { it.id == "retroarch" })
    }
}

class RetroAchievementsTest {
    @Test fun titlesMatchAcrossNamingStyles() {
        val n = RetroAchievements::normalize
        assertEquals(n("The Legend of Zelda: A Link to the Past"), n("Legend of Zelda, The - A Link to the Past"))
        assertEquals(n("Pokemon - Red Version"), n("Pokemon: Red Version"))
        assertEquals(n("Super Mario World"), n("Super Mario World (USA) [!]"))
        assertEquals(n("Banjo & Kazooie"), n("Banjo and Kazooie"))
    }

    @Test fun findsGameInList() {
        val list = org.json.JSONArray("""[
            {"Title":"~Hack~ Super Mario World: Kaizo","ID":9},
            {"Title":"Super Mario World","ID":228},
            {"Title":"Legend of Zelda, The: A Link to the Past","ID":355}
        ]""")
        assertEquals(228, RetroAchievements.findGameId(list, "Super Mario World"))
        assertEquals(355, RetroAchievements.findGameId(list, "Legend of Zelda, The - A Link to the Past"))
        assertNull(RetroAchievements.findGameId(list, "Chrono Trigger"))
    }

    @Test fun parsesProgress() {
        val p = RetroAchievements.parseProgress("""{
            "ID":228,"Title":"Super Mario World","ImageIcon":"/Images/066593.png","NumAchievements":3,
            "NumAwardedToUser":2,"NumAwardedToUserHardcore":1,
            "Achievements":{
              "1":{"Title":"Yoshi","Description":"Find Yoshi","Points":5,"BadgeName":"100","DateEarned":"2024-01-01 10:00:00","DateEarnedHardcore":"2024-01-01 10:00:00"},
              "2":{"Title":"Cape","Description":"Get the cape","Points":10,"BadgeName":"101","DateEarned":"2024-01-02 10:00:00"},
              "3":{"Title":"Star Road","Description":"Finish Star Road","Points":25,"BadgeName":"102"}
            }}""")
        assertEquals(3, p.total); assertEquals(2, p.earned); assertEquals(1, p.earnedHardcore)
        assertEquals(15, p.pointsEarned); assertEquals(40, p.pointsTotal)
        assertTrue(p.achievements.last().badge.endsWith("102_lock.png"))
        assertEquals("https://media.retroachievements.org/Images/066593.png", p.icon)
    }
}
