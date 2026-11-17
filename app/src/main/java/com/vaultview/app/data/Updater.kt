package com.vaultview.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.vaultview.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Checks the app's GitHub repo for a newer release and installs it. */
object Updater {
    data class Release(val versionCode: Int, val name: String, val apkUrl: String, val notes: String)

    var available by mutableStateOf<Release?>(null); private set
    var status by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set

    val repo: String get() = BuildConfig.UPDATE_REPO
    val currentVersion: String get() = BuildConfig.VERSION_NAME
    private val client = OkHttpClient()

    /** Version code is the last number in the tag, e.g. "v1.0.17" → 17. */
    fun versionFromTag(tag: String): Int? = Regex("""(\d+)\D*$""").find(tag)?.groupValues?.get(1)?.toIntOrNull()

    suspend fun check(quiet: Boolean = false) {
        if (repo.isBlank()) { if (!quiet) status = "Updates aren't set up in this build"; return }
        busy = true
        if (!quiet) status = "Checking…"
        try {
            val rel = withContext(Dispatchers.IO) {
                val req = Request.Builder().url("https://api.github.com/repos/$repo/releases/latest")
                    .header("Accept", "application/vnd.github+json").build()
                client.newCall(req).execute().use { res ->
                    if (res.code == 404) return@use null
                    if (!res.isSuccessful) throw IOException("GitHub said HTTP ${res.code}")
                    val o = JSONObject(res.body!!.string())
                    val assets = o.getJSONArray("assets")
                    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@use null
                    Release(
                        versionCode = versionFromTag(o.getString("tag_name")) ?: return@use null,
                        name = o.optString("name").ifBlank { o.getString("tag_name") },
                        apkUrl = apk.getString("browser_download_url"),
                        notes = o.optString("body"),
                    )
                }
            }
            available = rel?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
            status = when {
                available != null -> "${available!!.name} is available"
                quiet -> null
                else -> "You're on the latest version"
            }
        } catch (e: Exception) {
            if (!quiet) status = "Couldn't check: ${e.message}"
        } finally {
            busy = false
        }
    }

    /** Downloads the APK and hands it to Android's installer. */
    suspend fun install(context: Context) {
        val rel = available ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            status = "Allow VaultView to install updates, then press Install again"
            context.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        busy = true
        status = "Downloading update…"
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val out = File(dir, "VaultView-${rel.versionCode}.apk")
                client.newCall(Request.Builder().url(rel.apkUrl).build()).execute().use { res ->
                    if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                    val body = res.body!!
                    val total = body.contentLength()
                    out.outputStream().use { o ->
                        body.byteStream().use { i ->
                            val buf = ByteArray(1 shl 16); var done = 0L
                            while (true) {
                                val n = i.read(buf); if (n < 0) break
                                o.write(buf, 0, n); done += n
                                if (total > 0) progress = done.toFloat() / total
                            }
                        }
                    }
                }
                out
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            status = "Follow Android's prompt to finish updating"
        } catch (e: Exception) {
            status = "Update failed: ${e.message}"
        } finally {
            busy = false
        }
    }
}
