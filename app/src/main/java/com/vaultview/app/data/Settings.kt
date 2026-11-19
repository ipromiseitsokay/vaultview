package com.vaultview.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User preferences, kept on the device. */
object Settings {
    private lateinit var prefs: SharedPreferences

    /** SAF tree URI of the folder chosen for downloads, or null if none picked yet. */
    var folderUri by mutableStateOf<String?>(null); private set
    var autoExtract by mutableStateOf(true); private set
    var systemFolders by mutableStateOf(true); private set
    var soundTheme by mutableStateOf("crisp"); private set
    var soundVolume by mutableStateOf(0.6f); private set
    /** Use a second screen (AYN Thor's bottom screen) for buttons and downloads. */
    var secondScreen by mutableStateOf(true); private set
    /** Hide the status and navigation bars. */
    var fullscreen by mutableStateOf(true); private set
    /** Write ES-DE gamelists and art for new downloads (read by ES-DE and iiSU). */
    var frontendExport by mutableStateOf(false); private set
    /** SAF tree URI of the ES-DE folder. */
    var esdeUri by mutableStateOf<String?>(null); private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        folderUri = prefs.getString("folder", null)
        autoExtract = prefs.getBoolean("autoExtract", true)
        systemFolders = prefs.getBoolean("systemFolders", true)
        soundTheme = prefs.getString("soundTheme", "crisp") ?: "crisp"
        soundVolume = prefs.getFloat("soundVolume", 0.6f)
        secondScreen = prefs.getBoolean("secondScreen", true)
        fullscreen = prefs.getBoolean("fullscreen", true)
        frontendExport = prefs.getBoolean("frontendExport", false)
        esdeUri = prefs.getString("esde", null)
    }

    fun setFolder(context: Context, uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Some pickers hand back folders without a lasting grant; keep going for this session anyway.
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        folderUri = uri.toString()
        prefs.edit().putString("folder", folderUri).apply()
    }

    fun updateAutoExtract(v: Boolean) { autoExtract = v; prefs.edit().putBoolean("autoExtract", v).apply() }
    fun updateSoundTheme(v: String) { soundTheme = v; prefs.edit().putString("soundTheme", v).apply() }
    fun updateSoundVolume(v: Float) { soundVolume = v; prefs.edit().putFloat("soundVolume", v).apply() }
    fun updateFrontendExport(v: Boolean) { frontendExport = v; prefs.edit().putBoolean("frontendExport", v).apply() }
    fun setEsdeFolder(context: Context, uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        esdeUri = uri.toString()
        prefs.edit().putString("esde", esdeUri).apply()
    }
    fun updateFullscreen(v: Boolean) { fullscreen = v; prefs.edit().putBoolean("fullscreen", v).apply() }
    fun updateSecondScreen(v: Boolean) { secondScreen = v; prefs.edit().putBoolean("secondScreen", v).apply() }
    fun updateSystemFolders(v: Boolean) { systemFolders = v; prefs.edit().putBoolean("systemFolders", v).apply() }

    /** Human-readable folder, e.g. "Internal storage/ROMs" or "SD card/ROMs". */
    fun folderLabel(): String? = folderUri?.let { u ->
        val docId = Uri.decode(Uri.parse(u).lastPathSegment ?: return@let null) // e.g. "primary:ROMs"
        val (vol, path) = docId.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val volume = if (vol == "primary") "Internal storage" else "SD card"
        if (path.isEmpty()) volume else "$volume/$path"
    }
}
