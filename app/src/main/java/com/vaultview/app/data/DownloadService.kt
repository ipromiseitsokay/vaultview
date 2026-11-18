package com.vaultview.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.documentfile.provider.DocumentFile
import com.vaultview.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/** Runs queued downloads one at a time in the foreground, then saves or unpacks them. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastNotify = 0L
    private var worker: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VaultView:download")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification("Starting download…", null), type)
        if (worker?.isActive != true) worker = scope.launch { runQueue() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private suspend fun runQueue() {
        wakeLock.acquire(6 * 60 * 60 * 1000L)
        try {
            while (true) {
                val item = withContext(Dispatchers.Main) { Downloads.nextQueued() } ?: break
                process(item)
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            withContext(Dispatchers.Main) {
                Downloads.persist()
                stopForeground(STOP_FOREGROUND_DETACH)
                val done = Downloads.items.firstOrNull()
                if (done != null && !done.active) showFinished(done)
                stopSelf()
            }
        }
    }

    private suspend fun set(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    private suspend fun process(item: DownloadItem) {
        val req = item.request ?: return
        var temp: File? = null
        var partial: DocumentFile? = null
        try {
            set { item.state = DownloadState.DOWNLOADING; item.message = null }
            // GET forms send their fields in the URL, like a browser does.
            val url = if (req.method.equals("GET", true) && req.form.isNotEmpty()) {
                req.url.toHttpUrl().newBuilder().apply { req.form.forEach { (k, v) -> setQueryParameter(k, v) } }.build()
            } else req.url.toHttpUrl()
            val http = Request.Builder().url(url)
                .header("User-Agent", req.userAgent)
                .header("Referer", req.referer)
                .header("Accept", "*/*")
                .apply { CookieManager.getInstance().getCookie(url.toString())?.let { header("Cookie", it) } }
                .apply {
                    if (req.method.equals("POST", true)) {
                        post(FormBody.Builder().apply { req.form.forEach { (k, v) -> add(k, v) } }.build())
                    }
                }
                .build()

            client.newCall(http).execute().use { res ->
                if (!res.isSuccessful) throw IOException(
                    if (res.code in 400..499) "The site refused the download (HTTP ${res.code}). Open the game again and retry."
                    else "Server error (HTTP ${res.code}). Try again in a bit."
                )
                val body = res.body ?: throw IOException("Empty response")
                if (body.contentType()?.subtype == "html") {
                    throw IOException("Got a web page instead of a file. Open the game again and retry.")
                }
                val name = fileNameFrom(res.header("Content-Disposition"), res.request.url.pathSegments.lastOrNull(), item.title)
                val total = body.contentLength()
                set { item.fileName = name; item.total = total }

                val root = targetRoot()
                val dir = if (Settings.systemFolders && item.system != null) root.dir(item.system!!.folder) else root
                val extract = Settings.autoExtract && Extractor.canExtract(name)

                val out: OutputStream = if (extract) {
                    temp = File(externalCacheDir ?: cacheDir, "dl/${item.key}-$name").also { it.parentFile?.mkdirs() }
                    temp!!.outputStream()
                } else {
                    partial = dir.newFile(name)
                    dir.open(partial!!)
                }

                out.use { o ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(1 shl 16)
                        var done = 0L
                        var lastUi = 0L
                        var lastBytes = 0L
                        while (true) {
                            if (item.cancelRequested) throw InterruptedException()
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastUi > 500) {
                                val rate = if (lastUi > 0) (done - lastBytes) * 1000 / (now - lastUi) else 0L
                                lastBytes = done
                                lastUi = now
                                set { item.speed = if (item.speed == 0L) rate else (item.speed * 7 + rate * 3) / 10 }
                                val p = if (total > 0) done.toFloat() / total else 0f
                                set { item.bytes = done; item.progress = p }
                                if (now - lastNotify > 1500) { lastNotify = now; notify(item.title, p.takeIf { total > 0 }) }
                            }
                        }
                        set { item.bytes = done }
                    }
                }

                if (extract) {
                    set { item.state = DownloadState.EXTRACTING; item.progress = 0f }
                    notify("Unpacking ${item.title}", null)
                    var lastUi = 0L
                    val files = Extractor.extract(temp!!, { path -> dir.createPath(path) },
                        onProgress = { p ->
                            val now = System.currentTimeMillis()
                            if (now - lastUi > 500) { lastUi = now; scope.launch(Dispatchers.Main) { item.progress = p } }
                        },
                        isCancelled = { item.cancelRequested })
                    set { item.message = "${files.size} file${if (files.size == 1) "" else "s"} unpacked"; item.files = files }
                }

                set {
                    item.state = DownloadState.DONE
                    item.progress = 1f
                    item.finishedAt = System.currentTimeMillis()
                    com.vaultview.app.Sfx.play(com.vaultview.app.Sfx.Cue.SUCCESS)
                    item.savedTo = listOfNotNull(Settings.folderLabel(), if (dir !== root) item.system?.folder else null).joinToString("/")
                    Downloads.persist()
                }
                // Frontends (ES-DE, iiSU): add this game's entry and art.
                runCatching { Frontends.exportDownload(this@DownloadService, item) }
            }
        } catch (e: InterruptedException) {
            partial?.delete()
            set { item.state = DownloadState.CANCELLED; item.message = null }
        } catch (e: Exception) {
            partial?.delete()
            set {
                item.state = DownloadState.FAILED; item.message = e.message ?: e.javaClass.simpleName
                com.vaultview.app.Sfx.play(com.vaultview.app.Sfx.Cue.ERROR)
            }
        } finally {
            temp?.delete()
        }
    }

    // ---- Where files go -------------------------------------------------------------------

    private interface Dir {
        fun dir(name: String): Dir
        fun newFile(name: String): DocumentFile
        fun open(file: DocumentFile): OutputStream
        fun createPath(path: String): OutputStream
    }

    private inner class SafDir(val doc: DocumentFile) : Dir {
        override fun dir(name: String): Dir =
            SafDir(doc.findFile(name)?.takeIf { it.isDirectory } ?: doc.createDirectory(name)
                ?: throw IOException("Couldn't create folder $name"))

        override fun newFile(name: String): DocumentFile {
            doc.findFile(name)?.delete()
            return doc.createFile("application/octet-stream", name) ?: throw IOException("Couldn't create $name")
        }

        override fun open(file: DocumentFile): OutputStream =
            contentResolver.openOutputStream(file.uri, "w") ?: throw IOException("Couldn't open ${file.name}")

        override fun createPath(path: String): OutputStream {
            val parts = path.split('/')
            var d: Dir = this
            for (p in parts.dropLast(1)) d = d.dir(p)
            val f = d.newFile(parts.last())
            return d.open(f)
        }
    }

    private fun targetRoot(): Dir {
        val uri = Settings.folderUri ?: throw IOException("Pick a download folder in Settings first")
        val doc = DocumentFile.fromTreeUri(this, Uri.parse(uri))
        if (doc == null || !doc.canWrite()) throw IOException("Can't write to the download folder. Pick it again in Settings.")
        return SafDir(doc)
    }

    // ---- Notifications ----------------------------------------------------------------------

    private fun openApp() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

    private fun notification(text: String, progress: Float?): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.vaultview.app.R.drawable.ic_stat_download)
            .setContentTitle("Downloading")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .apply { if (progress != null) setProgress(1000, (progress * 1000).toInt(), false) else setProgress(0, 0, true) }
            .build()

    private fun notify(text: String, progress: Float?) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text, progress))
    }

    private fun showFinished(item: DownloadItem) {
        val ok = item.state == DownloadState.DONE
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (ok) "Download finished" else "Download stopped")
            .setContentText(if (ok) "${item.title} → ${item.savedTo}" else "${item.title}: ${item.message ?: item.state.name.lowercase()}")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 42

        /** Picks a file name from Content-Disposition, then the URL, then the title. */
        fun fileNameFrom(disposition: String?, urlName: String?, title: String): String {
            val fromHeader = disposition?.let { d ->
                Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)""").find(d)?.groupValues?.get(1)?.let { Uri.decode(it) }
                    ?: Regex("""filename\s*=\s*"([^"]+)"""").find(d)?.groupValues?.get(1)
                    ?: Regex("""filename\s*=\s*([^;]+)""").find(d)?.groupValues?.get(1)?.trim()
            }
            val raw = fromHeader ?: urlName?.takeIf { it.contains('.') } ?: "$title.bin"
            return raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        }
    }
}
