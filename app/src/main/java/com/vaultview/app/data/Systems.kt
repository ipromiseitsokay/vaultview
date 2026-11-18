package com.vaultview.app.data

import androidx.compose.ui.graphics.Color

enum class Brand(val label: String, val color: Color) {
    NINTENDO("Nintendo", Color(0xFFE60012)),
    SONY("PlayStation", Color(0xFF2F6FE4)),
    SEGA("Sega", Color(0xFF0089CF)),
    MICROSOFT("Xbox", Color(0xFF107C10)),
    ATARI("Atari", Color(0xFFD9411E)),
    NEC("NEC", Color(0xFFF07C00)),
    PHILIPS("Philips", Color(0xFF7A5CD6)),
}

data class GameSystem(
    val code: String,      // vimm.net path segment, e.g. "SNES"
    val name: String,
    val brand: Brand,
    val year: Int,
    val handheld: Boolean = false,
    val folder: String = code.lowercase(), // ES-DE / RetroArch-style ROM folder name
) {
    /** Folder name on thumbnails.libretro.com, used to fill in missing box art. */
    val libretro: String? get() = Systems.libretroNames[code]
}

object Systems {
    val all = listOf(
        GameSystem("NES", "NES", Brand.NINTENDO, 1983, folder = "nes"),
        GameSystem("SNES", "Super Nintendo", Brand.NINTENDO, 1990, folder = "snes"),
        GameSystem("N64", "Nintendo 64", Brand.NINTENDO, 1996, folder = "n64"),
        GameSystem("GameCube", "GameCube", Brand.NINTENDO, 2001, folder = "gc"),
        GameSystem("Wii", "Wii", Brand.NINTENDO, 2006, folder = "wii"),
        GameSystem("WiiWare", "WiiWare", Brand.NINTENDO, 2008, folder = "wii"),
        GameSystem("WiiU", "Wii U", Brand.NINTENDO, 2012, folder = "wiiu"),
        GameSystem("GB", "Game Boy", Brand.NINTENDO, 1989, handheld = true, folder = "gb"),
        GameSystem("VB", "Virtual Boy", Brand.NINTENDO, 1995, handheld = true, folder = "virtualboy"),
        GameSystem("GBC", "Game Boy Color", Brand.NINTENDO, 1998, handheld = true, folder = "gbc"),
        GameSystem("GBA", "Game Boy Advance", Brand.NINTENDO, 2001, handheld = true, folder = "gba"),
        GameSystem("DS", "Nintendo DS", Brand.NINTENDO, 2004, handheld = true, folder = "nds"),
        GameSystem("3DS", "Nintendo 3DS", Brand.NINTENDO, 2011, handheld = true, folder = "n3ds"),

        GameSystem("PS1", "PlayStation", Brand.SONY, 1994, folder = "psx"),
        GameSystem("PS2", "PlayStation 2", Brand.SONY, 2000, folder = "ps2"),
        GameSystem("PS3", "PlayStation 3", Brand.SONY, 2006, folder = "ps3"),
        GameSystem("PSP", "PlayStation Portable", Brand.SONY, 2004, handheld = true, folder = "psp"),

        GameSystem("SMS", "Master System", Brand.SEGA, 1985, folder = "mastersystem"),
        GameSystem("Genesis", "Genesis", Brand.SEGA, 1988, folder = "genesis"),
        GameSystem("SegaCD", "Sega CD", Brand.SEGA, 1991, folder = "segacd"),
        GameSystem("32X", "Sega 32X", Brand.SEGA, 1994, folder = "sega32x"),
        GameSystem("Saturn", "Saturn", Brand.SEGA, 1994, folder = "saturn"),
        GameSystem("Dreamcast", "Dreamcast", Brand.SEGA, 1998, folder = "dreamcast"),
        GameSystem("GG", "Game Gear", Brand.SEGA, 1990, handheld = true, folder = "gamegear"),

        GameSystem("Xbox", "Xbox", Brand.MICROSOFT, 2001, folder = "xbox"),
        GameSystem("Xbox360", "Xbox 360", Brand.MICROSOFT, 2005, folder = "xbox360"),
        GameSystem("X360-D", "Xbox 360 (Digital)", Brand.MICROSOFT, 2005, folder = "xbox360"),

        GameSystem("Atari2600", "Atari 2600", Brand.ATARI, 1977, folder = "atari2600"),
        GameSystem("Atari5200", "Atari 5200", Brand.ATARI, 1982, folder = "atari5200"),
        GameSystem("Atari7800", "Atari 7800", Brand.ATARI, 1986, folder = "atari7800"),
        GameSystem("Jaguar", "Jaguar", Brand.ATARI, 1993, folder = "atarijaguar"),
        GameSystem("JaguarCD", "Jaguar CD", Brand.ATARI, 1995, folder = "atarijaguarcd"),
        GameSystem("Lynx", "Lynx", Brand.ATARI, 1989, handheld = true, folder = "atarilynx"),

        GameSystem("TG16", "TurboGrafx-16", Brand.NEC, 1987, folder = "tg16"),
        GameSystem("TGCD", "TurboGrafx-CD", Brand.NEC, 1988, folder = "tg-cd"),

        GameSystem("CDi", "CD-i", Brand.PHILIPS, 1991, folder = "cdimono1"),
    )

    val libretroNames = mapOf(
        "NES" to "Nintendo - Nintendo Entertainment System", "SNES" to "Nintendo - Super Nintendo Entertainment System",
        "N64" to "Nintendo - Nintendo 64", "GameCube" to "Nintendo - GameCube", "Wii" to "Nintendo - Wii",
        "WiiU" to "Nintendo - Wii U", "GB" to "Nintendo - Game Boy", "GBC" to "Nintendo - Game Boy Color",
        "GBA" to "Nintendo - Game Boy Advance", "DS" to "Nintendo - Nintendo DS", "3DS" to "Nintendo - Nintendo 3DS",
        "VB" to "Nintendo - Virtual Boy", "PS1" to "Sony - PlayStation", "PS2" to "Sony - PlayStation 2",
        "PS3" to "Sony - PlayStation 3", "PSP" to "Sony - PlayStation Portable", "SMS" to "Sega - Master System - Mark III",
        "Genesis" to "Sega - Mega Drive - Genesis", "SegaCD" to "Sega - Mega-CD - Sega CD", "32X" to "Sega - 32X",
        "Saturn" to "Sega - Saturn", "Dreamcast" to "Sega - Dreamcast", "GG" to "Sega - Game Gear",
        "Xbox" to "Microsoft - Xbox", "Xbox360" to "Microsoft - Xbox 360", "Atari2600" to "Atari - 2600",
        "Atari5200" to "Atari - 5200", "Atari7800" to "Atari - 7800", "Jaguar" to "Atari - Jaguar",
        "Lynx" to "Atari - Lynx", "TG16" to "NEC - PC Engine - TurboGrafx 16",
        "TGCD" to "NEC - PC Engine CD - TurboGrafx-CD", "CDi" to "Philips - CD-i",
    )

    fun byCode(code: String) = all.firstOrNull { it.code == code }

    /** Matches either a vault code ("GBA") or a display name ("Game Boy Advance"). */
    fun find(nameOrCode: String?): GameSystem? = nameOrCode?.let { n ->
        all.firstOrNull { it.code.equals(n, true) } ?: all.firstOrNull { it.name.equals(n, true) }
    }
}
