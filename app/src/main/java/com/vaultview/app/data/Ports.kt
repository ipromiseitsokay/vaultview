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
) {
    /** The vimm.net system the original game came out on, if the Vault has it. */
    val vimmSystem: String? get() = consoles.firstNotNullOfOrNull { Ports.consoleToVimm[it] }
}

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

    /** The Android package a port installed as, once we've seen its APK. */
    fun packageOf(context: Context, slug: String): String? =
        prefs(context).getString(slug, null)?.takeIf { isInstalled(context, it) }

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
            val asset = withContext(Dispatchers.IO) { androidAsset(p.slug) }
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
