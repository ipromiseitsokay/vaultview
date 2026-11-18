package com.vaultview.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Hands games to frontends (ES-DE, and iiSU through its "link ES-DE metadata" option) the way ES-DE
 * stores them, so they show up with box art, screenshots and details without any scraping:
 *
 *   ES-DE/gamelists/<system>/gamelist.xml
 *   ES-DE/downloaded_media/<system>/covers/<rom name>.png
 *   ES-DE/downloaded_media/<system>/screenshots/<rom name>.png
 *
 * <system> is the ROM's system folder (snes, psx, …). Entries and pictures that already exist are
 * left alone, so anything you scraped or edited yourself is never overwritten.
 */
object Frontends {
    /** One game to describe: where its file sits in the ROMs folder, and what we know about it. */
    data class Entry(
        val folder: String,     // system folder, e.g. "snes"
        val relPath: String,    // path inside it, e.g. "Super Mario World (USA).sfc" or "FF7/FF7.m3u"
        val name: String,
        val systemCode: String?,
        val gameId: Int?,
        val rating: String?,    // vimm's, out of 10
        val baseName: String,   // file name without extension (No-Intro name)
    )

    var busy by mutableStateOf<Float?>(null)
        private set
    var lastResult by mutableStateOf<String?>(null)
        private set
    private val lock = Mutex()

    // ---- Game details, captured from the game page when you download ---------------------------

    private var facts: JSONObject? = null
    private fun factsFile(context: Context) = File(context.filesDir, "facts.json")

    /** Remembers the details table of a game page (Players, Year, Publisher…) for later export. */
    fun rememberFacts(context: Context, gameId: Int, list: List<Pair<String, String>>) {
        if (list.isEmpty()) return
        val all = loadFacts(context)
        all.put(gameId.toString(), JSONObject().apply { list.forEach { (k, v) -> put(k, v) } })
        runCatching { factsFile(context).writeText(all.toString()) }
    }

    private fun loadFacts(context: Context): JSONObject =
        facts ?: runCatching { JSONObject(factsFile(context).readText()) }.getOrElse { JSONObject() }.also { facts = it }

    // ---- The ES-DE folder ------------------------------------------------------------------------

    /** Label for the chosen folder, like "Internal storage/ES-DE". */
    fun folderLabel(): String? = Settings.esdeUri?.let { u ->
        val docId = Uri.decode(Uri.parse(u).lastPathSegment ?: return@let null)
        val (vol, path) = docId.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        (if (vol == "primary") "Internal storage" else "SD card") + if (path.isEmpty()) "" else "/$path"
    }

    // ---- Exports -----------------------------------------------------------------------------------

    /** After a download: describe the game that just landed in the ROMs folder. */
    suspend fun exportDownload(context: Context, item: DownloadItem) {
        if (!Settings.frontendExport || Settings.esdeUri == null) return
        val sys = item.system ?: return
        if (!Settings.systemFolders) return // ES-DE needs games sorted into system folders
        val files = item.files.ifEmpty { listOfNotNull(item.fileName) }
        val main = pickMain(files) ?: return
        val rating = Catalog.systemNow(sys.code)?.games?.firstOrNull { it.id == item.gameId }?.rating
        runCatching {
            write(context, listOf(Entry(sys.folder, main, item.title, sys.code, item.gameId, rating, main.substringAfterLast('/').substringBeforeLast('.'))))
        }
    }

    /** Describes everything in the Library (games already on the handheld). */
    suspend fun exportLibrary(context: Context) {
        if (Settings.esdeUri == null) { lastResult = "Pick your ES-DE folder first"; return }
        if (Library.games.isEmpty()) Library.scan(context)
        busy = 0f
        try {
            val entries = Library.games.mapNotNull { g ->
                val top = g.folder.substringBefore('/').ifEmpty { return@mapNotNull null }
                val sub = g.folder.substringAfter('/', "")
                val match = g.gameId?.let { id -> g.system?.let { s -> Catalog.systemNow(s.code)?.games?.firstOrNull { it.id == id } } }
                    ?: g.system?.let { s -> findInCatalog(g.title, g.regions, s.code) }
                Entry(top, if (sub.isEmpty()) g.fileName else "$sub/${g.fileName}",
                    match?.title ?: g.title, g.system?.code, match?.id ?: g.gameId, match?.rating, g.baseName)
            }
            val n = write(context, entries) { busy = it }
            lastResult = "Added $n new game${if (n == 1) "" else "s"} for ES-DE / iiSU · ${entries.size} checked"
        } catch (e: Exception) {
            lastResult = "Couldn't write to the ES-DE folder: ${e.message}"
        } finally {
            busy = null
        }
    }

