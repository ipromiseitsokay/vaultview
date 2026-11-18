package com.vaultview.app.data

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Emulators the app knows how to start a game in. Package names and launch formats follow
 * ES-DE's Android launch rules, which are tested on handhelds.
 */
object Emulators {
    /** How a particular emulator wants to be handed a game. */
    class Emulator(
        val id: String,
        val name: String,
        /** package to activity ("." prefix means inside that package); first installed one wins */
        val targets: List<Pair<String, String>>,
        val systems: Set<String>,
        val build: (Intent, Rom, String /* package */) -> Unit,
    )

    /** Where to get an emulator that isn't installed yet. */
    sealed interface Source {
        /** Latest GitHub release; [pick] chooses the right APK from the release's file names. */
        data class GitHub(val repo: String, val pick: (List<String>) -> String?) : Source
        /** libretro's official RetroArch build (64-bit). */
        data object RetroArchBuildbot : Source
        data class PlayStore(val pkg: String) : Source
    }

    /** Best source for each emulator. Play Store when that's the only official channel. */
    val sources: Map<String, Source> = mapOf(
        "retroarch" to Source.RetroArchBuildbot,
        "duckstation" to Source.PlayStore("com.github.stenzek.duckstation"),
        "nethersx2" to Source.GitHub("Trixarian/NetherSX2-patch") { n -> n.firstOrNull { it.endsWith(".apk") } },
        "armsx2" to Source.GitHub("ARMSX2/ARMSX2") { n ->
            // Builds are per Android version ("…-sdk33.apk"): take the newest one this device can run.
            val sdk = android.os.Build.VERSION.SDK_INT
            n.filter { it.endsWith(".apk") }
                .mapNotNull { name -> Regex("""sdk(\d+)""").find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to name } }
                .filter { it.first <= sdk }.maxByOrNull { it.first }?.second
        },
        "ppsspp" to Source.PlayStore("org.ppsspp.ppsspp"),
        "dolphin" to Source.PlayStore("org.dolphinemu.dolphinemu"),
        "azahar" to Source.GitHub("azahar-emu/azahar") { n -> n.firstOrNull { it.endsWith(".apk") && "vanilla" in it } ?: n.firstOrNull { it.endsWith(".apk") } },
        "melonds" to Source.GitHub("rafaelvcaetano/melonDS-android") { n -> n.firstOrNull { it.endsWith(".apk") } },
        "drastic" to Source.PlayStore("com.dsemu.drastic"),
        "flycast" to Source.GitHub("flyinghead/flycast") { n -> n.firstOrNull { it.endsWith(".apk") } },
        "redream" to Source.PlayStore("io.recompiled.redream"),
        "m64plusfz" to Source.PlayStore("org.mupen64plusae.v3.fzurita"),
        "myboy" to Source.PlayStore("com.fastemulator.gba"),
        "pizzaboygba" to Source.PlayStore("it.dbtecno.pizzaboygba"),
        "cemu" to Source.GitHub("SSimco/Cemu") { n -> n.firstOrNull { it.endsWith(".apk") } },
        "aps3e" to Source.GitHub("aenu1/aps3e") { n -> n.firstOrNull { it.endsWith(".apk") } },
    )

    /** Bumped when VaultView comes back to the front, so "installed" checks run again. */
    fun refresh() { version++ }

    /** A game file, as a document link and (when on regular storage) a plain path. */
    class Rom(val uri: Uri, val path: String?)

    private const val RA_ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"

    /** RetroArch cores per system: label to core name. The first is the default. */
    val cores: Map<String, List<Pair<String, String>>> = mapOf(
        "NES" to listOf("FCEUmm" to "fceumm", "Mesen" to "mesen", "Nestopia" to "nestopia"),
        "SNES" to listOf("Snes9x" to "snes9x", "bsnes" to "bsnes"),
        "N64" to listOf("Mupen64Plus-Next" to "mupen64plus_next_gles3", "ParaLLEl N64" to "parallel_n64"),
        "GB" to listOf("Gambatte" to "gambatte", "SameBoy" to "sameboy", "mGBA" to "mgba"),
        "GBC" to listOf("Gambatte" to "gambatte", "SameBoy" to "sameboy", "mGBA" to "mgba"),
        "GBA" to listOf("mGBA" to "mgba", "VBA-M" to "vbam", "gpSP" to "gpsp"),
        "DS" to listOf("melonDS DS" to "melondsds", "DeSmuME" to "desmume"),
        "VB" to listOf("Beetle VB" to "mednafen_vb"),
        "PS1" to listOf("SwanStation" to "swanstation", "PCSX ReARMed" to "pcsx_rearmed", "Beetle PSX HW" to "mednafen_psx_hw"),
        "PS2" to listOf("LRPS2" to "pcsx2"),
        "PSP" to listOf("PPSSPP" to "ppsspp"),
        "Genesis" to listOf("Genesis Plus GX" to "genesis_plus_gx", "PicoDrive" to "picodrive"),
        "SMS" to listOf("Genesis Plus GX" to "genesis_plus_gx", "PicoDrive" to "picodrive"),
        "GG" to listOf("Genesis Plus GX" to "genesis_plus_gx", "PicoDrive" to "picodrive"),
        "SegaCD" to listOf("Genesis Plus GX" to "genesis_plus_gx", "PicoDrive" to "picodrive"),
        "32X" to listOf("PicoDrive" to "picodrive"),
        "Saturn" to listOf("YabaSanshiro" to "yabasanshiro", "Beetle Saturn" to "mednafen_saturn"),
        "Dreamcast" to listOf("Flycast" to "flycast"),
        "GameCube" to listOf("Dolphin" to "dolphin"),
        "Wii" to listOf("Dolphin" to "dolphin"),
        "WiiWare" to listOf("Dolphin" to "dolphin"),
        "3DS" to listOf("Citra" to "citra"),
        "Atari2600" to listOf("Stella" to "stella2014"),
        "Atari5200" to listOf("a5200" to "a5200"),
        "Atari7800" to listOf("ProSystem" to "prosystem"),
        "Lynx" to listOf("Handy" to "handy", "Beetle Lynx" to "mednafen_lynx"),
        "Jaguar" to listOf("Virtual Jaguar" to "virtualjaguar"),
        "TG16" to listOf("Beetle PCE FAST" to "mednafen_pce_fast", "Beetle PCE" to "mednafen_pce"),
        "TGCD" to listOf("Beetle PCE FAST" to "mednafen_pce_fast", "Beetle PCE" to "mednafen_pce"),
        "CDi" to listOf("SAME CDi" to "same_cdi"),
    )

    private fun Intent.clear() = addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    private fun Intent.view(rom: Rom) { action = Intent.ACTION_VIEW; data = rom.uri }

    val all: List<Emulator> = listOf(
        Emulator(
            "retroarch", "RetroArch",
            listOf("com.retroarch.aarch64" to RA_ACTIVITY, "com.retroarch" to RA_ACTIVITY, "com.retroarch.ra32" to RA_ACTIVITY),
            cores.keys,
        ) { i, rom, pkg -> i.action = Intent.ACTION_MAIN; i.clear(); i.putExtra("ROM", rom.path) ; i.putExtra("CONFIGFILE", "/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg") },
        Emulator("duckstation", "DuckStation", listOf("com.github.stenzek.duckstation" to ".EmulationActivity"), setOf("PS1")) { i, rom, _ ->
            i.clear(); i.putExtra("resumeState", false); i.putExtra("bootPath", rom.uri.toString())
        },
        Emulator("nethersx2", "NetherSX2 / AetherSX2", listOf("xyz.aethersx2.android" to ".EmulationActivity"), setOf("PS2")) { i, rom, _ ->
            i.clear(); i.action = Intent.ACTION_MAIN; i.putExtra("bootPath", rom.uri.toString())
        },
        Emulator(
            "armsx2", "ARMSX2",
            listOf("com.armsx2" to ".MainActivity", "come.nanodata.armsx2" to "com.armsx2.MainActivity", "com.armsx2.nightly" to "com.armsx2.MainActivity"),
            setOf("PS2"),
        ) { i, rom, _ -> i.view(rom) },
        Emulator(
            "ppsspp", "PPSSPP",
            listOf("org.ppsspp.ppssppgold" to "org.ppsspp.ppsspp.PpssppActivity", "org.ppsspp.ppsspp" to ".PpssppActivity"),
            setOf("PSP"),
        ) { i, rom, _ -> i.view(rom); i.addCategory(Intent.CATEGORY_DEFAULT) },
        Emulator("dolphin", "Dolphin", listOf("org.dolphinemu.dolphinemu" to ".ui.main.TvMainActivity"), setOf("GameCube", "Wii", "WiiWare")) { i, rom, _ ->
            i.action = Intent.ACTION_MAIN; i.addCategory("android.intent.category.LEANBACK_LAUNCHER"); i.putExtra("AutoStartFile", rom.uri.toString())
        },
        Emulator(
            "azahar", "Azahar / Lime3DS / Citra",
            listOf(
                "org.azahar_emu.azahar" to "org.citra.citra_emu.activities.EmulationActivity",
                "io.github.lime3ds.android" to "org.citra.citra_emu.activities.EmulationActivity",
                "org.citra.citra_emu" to ".activities.EmulationActivity",
            ),
            setOf("3DS"),
        ) { i, rom, _ -> i.clear(); i.data = rom.uri },
        Emulator(
            "melonds", "melonDS",
            listOf("me.magnum.melonds" to ".ui.emulator.EmulatorActivity", "me.magnum.melondualds" to "me.magnum.melonds.ui.emulator.EmulatorActivity"),
            setOf("DS"),
        ) { i, rom, _ -> i.action = "me.magnum.melonds.LAUNCH_ROM"; i.putExtra("uri", rom.uri.toString()) },
        Emulator("drastic", "DraStic", listOf("com.dsemu.drastic" to ".DraSticActivity"), setOf("DS")) { i, rom, _ -> i.clear(); i.data = rom.uri },
        Emulator(
            "flycast", "Flycast",
            listOf("com.flycast.emulator" to "com.flycast.emulator.MainActivity"),
            setOf("Dreamcast"),
        ) { i, rom, _ -> i.view(rom) },
        Emulator("redream", "Redream", listOf("io.recompiled.redream" to ".MainActivity"), setOf("Dreamcast")) { i, rom, _ -> i.view(rom) },
        Emulator(
            "m64plusfz", "M64Plus FZ",
            listOf(
                "org.mupen64plusae.v3.fzurita.pro" to "paulscode.android.mupen64plusae.SplashActivity",
                "org.mupen64plusae.v3.fzurita" to "paulscode.android.mupen64plusae.SplashActivity",
                "org.mupen64plusae.v3.fzurita.amazon" to "paulscode.android.mupen64plusae.SplashActivity",
            ),
            setOf("N64"),
        ) { i, rom, _ -> i.view(rom) },
        Emulator("myboy", "My Boy!", listOf("com.fastemulator.gba" to ".EmulatorActivity"), setOf("GBA")) { i, rom, _ -> i.view(rom) },
        Emulator(
            "pizzaboygba", "Pizza Boy GBA",
            listOf(
                "it.dbtecno.pizzaboygbapro" to "it.dbtecno.pizzaboygbapro.MainActivity",
                "it.dbtecno.pizzaboygba" to "it.dbtecno.pizzaboygba.MainActivity",
            ),
            setOf("GBA"),
        ) { i, rom, _ -> i.clear(); i.putExtra("rom_uri", rom.uri.toString()) },
        Emulator(
            "cemu", "Cemu",
            listOf("info.cemu.cemu" to "info.cemu.cemu.emulation.EmulationActivity", "info.cemu.Cemu" to "info.cemu.Cemu.emulation.EmulationActivity"),
            setOf("WiiU"),
        ) { i, rom, _ -> i.data = rom.uri },
        Emulator(
            "ax360e", "aX360e",
            listOf("aenu.ax360e" to "aenu.ax360e.EmulatorActivity", "aenu.ax360e.free" to "aenu.ax360e.EmulatorActivity"),
            setOf("Xbox360", "X360-D"),
        ) { i, rom, _ -> i.action = "aenu.intent.action.AX360E"; i.putExtra("game_uri", rom.uri.toString()) },
        Emulator(
            "aps3e", "aPS3e (ISO)",
            listOf("aenu.aps3e.premium" to "aenu.aps3e.EmulatorActivity", "aenu.aps3e" to "aenu.aps3e.EmulatorActivity"),
            setOf("PS3"),
        ) { i, rom, _ -> i.action = "aenu.intent.action.APS3E"; i.putExtra("iso_uri", rom.uri.toString()) },
    )

    /** Which emulator to suggest first for each system when several are installed. */
    private val preferred = mapOf(
        "PS1" to listOf("duckstation", "retroarch"), "PS2" to listOf("nethersx2", "armsx2", "retroarch"),
        "PSP" to listOf("ppsspp", "retroarch"), "GameCube" to listOf("dolphin", "retroarch"),
        "Wii" to listOf("dolphin", "retroarch"), "WiiWare" to listOf("dolphin", "retroarch"),
        "3DS" to listOf("azahar", "retroarch"), "DS" to listOf("melonds", "drastic", "retroarch"),
        "Dreamcast" to listOf("flycast", "redream", "retroarch"), "N64" to listOf("retroarch", "m64plusfz"),
        "WiiU" to listOf("cemu"), "Xbox360" to listOf("ax360e"), "X360-D" to listOf("ax360e"), "PS3" to listOf("aps3e"),
    )

    /** Lower is better: the recommended emulators for a system come first. */
    fun rank(code: String?, id: String): Int = (preferred[code] ?: listOf("retroarch")).indexOf(id).let { if (it < 0) 50 else it }

    private lateinit var prefs: SharedPreferences
    /** Bumped when a choice changes, so screens refresh. */
    var version by mutableIntStateOf(0); private set

    fun init(context: Context) { prefs = context.getSharedPreferences("emulators", Context.MODE_PRIVATE) }

    fun forSystem(code: String?): List<Emulator> = if (code == null) emptyList() else all.filter { code in it.systems }

    fun installedPackage(context: Context, e: Emulator): Pair<String, String>? =
        e.targets.firstOrNull { (pkg, _) ->
            try { context.packageManager.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
        }

    /** The saved choice for a system: emulator id and, for RetroArch, the core. "other" means ask Android. */
    data class Choice(val emulatorId: String, val core: String? = null)

    fun choice(code: String?): Choice? = code?.let { c ->
        prefs.getString("emu_$c", null)?.let { s -> Choice(s.substringBefore(':'), s.substringAfter(':', "").ifBlank { null }) }
    }

    fun setChoice(code: String, choice: Choice) {
        prefs.edit().putString("emu_$code", choice.emulatorId + (choice.core?.let { ":$it" } ?: "")).apply()
        version++
    }

    /** The saved choice, or the best installed emulator for this system. */
    fun effectiveChoice(context: Context, code: String?): Choice? {
        choice(code)?.let { return it }
        val options = forSystem(code).filter { installedPackage(context, it) != null }
        val order = preferred[code] ?: listOf("retroarch")
        val best = options.sortedBy { order.indexOf(it.id).let { i -> if (i < 0) 99 else i } }.firstOrNull() ?: return null
        return Choice(best.id, if (best.id == "retroarch") cores[code]?.firstOrNull()?.second else null)
    }

    fun label(choice: Choice?, code: String?): String = when {
        choice == null -> "No emulator chosen"
        choice.emulatorId == "other" -> "Ask every time"
        else -> {
            val e = all.firstOrNull { it.id == choice.emulatorId }?.name ?: choice.emulatorId
            val core = choice.core?.let { c -> cores[code]?.firstOrNull { it.second == c }?.first ?: c }
            if (core != null) "$e · $core" else e
        }
    }

    /** "primary:ROMs/snes/x.sfc" style document ids to "/storage/emulated/0/ROMs/snes/x.sfc". */
    fun pathFor(uri: Uri): String? = runCatching {
        val id = DocumentsContract.getDocumentId(uri)
        val vol = id.substringBefore(':'); val rel = id.substringAfter(':', "")
        if (vol == "primary") "/storage/emulated/0/$rel" else if (vol.matches(Regex("[0-9A-F]{4}-[0-9A-F]{4}"))) "/storage/$vol/$rel" else null
    }.getOrNull()

    sealed interface Result {
        data object Started : Result
        data class Problem(val message: String) : Result
    }

    /** Starts a game. Returns what happened so the screen can explain any problem. */
    fun launch(context: Context, game: LibraryGame, choice: Choice): Result {
        val rom = Rom(game.uri, pathFor(game.uri))
        val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        if (choice.emulatorId == "other") {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(rom.uri, "*/*").addFlags(grant)
            return try {
                context.startActivity(Intent.createChooser(view, "Play ${game.title} with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Result.Started
            } catch (e: Exception) { Result.Problem("No app on this device can open that file.") }
        }
        val emu = all.firstOrNull { it.id == choice.emulatorId } ?: return Result.Problem("Unknown emulator.")
        val (pkg, act) = installedPackage(context, emu) ?: return Result.Problem("${emu.name} isn't installed.")
        val cls = if (act.startsWith(".")) pkg + act else act
        val intent = Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or grant)
        // Extras can carry a content link too; ClipData makes sure the read permission goes with it.
        intent.clipData = ClipData.newRawUri(game.title, rom.uri)
        if (emu.id == "retroarch") {
            if (rom.path == null) return Result.Problem("RetroArch needs the game on internal storage or an SD card.")
            val core = choice.core ?: cores[game.system?.code]?.firstOrNull()?.second
                ?: return Result.Problem("Pick a RetroArch core for this system first.")
            emu.build(intent, rom, pkg)
            intent.putExtra("LIBRETRO", "/data/data/$pkg/cores/${core}_libretro_android.so")
        } else {
            emu.build(intent, rom, pkg)
        }
        return try {
            context.startActivity(intent); Result.Started
        } catch (e: Exception) {
            Result.Problem("${emu.name} didn't start: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Every package above, for the manifest's package-visibility list (kept in sync by a test). */
    val packages: Set<String> get() = all.flatMap { it.targets.map { t -> t.first } }.toSet()
}
