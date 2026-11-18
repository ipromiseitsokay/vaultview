package com.vaultview.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import com.vaultview.app.data.Settings

/**
 * Short UI sounds from one family at a time, so they stay coherent.
 * Sounds come from "UI Utility Pack Vol 1" (sets 11 and 20), trimmed and level-matched.
 */
object Sfx {
    enum class Cue { MOVE, SELECT, BACK, SUCCESS, ERROR }

    val themes = listOf("off" to "Off", "crisp" to "Crisp", "soft" to "Soft")

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    private var lastMove = 0L
    private var lastKey = 0L

    /** Called for controller presses (they already made their sound). */
    fun key(cue: Cue) { lastKey = SystemClock.uptimeMillis(); play(cue) }

    /** For touch actions: stays quiet if a controller press just played. */
    fun tap(cue: Cue) { if (SystemClock.uptimeMillis() - lastKey > 250) play(cue) }

    fun init(context: Context) {
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
        val res = mapOf(
            "crisp_move" to R.raw.sfx_crisp_move, "crisp_select" to R.raw.sfx_crisp_select,
            "crisp_back" to R.raw.sfx_crisp_back, "crisp_success" to R.raw.sfx_crisp_success,
            "crisp_error" to R.raw.sfx_crisp_error,
            "soft_move" to R.raw.sfx_soft_move, "soft_select" to R.raw.sfx_soft_select,
            "soft_back" to R.raw.sfx_soft_back, "soft_success" to R.raw.sfx_soft_success,
            "soft_error" to R.raw.sfx_soft_error,
        )
        res.forEach { (k, r) -> ids[k] = p.load(context, r, 1) }
        pool = p
    }

    fun play(cue: Cue, theme: String = Settings.soundTheme) {
        if (theme == "off") return
        val now = SystemClock.uptimeMillis()
        if (cue == Cue.MOVE) {
            // Holding the D-pad repeats fast; keep ticks from piling up.
            if (now - lastMove < 70) return
            lastMove = now
        }
        val id = ids["${theme}_${cue.name.lowercase()}"] ?: return
        val v = Settings.soundVolume
        pool?.play(id, v, v, if (cue == Cue.MOVE) 0 else 1, 0, 1f)
    }
}