    private suspend fun findInCatalog(title: String, regions: List<String>, code: String): Game? {
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
        val want = norm(title)
        return Catalog.search(title, code).orEmpty().filter { norm(it.title) == want }
            .sortedBy { g -> if (regions.any { it in g.regions }) 0 else 1 }.firstOrNull()
    }

    /** The file a frontend should list: playlist first, then disc descriptors, then the first game file. */
    internal fun pickMain(files: List<String>): String? {
        fun ext(n: String) = n.substringAfterLast('.', "").lowercase()
        val roms = files.filter { ext(it).length in 1..5 && ext(it) !in Library.ignored && !it.substringAfterLast('/').startsWith(".") }
        listOf("m3u", "cue", "gdi", "chd", "iso", "rvz", "cso").forEach { e -> roms.firstOrNull { ext(it) == e }?.let { return it } }
        return roms.firstOrNull()
    }

    /** Writes entries and pictures. Returns how many games were new to the gamelists. */
    private suspend fun write(context: Context, entries: List<Entry>, onProgress: (Float) -> Unit = {}): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(Settings.esdeUri ?: return@withContext 0)
            val saf = Saf(context, tree)
            var added = 0
            var done = 0
            for ((folder, group) in entries.groupBy { it.folder }) {
                val lists = saf.dir(saf.root, "gamelists", folder)
                val media = saf.dir(saf.root, "downloaded_media", folder)
                val covers = saf.dir(media, "covers")
                val shots = saf.dir(media, "screenshots")
                val coverNames = saf.names(covers)
                val shotNames = saf.names(shots)
                added += mergeGamelist(context, saf, lists, group)
                for (e in group) {
                    val stem = e.relPath.substringBeforeLast('.')
                    val subdir = stem.substringBeforeLast('/', "")
                    val name = stem.substringAfterLast('/')
                    fun has(names: Set<String>) = listOf("png", "jpg", "jpeg", "webp").any { "$name.$it" in names }
                    // Pictures for games in their own folder go in a matching subfolder, as ES-DE expects.
                    val coverDir = if (subdir.isEmpty()) covers else saf.dir(covers, *subdir.split('/').toTypedArray())
                    val shotDir = if (subdir.isEmpty()) shots else saf.dir(shots, *subdir.split('/').toTypedArray())
                    if (!has(if (subdir.isEmpty()) coverNames else saf.names(coverDir))) {
                        coverSources(e).firstNotNullOfOrNull { fetch(it) }?.let { (bytes, ext) -> saf.writeFile(coverDir, "$name.$ext", bytes) }
                    }
                    if (e.gameId != null && !has(if (subdir.isEmpty()) shotNames else saf.names(shotDir))) {
                        fetch(Vimm.screenshot(e.gameId))?.let { (bytes, ext) -> saf.writeFile(shotDir, "$name.$ext", bytes) }
                    }
                    done++
                    withContext(Dispatchers.Main) { onProgress(done.toFloat() / entries.size) }
                }
            }
            added
        }
    }

    private fun coverSources(e: Entry): List<String> =
        listOfNotNull(e.gameId?.let { Vimm.boxArt(it) }, Vimm.libretroExact(e.systemCode, e.baseName)) +
            Vimm.libretroBoxArt(e.systemCode, e.name, emptyList()).take(2)

    /** Downloads a picture through the app's client (which turns vimm's "not found" pictures into 404s). */
    private fun fetch(url: String): Pair<ByteArray, String>? = runCatching {
        Vimm.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val bytes = r.body?.bytes() ?: return null
            if (bytes.size < 2048) return null
            val png = bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
            bytes to if (png) "png" else "jpg"
        }
    }.getOrNull()

    /** Adds missing <game> entries to gamelist.xml (creating it if needed). Returns how many were added. */
    private fun mergeGamelist(context: Context, saf: Saf, dir: String, group: List<Entry>): Int {
        val existing = saf.find(dir, "gamelist.xml")
        val factory = DocumentBuilderFactory.newInstance()
        val doc = existing?.let { id ->
            runCatching { context.contentResolver.openInputStream(saf.uri(id))!!.use { factory.newDocumentBuilder().parse(it) } }.getOrNull()
        } ?: factory.newDocumentBuilder().newDocument().apply { appendChild(createElement("gameList")) }
        val root = doc.documentElement
        val have = mutableSetOf<String>()
        val games = root.getElementsByTagName("game")
        for (i in 0 until games.length) {
            (games.item(i) as Element).getElementsByTagName("path").item(0)?.textContent?.trim()?.let { have += it }
        }
        val all = loadFacts(context)
        var added = 0
        for (e in group) {
            val path = "./" + e.relPath
            if (path in have) continue
            val g = doc.createElement("game")
            fun field(tag: String, value: String?) {
                if (value.isNullOrBlank()) return
                g.appendChild(doc.createElement(tag).apply { textContent = value })
            }
            val f = e.gameId?.let { all.optJSONObject(it.toString()) }
            field("path", path)
            field("name", e.name)
            field("rating", e.rating?.toFloatOrNull()?.let { "%.2f".format(java.util.Locale.US, (it / 10f).coerceIn(0f, 1f)) })
            field("releasedate", f?.optString("Year")?.let { Regex("\\d{4}").find(it)?.value }?.let { "${it}0101T000000" })
            field("developer", f?.optString("Developer"))
            field("publisher", f?.optString("Publisher"))
            // ES-DE wants "2" or "1-4", not vimm's "2 Simultaneous".
            field("players", f?.optString("Players")?.let { Regex("\\d+(-\\d+)?").find(it)?.value })
            root.appendChild(g)
            have += path
            added++
        }
        if (added == 0 && existing != null) return 0
        val out = java.io.ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }.transform(DOMSource(doc), StreamResult(out))
        // Same layout ES-DE writes: declaration on its own line.
        val xml = out.toString("UTF-8").replaceFirst(Regex("\\?>\\s*<gameList"), "?>\n<gameList")
        saf.writeFile(dir, "gamelist.xml", xml.toByteArray(), overwrite = true)
        return added
    }

    /** Minimal Storage Access Framework helper over one picked folder, by document id. */
    private class Saf(val context: Context, val tree: Uri) {
        val root: String = DocumentsContract.getTreeDocumentId(tree)
        private val cache = mutableMapOf<String, Map<String, Pair<String, Boolean>>>()

        fun uri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

        /** name -> (id, isDir) for a folder's children. */
        private fun list(id: String): Map<String, Pair<String, Boolean>> = cache.getOrPut(id) {
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE)
            context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), cols, null, null, null)?.use { c ->
                buildMap { while (c.moveToNext()) put(c.getString(1) ?: "", c.getString(0) to (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)) }
            } ?: emptyMap()
        }

        fun names(id: String): Set<String> = list(id).keys
        fun find(dir: String, name: String): String? = list(dir)[name]?.takeIf { !it.second }?.first

        /** Walks into (creating as needed) nested folders under [from]. */
        fun dir(from: String, vararg path: String): String = path.fold(from) { cur, name ->
            list(cur)[name]?.takeIf { it.second }?.first ?: run {
                val made = DocumentsContract.createDocument(context.contentResolver, uri(cur), DocumentsContract.Document.MIME_TYPE_DIR, name)
                    ?: throw java.io.IOException("Couldn't create folder $name")
                cache.remove(cur)
                DocumentsContract.getDocumentId(made)
            }
        }

        fun writeFile(dir: String, name: String, bytes: ByteArray, overwrite: Boolean = false) {
            val existing = find(dir, name)
            if (existing != null && !overwrite) return
            // octet-stream so the provider never renames the file (e.g. adding ".txt").
            val target = existing?.let { uri(it) }
                ?: DocumentsContract.createDocument(context.contentResolver, uri(dir), "application/octet-stream", name)
                ?: throw java.io.IOException("Couldn't create $name")
            context.contentResolver.openOutputStream(target, "wt")!!.use { it.write(bytes) }
            cache.remove(dir)
        }
    }
}
