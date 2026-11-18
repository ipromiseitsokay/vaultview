package com.vaultview.app.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** A native Android port (source port, recompilation or decompilation) from the Quiver catalog. */
data class Port(
    val slug: String,
    val name: String,
    val project: String?,
    val developer: String?,
    val consoles: List<String>,
    val gameTitle: String?,
    val capsule: String?,  // 2:3 cover
    val hero: String?,     // wide banner
    val icon: String?,
    val version: String?,
    val reviews: Int,
    val needs: List<String>, // what the port asks you to provide, e.g. "Super Mario 64 ROM"
    val android: AndroidBuild? = null, // the checked Android download, when the daily list found one
) {
    /** The vimm.net system the original game came out on, if the Vault has it. */
    val vimmSystem: String? get() = consoles.firstNotNullOfOrNull { Ports.consoleToVimm[it] }
}

/** An installable Android build: an .apk, or a .zip holding [apk]. */
data class AndroidBuild(val url: String, val filename: String, val sha256: String?, val apk: String?)

/** A list someone shared from Quiver Launcher (quiverlauncher.com/lists/...). */
data class QuiverList(val slug: String, val name: String, val owner: String, val ports: List<Port>)

/**
 * Ports, as listed by the public catalog behind Quiver Launcher (quiverlauncher.com). VaultView only
 * reads that catalog; the ports themselves come from their developers' own release pages, checked
 * against the checksums the catalog recorded.
 */
object Ports {
    private const val API = "https://api.quiverlauncher.com/api/v1"
    const val QUIVER_PACKAGE = "com.quiverlauncher.app"
    const val QUIVER_REPO = "tgeorgiadis/quiver-launcher"

    var items by mutableStateOf<List<Port>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Install progress per slug (0..1), and a short status line per slug. */
    val progress = mutableStateMapOf<String, Float>()
    val messages = mutableStateMapOf<String, String>()

    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

    /** Quiver's console codes to vimm.net's system codes. */
    val consoleToVimm = mapOf(
        "nes" to "NES", "snes" to "SNES", "n64" to "N64", "gcn" to "GameCube", "wii" to "Wii", "wiiu" to "WiiU",
        "gb" to "GB", "gbc" to "GBC", "gba" to "GBA", "vb" to "VB", "nds" to "DS", "3ds" to "3DS",
        "ps1" to "PS1", "ps2" to "PS2", "ps3" to "PS3", "psp" to "PSP", "og xbox" to "Xbox", "x360" to "Xbox360",
        "sms" to "SMS", "gg" to "GG", "gen" to "Genesis", "segacd" to "SegaCD", "32x" to "32X",
        "saturn" to "Saturn", "dreamcast" to "Dreamcast", "cdi" to "CDi",
    )

    val consoleNames = mapOf(
        "nes" to "NES", "snes" to "SNES", "n64" to "N64", "gcn" to "GameCube", "wii" to "Wii", "wiiu" to "Wii U",
        "switch" to "Switch", "gb" to "Game Boy", "gbc" to "GBC", "gba" to "GBA", "nds" to "DS", "3ds" to "3DS",
        "ps1" to "PlayStation", "ps2" to "PS2", "ps3" to "PS3", "ps4" to "PS4", "psp" to "PSP", "vita" to "Vita",
        "og xbox" to "Xbox", "x360" to "Xbox 360", "sms" to "Master System", "gg" to "Game Gear", "gen" to "Genesis",
        "segacd" to "Sega CD", "saturn" to "Saturn", "dreamcast" to "Dreamcast", "arcade" to "Arcade", "pc" to "PC",
        "mobile" to "Mobile",
    )

    private fun cacheFile(context: Context) = File(context.filesDir, "ports.json")

