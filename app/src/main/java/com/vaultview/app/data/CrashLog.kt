package com.vaultview.app.data

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last crash on the device so testers can send it (Settings shows it, and the next launch
 * offers to share it). Also remembers whether the second-screen deck was up, so a dual-screen crash
 * can switch the deck off instead of crashing again.
 */
object CrashLog {
    private lateinit var file: File
    @Volatile var deckShowing = false

    fun install(context: Context) {
        file = File(context.filesDir, "last-crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { file.writeText(report(context, thread, e)) }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun report(context: Context, thread: Thread, e: Throwable): String {
        val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val displays = runCatching {
            context.getSystemService(DisplayManager::class.java).displays.joinToString { "#${it.displayId} ${it.name} ${it.mode.physicalWidth}x${it.mode.physicalHeight} flags=${it.flags}" }
        }.getOrDefault("?")
        return buildString {
            appendLine("VaultView $version crash report")
            appendLine("Time: ${java.util.Date()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
            appendLine("Displays: $displays")
            appendLine("Second-screen deck showing: $deckShowing")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
    }

    /** The last crash report, if the app crashed since it was last dismissed. */
    fun pending(): String? = if (::file.isInitialized && file.exists()) runCatching { file.readText() }.getOrNull() else null
    fun clear() { if (::file.isInitialized) file.delete() }
    fun crashedWithDeck(): Boolean = pending()?.contains("Second-screen deck showing: true") == true
}
