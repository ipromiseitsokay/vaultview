package com.vaultview.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A game found in the ROMs folder. */
data class LibraryGame(
    val key: String,
    val title: String,           // "Super Mario World"
    val fileName: String,        // "Super Mario World (USA).sfc"
    val baseName: String,        // "Super Mario World (USA)" (No-Intro name, used for box art)
    val system: GameSystem?,
    val uri: Uri,
    val size: Long,
    val modified: Long,
    val gameId: Int?,            // vimm.net id when it was downloaded through the app
    val regions: List<String>,
    val folder: String,          // where it sits, e.g. "snes"
)

/** Scans the chosen ROMs folder (system subfolders and loose files) into a library. */
object Library {
    var games by mutableStateOf<List<LibraryGame>>(emptyList()); private set
    var scanning by mutableStateOf(false); private set
    var lastScan by mutableLongStateOf(0L); private set
    var error by mutableStateOf<String?>(null); private set

    private val aliases: Map<String, String> by lazy {
        val m = mutableMapOf<String, String>()
        Systems.all.forEach { s ->
            m[s.folder] = s.code; m[s.code.lowercase()] = s.code; m[s.name.lowercase()] = s.code
        }
        // Folder names other frontends and emulators use.
        mapOf(
            "ps1" to "PS1", "psx" to "PS1", "playstation" to "PS1", "ps2" to "PS2", "ps3" to "PS3", "psp" to "PSP",
            "megadrive" to "Genesis", "genesis" to "Genesis", "md" to "Genesis", "mastersystem" to "SMS", "sms" to "SMS",
            "gamegear" to "GG", "gg" to "GG", "segacd" to "SegaCD", "megacd" to "SegaCD", "sega32x" to "32X", "32x" to "32X",
            "saturn" to "Saturn", "dreamcast" to "Dreamcast", "dc" to "Dreamcast",
            "gc" to "GameCube", "gamecube" to "GameCube", "ngc" to "GameCube", "wii" to "Wii", "wiiu" to "WiiU",
            "nes" to "NES", "famicom" to "NES", "fc" to "NES", "snes" to "SNES", "sfc" to "SNES", "superfamicom" to "SNES",
            "n64" to "N64", "gb" to "GB", "gbc" to "GBC", "gba" to "GBA", "nds" to "DS", "ds" to "DS",
            "n3ds" to "3DS", "3ds" to "3DS", "virtualboy" to "VB", "vb" to "VB",
            "xbox" to "Xbox", "xbox360" to "Xbox360",
            "atari2600" to "Atari2600", "atari5200" to "Atari5200", "atari7800" to "Atari7800",
            "atarijaguar" to "Jaguar", "jaguar" to "Jaguar", "atarijaguarcd" to "JaguarCD", "atarilynx" to "Lynx", "lynx" to "Lynx",
            "tg16" to "TG16", "pcengine" to "TG16", "tg-cd" to "TGCD", "tgcd" to "TGCD", "pcenginecd" to "TGCD",
            "cdimono1" to "CDi", "cdi" to "CDi",
        ).forEach { (k, v) -> m[k] = v }
        m
    }

    private val byExtension = mapOf(
        "nes" to "NES", "fds" to "NES", "sfc" to "SNES", "smc" to "SNES", "n64" to "N64", "z64" to "N64", "v64" to "N64",
        "gb" to "GB", "gbc" to "GBC", "gba" to "GBA", "nds" to "DS", "3ds" to "3DS", "cia" to "3DS",
        "md" to "Genesis", "gen" to "Genesis", "smd" to "Genesis", "sms" to "SMS", "gg" to "GG", "32x" to "32X",
        "pce" to "TG16", "a26" to "Atari2600", "a52" to "Atari5200", "a78" to "Atari7800", "lnx" to "Lynx",
        "j64" to "Jaguar", "jag" to "Jaguar", "vb" to "VB", "gdi" to "Dreamcast", "cdi" to "Dreamcast",
        "cso" to "PSP", "pbp" to "PSP", "wbfs" to "Wii", "wua" to "WiiU", "wux" to "WiiU", "rvz" to "GameCube", "gcm" to "GameCube",
    )
    private val ignored = setOf(
        "txt", "nfo", "jpg", "jpeg", "png", "gif", "webp", "xml", "dat", "sav", "srm", "sta", "cfg", "ini", "db", "log",
        "pdf", "md5", "sfv", "sha1", "url", "html", "htm", "json", "mp4", "mkv", "mp3", "ogg", "wav", "zip.part", "tmp",
    )
    private val skipFolders = setOf("media", "images", "videos", "screenshots", "downloaded_media", "manuals", "saves", "states", "bios")
    private val regionWords = setOf("USA", "Europe", "Japan", "World", "France", "Germany", "Spain", "Italy", "UK", "Australia",
        "Korea", "Brazil", "China", "Canada", "Netherlands", "Sweden", "Asia", "Taiwan", "Hong Kong", "Russia", "Scandinavia")

