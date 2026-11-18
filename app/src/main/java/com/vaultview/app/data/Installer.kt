package com.vaultview.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Gets emulators: downloads official APKs and hands them to Android, or opens the Play Store. */
object Installer {
    /** Per emulator id: progress 0..1 while downloading, or a message. */
    val progress = mutableStateMapOf<String, Float>()
    val messages = mutableStateMapOf<String, String>()

    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

    suspend fun install(context: Context, emulatorId: String) {
        val source = Emulators.sources[emulatorId] ?: run { messages[emulatorId] = "No download source known"; return }
        messages.remove(emulatorId)
        when (source) {
            is Emulators.Source.PlayStore -> openStore(context, source.pkg)
            is Emulators.Source.GitHub -> download(context, emulatorId) { latestGitHubApk(source) }
            Emulators.Source.RetroArchBuildbot -> download(context, emulatorId) {
                val tag = withContext(Dispatchers.IO) { githubJson("libretro/RetroArch").getString("tag_name").removePrefix("v") }
                "https://buildbot.libretro.com/stable/$tag/android/RetroArch_aarch64.apk"
            }
        }
    }

    private fun openStore(context: Context, pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(market) } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun githubJson(repo: String): JSONObject {
        val req = Request.Builder().url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("GitHub said HTTP ${r.code}")
            return JSONObject(r.body!!.string())
        }
    }

    private suspend fun latestGitHubApk(source: Emulators.Source.GitHub): String = withContext(Dispatchers.IO) {
        val assets = githubJson(source.repo).getJSONArray("assets")
        val byName = (0 until assets.length()).associate { assets.getJSONObject(it).let { a -> a.getString("name") to a.getString("browser_download_url") } }
        val name = source.pick(byName.keys.toList()) ?: throw IOException("No Android build in the latest release")
        byName.getValue(name)
    }

    private suspend fun download(context: Context, id: String, url: suspend () -> String) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            messages[id] = "Allow VaultView to install apps, then press Install again"
            context.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        progress[id] = 0f
        try {
            val link = url()
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "emu-$id.apk")
                http.newCall(Request.Builder().url(link).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code})")
                    val body = r.body!!; val total = body.contentLength()
                    out.outputStream().use { o ->
                        body.byteStream().use { i ->
                            val buf = ByteArray(1 shl 16); var done = 0L; var last = 0L
                            while (true) {
                                val n = i.read(buf); if (n < 0) break
                                o.write(buf, 0, n); done += n
                                val now = System.currentTimeMillis()
                                if (total > 0 && now - last > 200) { last = now; withContext(Dispatchers.Main) { progress[id] = done.toFloat() / total } }
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
            messages[id] = "Finish installing in Android's prompt"
        } catch (e: Exception) {
            messages[id] = e.message ?: "Couldn't download"
        } finally {
            progress.remove(id)
        }
    }
}