    /** Shows the saved list straight away, then refreshes it if it's more than a day old. */
    suspend fun load(context: Context, force: Boolean = false) {
        val file = cacheFile(context)
        if (items.isEmpty() && file.exists()) runCatching { items = parse(JSONArray(file.readText())) }
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 24 * 3600 * 1000L
        if (fresh && !force && items.isNotEmpty()) return
        loading = true
        error = null
        try {
            val all = withContext(Dispatchers.IO) {
                // First choice: the daily list on our GitHub release, which keeps only ports whose
                // latest release really has an Android build. Fall back to the live catalog.
                runCatching { dailyList() }.getOrNull()?.let { return@withContext it }
                val out = JSONArray()
                var cursor: String? = null
                repeat(6) { // up to 600 ports
                    val url = "$API/apps?os=android&projectType=port&sort=rating&limit=100" +
                        (cursor?.let { "&cursor=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
                    val page = JSONObject(get(url))
                    val arr = page.getJSONArray("items")
                    for (i in 0 until arr.length()) out.put(arr.getJSONObject(i))
                    cursor = page.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" }
                    if (page.optBoolean("isDone", true) || cursor == null) return@withContext out
                }
                out
            }
            file.writeText(all.toString())
            items = parse(all)
        } catch (e: Exception) {
            if (items.isEmpty()) error = "Couldn't reach the ports catalog. Check your connection."
        } finally {
            loading = false
        }
    }

    private const val DAILY = "https://github.com/ipromiseitsokay/vaultview/releases/download/catalog/ports.json.gz"

    private fun dailyList(): JSONArray =
        http.newCall(Request.Builder().url(DAILY).header("User-Agent", "VaultView").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val text = java.util.zip.GZIPInputStream(r.body!!.byteStream()).bufferedReader().readText()
            JSONObject(text).getJSONArray("ports")
        }

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).header("User-Agent", "VaultView").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body!!.string()
        }