    private data class Doc(val id: String, val name: String, val isDir: Boolean, val size: Long, val modified: Long)

    private fun children(context: Context, tree: Uri, docId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Doc(
                        id = c.getString(0), name = c.getString(1) ?: "",
                        isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        size = if (c.isNull(3)) 0 else c.getLong(3), modified = if (c.isNull(4)) 0 else c.getLong(4),
                    ))
                }
            }
        } ?: emptyList()
    }

    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
    /** Looks like a game file: has an extension (1-5 chars) that isn't on the ignore list. */
    private fun isRom(d: Doc) = !d.isDir && !d.name.startsWith(".") && ext(d.name).let { it.length in 1..5 && it !in ignored }
    private fun base(name: String) = if ('.' in name) name.substringBeforeLast('.') else name

    /** Picks the file to launch from a set (playlist first, then disc descriptors, then the biggest). */
    private fun pickMain(files: List<Doc>): Doc? {
        val roms = files.filter { isRom(it) }
        if (roms.isEmpty()) return null
        listOf("m3u", "cue", "gdi", "chd", "iso", "rvz", "cso").forEach { e -> roms.firstOrNull { ext(it.name) == e }?.let { return it } }
        return roms.maxByOrNull { it.size }
    }

    /** Files in one folder that each count as a game (hides .bin tracks behind their .cue, discs behind an .m3u). */
    private fun gamesIn(files: List<Doc>): List<Doc> {
        val roms = files.filter { isRom(it) }
        if (roms.any { ext(it.name) == "m3u" }) return roms.filter { ext(it.name) == "m3u" }
        val hasCue = roms.any { ext(it.name) == "cue" || ext(it.name) == "gdi" }
        return roms.filter { !(hasCue && ext(it.name) in setOf("bin", "raw", "img", "wav")) }
    }

    private fun parse(baseName: String): Pair<String, List<String>> {
        val title = baseName.substringBefore(" (").trim().ifEmpty { baseName }
        val regions = Regex("""\(([^)]*)\)""").findAll(baseName).map { it.groupValues[1] }
            .firstOrNull { g -> g.split(", ").all { it in regionWords } }?.split(", ") ?: emptyList()
        return title to regions
    }

    suspend fun scan(context: Context) {
        val folder = Settings.folderUri ?: run { games = emptyList(); return }
        if (scanning) return
        scanning = true; error = null
        try {
            val result = withContext(Dispatchers.IO) {
                val tree = Uri.parse(folder)
                val rootId = DocumentsContract.getTreeDocumentId(tree)
                val ids = downloadIndex()
                val out = mutableListOf<LibraryGame>()
                fun add(doc: Doc, title: String? = null, sys: GameSystem?, where: String) {
                    val b = title ?: base(doc.name)
                    val (t, regions) = parse(b)
                    out += LibraryGame(
                        key = doc.id, title = t, fileName = doc.name, baseName = b, system = sys,
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, doc.id),
                        size = doc.size, modified = doc.modified,
                        gameId = ids[b.lowercase()] ?: ids[base(doc.name).lowercase()], regions = regions, folder = where,
                    )
                }
                for (top in children(context, tree, rootId)) {
                    if (top.name.startsWith(".")) continue
                    if (top.isDir) {
                        val sys = aliases[top.name.lowercase()]?.let { Systems.byCode(it) }
                        if (sys == null || top.name.lowercase() in skipFolders) continue
                        val inside = children(context, tree, top.id)
                        gamesIn(inside).forEach { add(it, sys = sys, where = top.name) }
                        // One level down: a folder per game (multi-disc sets and the like).
                        inside.filter { it.isDir && !it.name.startsWith(".") && it.name.lowercase() !in skipFolders }.forEach { sub ->
                            pickMain(children(context, tree, sub.id))?.let { main ->
                                add(main.copy(size = main.size), title = sub.name, sys = sys, where = "${top.name}/${sub.name}")
                            }
                        }
                    } else if (isRom(top)) {
                        add(top, sys = byExtension[ext(top.name)]?.let { Systems.byCode(it) }, where = "")
                    }
                }
                out.sortedBy { it.title.lowercase() }
            }
            games = result
            lastScan = System.currentTimeMillis()
        } catch (e: Exception) {
            error = "Couldn't read the ROMs folder: ${e.message}"
        } finally {
            scanning = false
        }
    }

    /** Names of files the app downloaded, mapped to their vimm.net game id. */
    private fun downloadIndex(): Map<String, Int> {
        val m = mutableMapOf<String, Int>()
        Downloads.items.filter { it.state == DownloadState.DONE }.forEach { d ->
            (d.files + listOfNotNull(d.fileName)).forEach { f ->
                m[base(f.substringAfterLast('/')).lowercase()] = d.gameId
            }
        }
        return m
    }

    fun needsRescan(): Boolean =
        lastScan == 0L || Downloads.items.any { it.state == DownloadState.DONE && it.finishedAt > lastScan }
}
