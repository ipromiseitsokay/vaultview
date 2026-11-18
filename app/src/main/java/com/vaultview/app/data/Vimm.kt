package com.vaultview.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class Game(
    val id: Int,
    val title: String,
    val system: String? = null,
    val regions: List<String> = emptyList(),
    val version: String? = null,
    val rating: String? = null,
    val media: String? = null,
    val tags: List<String> = emptyList(),
)

/** Reads vimm.net's public list pages and turns them into [Game]s. Nothing is downloaded here. */
object Vimm {
    const val BASE = "https://vimm.net"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    lateinit var client: OkHttpClient
        private set

    private val memo = ConcurrentHashMap<String, List<Game>>()
    /** MD5s of vimm.net's two "image not found" pictures, in both the PNG and JPEG forms it serves. */
    private val missingImageHashes = setOf(
        "1d9f7728035c43225ddb91608ef94a36", "ebe5cdf2dc42fbe37e567c41b37ec9ea", // 250x100 banner
        "ef12b5554b8c6e77d3ed8ad91fe1a53b", "cadbbfff1f3f45455fa5e9637e48c63b", // 256x187 box
    )
    private val idRe = Regex("""/vault/(\d+)$""")

    fun init(context: Context) {
        client = buildClient(Cache(context.cacheDir.resolve("http"), 20L * 1024 * 1024))
    }

    internal fun buildClient(cache: Cache?): OkHttpClient =
        OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                // dl.vimm.net serves a placeholder instead of the art unless the request comes from vimm.net.
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", UA)
                        .header("Referer", "$BASE/")
                        .build()
                )
            }
            .addInterceptor { chain ->
                // Missing art comes back as HTTP 200 with a stock "Error: Image not found." picture.
                // Recognise those exact files and report a 404 so the UI can fall back.
                val res = chain.proceed(chain.request())
                if (res.isSuccessful && chain.request().url.encodedPath.endsWith("image.php")) {
                    val head = res.peekBody(4096).byteString()
                    if (head.size < 4096 && head.md5().hex() in missingImageHashes) {
                        res.close()
                        return@addInterceptor res.newBuilder().code(404).message("No image")
                            .body("".toResponseBody(null)).build()
                    }
                }
                res
            }
            .build()

    fun boxArt(id: Int) = "https://dl.vimm.net/image.php?type=box&id=$id"
    fun screenshot(id: Int) = "https://dl.vimm.net/image.php?type=screen&id=$id"
    fun gamePage(id: Int) = "$BASE/vault/$id"

    /**
     * Likely box-art URLs on thumbnails.libretro.com (No-Intro style names), best guess first.
     * vimm writes "Zelda, The: A Link to the Past"; No-Intro writes "Zelda, The - A Link to the Past (USA)".
     */
    fun libretroBoxArt(system: String?, title: String?, regions: List<String>): List<String> {
        val folder = Systems.find(system)?.libretro ?: return emptyList()
        if (title.isNullOrBlank()) return emptyList()
        val name = title.replace(Regex("\\s*\\((NP|Hack|Proto|Beta)\\)\\s*$"), "").replace(": ", " - ").trim()
        val tags = buildList {
            if (regions.size > 1) add(regions.joinToString(", "))
            addAll(regions)
            addAll(listOf("USA", "World", "Europe", "Japan"))
        }.distinct().take(4)
        fun clean(s: String) = s.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        return tags.map { "https://thumbnails.libretro.com/${enc(folder)}/Named_Boxarts/${enc(clean("$name ($it)"))}.png" }
    }

    /** Top-rated games shown on a system's landing page. */
    suspend fun topGames(code: String): List<Game> = cached("top:$code") {
        parseTop(get("$BASE/vault/$code"), Systems.byCode(code)?.name)
    }

    internal fun parseTop(doc: Document, system: String?): List<Game> =
        doc.select("tbody#topTen a[href]").mapNotNull { a ->
            val id = idRe.find(a.absUrl("href"))?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            Game(id = id, title = a.text(), system = system)
        }

    /** One letter of a system's library. [section] is "A".."Z" or "#". */
    suspend fun section(code: String, section: String): List<Game> = cached("sec:$code:$section") {
        val url = if (section == "#") {
            "$BASE/vault/".toHttpUrl().newBuilder()
                .addQueryParameter("p", "list").addQueryParameter("system", code)
                .addQueryParameter("section", "number").build().toString()
        } else "$BASE/vault/$code/$section"
        parseRows(get(url), Systems.byCode(code)?.name)
    }

    suspend fun search(query: String, code: String? = null): List<Game> = cached("q:$code:${query.lowercase()}") {
        val url = "$BASE/vault/".toHttpUrl().newBuilder().apply {
            addQueryParameter("p", "list")
            if (code != null) addQueryParameter("system", code)
            addQueryParameter("q", query)
        }.build().toString()
        parseRows(get(url), code?.let { Systems.byCode(it)?.name })
    }

    private suspend fun cached(key: String, load: suspend () -> List<Game>): List<Game> =
        memo[key] ?: load().also { memo[key] = it }

    private suspend fun get(url: String): Document = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("vimm.net returned HTTP ${res.code}")
            Jsoup.parse(res.body!!.string(), url)
        }
    }

    internal fun parseRows(doc: Document, system: String?): List<Game> =
        doc.select("tr").mapNotNull { tr ->
            val link = tr.select("a[href]").firstOrNull { idRe.containsMatchIn(it.absUrl("href")) }
                ?: return@mapNotNull null
            val id = idRe.find(link.absUrl("href"))!!.groupValues[1].toInt()
            val titleCell = link.closest("td") ?: return@mapNotNull null
            val first = tr.children().firstOrNull()
            // Search results carry the system name in a plain first column.
            val sys = if (first != null && first != titleCell && first.select("a,img").isEmpty())
                first.text().ifBlank { null } else system
            Game(
                id = id,
                title = link.text(),
                system = sys,
                regions = tr.select("img.flag").map { it.attr("title") }.filter { it.isNotBlank() },
                version = titleCell.nextElementSibling()?.nextElementSibling()?.text()?.ifBlank { null },
                rating = tr.selectFirst("a[href*=p=rating]")?.text()?.takeIf { it.toDoubleOrNull() != null },
                media = tr.selectFirst("img.media-icon")?.attr("title")?.ifBlank { null },
                tags = titleCell.select("b[title]").map { it.attr("title") }.filter { it.isNotBlank() },
            )
        }.distinctBy { it.id }
}