    internal fun parse(arr: JSONArray): List<Port> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val game = o.optJSONArray("games")?.optJSONObject(0)
        val art = o.optJSONObject("libraryArt") ?: game?.optJSONObject("libraryArt")
        fun s(obj: JSONObject?, k: String) = obj?.optString(k)?.takeIf { it.isNotBlank() && it != "null" }
        val needs = o.optJSONObject("launcher")?.optJSONArray("setupFiles")?.let { sf ->
            (0 until sf.length()).mapNotNull { sf.optJSONObject(it) }
                .filter { !it.has("required") || it.optBoolean("required", true) }
                .mapNotNull { s(it, "label") }.distinct()
        } ?: emptyList()
        Port(
            slug = o.getString("slug"),
            name = o.optString("name"),
            project = s(o, "projectName"),
            developer = o.optJSONObject("developer")?.let { s(it, "name") },
            consoles = o.optJSONArray("consoles")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            gameTitle = s(game, "title"),
            capsule = s(art, "capsule"),
            hero = s(art, "hero") ?: s(art, "header"),
            icon = s(o, "artwork") ?: s(game, "artwork"),
            version = s(o, "lastReleaseVersion"),
            reviews = o.optInt("reviewCount"),
            needs = needs,
            android = o.optJSONObject("android")?.let { a ->
                AndroidBuild(a.getString("url"), a.getString("filename"), s(a, "sha256"), s(a, "apk"))
            },
        )
    }

    // ---- The original game on the Vault -------------------------------------------------------

    /** Finds the vimm.net game this port is based on: same system, closest title, USA first. */
    suspend fun vimmGame(p: Port): Game? {
        val code = p.vimmSystem ?: return null
        val title = p.gameTitle ?: p.name
        // Vimm puts articles at the end: "Legend of Zelda, The: ..."
        val vimmStyle = Regex("^(The|A|An) (.+?)(:.*)?$").find(title)?.let { m ->
            "${m.groupValues[2]}, ${m.groupValues[1]}${m.groupValues[3]}"
        }
        val candidates = (Catalog.search(vimmStyle ?: title, code).orEmpty() +
            (if (vimmStyle != null) Catalog.search(title, code).orEmpty() else emptyList())).distinctBy { it.id }
        if (candidates.isEmpty()) return null
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
        val want = setOfNotNull(norm(title), vimmStyle?.let(::norm))
        return candidates.sortedWith(
            compareBy<Game> { norm(it.title) !in want }
                .thenBy { it.title.contains("(") }
                .thenBy { "USA" !in it.regions }
                .thenBy { it.title.length }
        ).first()
    }

    // ---- Installing --------------------------------------------------------------------------

    private fun prefs(context: Context) = context.getSharedPreferences("ports", Context.MODE_PRIVATE)

    /** Installed ports on this device: slug to Android package. Updated by [refreshInstalled] and [sync]. */
    var installed by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** The Android package a port installed as, if it's on the device. */
    fun packageOf(context: Context, slug: String): String? =
        installed[slug] ?: prefs(context).getString(slug, null)?.takeIf { isInstalled(context, it) }

    /** Re-checks which known ports are still installed (cheap; run on resume). */
    fun refreshInstalled(context: Context) {
        val all = prefs(context).all.filterKeys { !it.startsWith("_") }.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
        installed = all.filter { isInstalled(context, it.second) }.toMap()
    }

    var list by mutableStateOf<QuiverList?>(null)
        private set
    var lastSync by mutableStateOf<String?>(null)
        private set

    fun listSlug(context: Context): String? = prefs(context).getString("_list", null)

    /** Accepts quiverlauncher.com/lists/NAME or just NAME, like Quiver itself does. */
    fun parseListSlug(input: String): String? =
        Regex("^(?:(?:https?://)?(?:www\\.)?quiverlauncher\\.com/lists/)?([a-z0-9-]{1,100})/?(?:[?#].*)?$", RegexOption.IGNORE_CASE)
            .find(input.trim())?.groupValues?.get(1)?.lowercase()

    fun setList(context: Context, slug: String?) {
        prefs(context).edit().apply { if (slug == null) remove("_list") else putString("_list", slug) }.apply()
        if (slug == null) list = null
    }

    /**
     * Sync with Quiver Launcher, without its website: finds every port installed on this device
     * (whether Quiver or VaultView installed it) by matching installed apps' names to the catalog, and
     * pulls your shared Quiver list, if you linked one.
     */
    suspend fun sync(context: Context): String {
        if (items.isEmpty()) load(context)
        val found = withContext(Dispatchers.Default) { findInstalled(context) }
        val p = prefs(context).edit()
        found.forEach { (slug, pkg) -> p.putString(slug, pkg) }
        p.apply()
        refreshInstalled(context)
        val parts = mutableListOf("${installed.size} port${if (installed.size == 1) "" else "s"} installed")
        listSlug(context)?.let { slug ->
            runCatching { fetchList(slug) }
                .onSuccess { list = it; parts += "list “${it.name}” has ${it.ports.size}" }
                .onFailure { parts += "couldn't read your Quiver list" }
        }
        return parts.joinToString(" · ").also { lastSync = it }
    }

    private suspend fun fetchList(slug: String): QuiverList = withContext(Dispatchers.IO) {
        val o = JSONObject(get("$API/lists/$slug"))
        val arr = o.getJSONArray("items")
        val entries = JSONArray()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.optJSONObject("entry")?.let { entries.put(it) }
        val known = items.associateBy { it.slug }
        QuiverList(slug, o.optString("name"), o.optJSONObject("owner")?.optString("name") ?: "",
            parse(entries).map { known[it.slug] ?: it })
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    /**
     * Matches launchable apps on the device to ports. Strongest first: the app's name is the project's
     * name ("Ghostship"), the package contains it (com.mgrz18.sdlpop), the app's name is the game's title
     * and only one port has that title ("Jill of the Jungle"), then a looser prefix match.
     */
    private fun findInstalled(context: Context): Map<String, String> {
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { Triple(it.activityInfo.packageName, norm(it.loadLabel(pm).toString()), norm(it.activityInfo.packageName)) }
            .filter { (pkg, label, _) -> pkg != context.packageName && pkg != QUIVER_PACKAGE && label.length >= 3 }
            .distinctBy { it.first }
        val titleCount = items.groupingBy { norm(it.name) }.eachCount()
        val out = mutableMapOf<String, String>()
        val taken = mutableSetOf<String>()
        fun claim(port: Port, pkg: String) { if (pkg !in taken) { out[port.slug] = pkg; taken += pkg } }
        val generic = Regex("^(android|arm64|arm|v8a|v7a|aarch64|x86|x8664|universal|release|debug|signed|unsigned|apk|app|game|port|v?\\d+)$")
        fun fromFile(f: String?) = f?.substringBeforeLast('.')?.split('-', '_', '.', ' ')
            ?.map(::norm)?.firstOrNull { it.isNotEmpty() && !generic.matches(it) }
        val names = items.associateWith { port ->
            listOfNotNull(port.project, fromFile(port.android?.filename))
                .map(::norm).map { it.removeSuffix("android").removePrefix("android") }
                .filter { it.length >= 4 && !generic.matches(it) }.distinct()
        }
        val tiers: List<(Port, Triple<String, String, String>) -> Boolean> = listOf(
            { p, (_, label, _) -> names.getValue(p).any { it == label } },
            { p, (_, _, pkg) -> names.getValue(p).any { it.length >= 5 && pkg.endsWith(it) } },
            { p, (_, label, _) -> norm(p.name).let { t -> t.length >= 6 && t == label && titleCount[t] == 1 } },
            { p, (_, label, _) -> names.getValue(p).any { n -> n.length >= 6 && (label.startsWith(n) || (label.length >= 6 && n.startsWith(label))) } },
        )
        for (tier in tiers) for (port in items) {
            if (port.slug in out) continue
            apps.firstOrNull { it.first !in taken && tier(port, it) }?.let { claim(port, it.first) }
        }
        return out
    }

    fun isInstalled(context: Context, pkg: String) =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun launch(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /**
     * Downloads the latest checked Android build, verifies it against the catalog's checksum, pulls the
     * APK out if it came zipped, and hands it to Android's installer.
     */
    suspend fun install(context: Context, p: Port) {
        messages.remove(p.slug)
        if (!Installer.canInstall(context)) { messages[p.slug] = "Allow VaultView to install apps, then press Install again"; return }
        progress[p.slug] = 0f
        try {
            val asset = p.android?.let { Asset(it.url, it.filename, it.sha256) }
                ?: withContext(Dispatchers.IO) { androidAsset(p.slug) }
                ?: throw IOException("No Android download for this port yet. Try it in Quiver.")
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Earlier port downloads have been handed to Android already; don't let them pile up.
            dir.listFiles { f -> f.name.startsWith("port-") }?.forEach { it.delete() }
            val download = File(dir, "port-${p.slug}-${asset.filename}")
            Installer.fetch(asset.url, download) { progress[p.slug] = it * 0.95f }
            val apk = withContext(Dispatchers.IO) {
                asset.sha256?.let { want ->
                    if (sha256(download) != want) { download.delete(); throw IOException("Download didn't match the catalog's checksum, so it wasn't installed.") }
                }
                if (download.name.endsWith(".apk", true)) download else apkFromZip(download, dir, p.slug)
            }
            context.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName?.let {
                prefs(context).edit().putString(p.slug, it).apply()
            }
            Installer.openInstaller(context, apk)
            messages[p.slug] = "Finish installing in Android's prompt"
        } catch (e: Exception) {
            messages[p.slug] = e.message ?: "Couldn't install"
        } finally {
            progress.remove(p.slug)
        }
    }

    private data class Asset(val url: String, val filename: String, val sha256: String?)

    private fun androidAsset(slug: String): Asset? {
        val rel = JSONObject(get("$API/apps/$slug/releases?limit=1")).getJSONArray("items").optJSONObject(0) ?: return null
        val assets = rel.getJSONArray("assets")
        val android = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("os") == "android" }
        fun rank(a: JSONObject): Int {
            val n = a.optString("filename").lowercase()
            val arch = a.optString("architecture").lowercase()
            return (if (n.endsWith(".apk")) 0 else 10) +
                (if ("arm64" in n || "aarch64" in n || arch == "arm64") 0 else if ("x86" in n || "armeabi" in n || arch.startsWith("x86")) 5 else 1)
        }
        val a = android.filter { it.optString("filename").lowercase().let { n -> n.endsWith(".apk") || n.endsWith(".zip") } }
            .minByOrNull(::rank) ?: return null
        val sum = (a.optString("checksum").takeIf { it.startsWith("sha256:") } ?: a.optString("unverifiedChecksum").takeIf { it.startsWith("sha256:") })
            ?.removePrefix("sha256:")
        return Asset(a.getString("url"), a.getString("filename"), sum)
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val buf = ByteArray(1 shl 16); while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun apkFromZip(zip: File, dir: File, slug: String): File = ZipFile(zip).use { z ->
        val entry = z.entries().toList().filter { !it.isDirectory && it.name.endsWith(".apk", true) }
            .sortedWith(compareBy({ !it.name.contains("arm64", true) }, { -it.size })).firstOrNull()
            ?: throw IOException("This port's Android download isn't an app you can install directly. Try it in Quiver.")
        val out = File(dir, "port-$slug.apk")
        z.getInputStream(entry).use { i -> out.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
        zip.delete()
        out
    }
}
