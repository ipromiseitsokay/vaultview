package com.vaultview.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vaultview.app.ui.VaultRoot
import com.vaultview.app.ui.VaultTheme
import kotlin.math.abs

/** Whether the person is driving the app with buttons (true) or touch (false). */
object Controls {
    var usingController by mutableStateOf(false)
    /** Counts presses of Start; the app opens your last game when it changes. */
    var startPresses by mutableStateOf(0)
}

class MainActivity : ComponentActivity() {
    private val main = Handler(Looper.getMainLooper())
    private val lastMove = LongArray(4)          // per direction, when we last moved
    private var held = 0                         // direction keycode held on the stick/hat
    private var repeater: Runnable? = null
    // Pacing for held directions: one move per drawn frame, never a backlog, and a stop at the end.
    private var framePending = false
    private var lastRepeatAt = 0L
    private var edge = 0                          // direction that just ran into the end of a list
    private val frameDone = android.view.Choreographer.FrameCallback { framePending = false }

    /** Sends one move to the UI. Returns false when nothing could take focus that way (an edge). */
    private fun step(down: KeyEvent): Boolean {
        framePending = true
        android.view.Choreographer.getInstance().postFrameCallback(frameDone)
        val moved = super.dispatchKeyEvent(down)
        if (moved) Sfx.key(Sfx.Cue.MOVE) else edge = down.keyCode
        return moved
    }
    private var lastPress = 0L                  // last A-type or B-type press
    private var lastPressKind = 0               // 1 = select, 2 = back
    private var lastPressCode = 0
    private val swallowUp = mutableSetOf<Int>() // releases of presses we dropped

    companion object {
        /** The top-screen activity, for the bottom-screen deck to hand stray button presses to. */
        var current: MainActivity? = null
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        enableEdgeToEdge()
        setContent { VaultTheme { VaultRoot() } }
        // The app plays its own sounds; turn off Android's clicks so nothing doubles.
        window.decorView.isSoundEffectsEnabled = false
        findViewById<ViewGroup>(android.R.id.content)?.let { root ->
            root.isSoundEffectsEnabled = false
            root.post { for (i in 0 until root.childCount) root.getChildAt(i).isSoundEffectsEnabled = false }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) Controls.usingController = false
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) {
            Controls.usingController = true
        }
        if (code == KeyEvent.KEYCODE_BUTTON_START) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) { Controls.startPresses++; Sfx.key(Sfx.Cue.SELECT) }
            return true
        }
        if (dirIndex(code) >= 0) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val now = SystemClock.uptimeMillis()
                if (event.repeatCount == 0) {
                    // Many handhelds report the D-pad both as keys and as a hat axis; keep only one.
                    if (recentlyMoved(code)) return true
                    edge = 0
                } else {
                    // Held D-pad: Android repeats fast. Drop repeats (never queue them) while the last
                    // move is still being drawn, pace them like the stick, and stop at the end of a list.
                    if (code == edge || framePending || now - lastRepeatAt < 110) return true
                    lastRepeatAt = now
                }
                markMoved(code)
                step(event)
                return true
            }
            if (event.action == KeyEvent.ACTION_UP && code == edge) edge = 0
            return super.dispatchKeyEvent(event)
        }
        // Gamepad A and B are turned into select and back here, exactly once per press.
        // Some handhelds also send their own select/back next to A/B; that twin is dropped.
        val kind = when (code) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 1
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> 2
            else -> 0
        }
        if (kind != 0) {
            val gamepad = code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_BUTTON_B
            val mapped = when (code) {
                KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
                else -> code
            }
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) return if (gamepad) true else super.dispatchKeyEvent(event)
                    val now = SystemClock.uptimeMillis()
                    if (kind == lastPressKind && code != lastPressCode && now - lastPress < 180) {
                        swallowUp += code
                        return true
                    }
                    lastPress = now; lastPressKind = kind; lastPressCode = code
                    Sfx.key(if (kind == 1) Sfx.Cue.SELECT else Sfx.Cue.BACK)
                    if (gamepad) {
                        super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, KeyEvent.ACTION_DOWN, mapped, 0))
                        return true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (swallowUp.remove(code)) return true
                    if (gamepad) {
                        super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, KeyEvent.ACTION_UP, mapped, 0))
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** D-pad hat and left stick, handled here so Android doesn't invent extra key presses. */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.isFromSource(InputDevice.SOURCE_JOYSTICK) && ev.actionMasked == MotionEvent.ACTION_MOVE) {
            Controls.usingController = true
            val hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val sx = ev.getAxisValue(MotionEvent.AXIS_X)
            val sy = ev.getAxisValue(MotionEvent.AXIS_Y)
            val x = if (abs(hx) > 0.5f) hx else sx
            val y = if (abs(hy) > 0.5f) hy else sy
            // Press at 0.6, release below 0.35, so a resting stick never jitters.
            val keep = when (held) {
                KeyEvent.KEYCODE_DPAD_LEFT -> x < -0.35f
                KeyEvent.KEYCODE_DPAD_RIGHT -> x > 0.35f
                KeyEvent.KEYCODE_DPAD_UP -> y < -0.35f
                KeyEvent.KEYCODE_DPAD_DOWN -> y > 0.35f
                else -> false
            }
            val dir = when {
                keep -> held
                abs(x) >= abs(y) && abs(x) > 0.6f -> if (x > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                abs(y) > 0.6f -> if (y > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
                else -> 0
            }
            if (dir != held) {
                stopRepeat()
                held = dir
                if (dir != 0) {
                    move(dir, fresh = true)
                    startRepeat(dir)
                }
            }
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun move(code: Int, fresh: Boolean) {
        if (fresh) { if (recentlyMoved(code)) return; edge = 0 }
        else if (code == edge || framePending) return
        markMoved(code)
        val t = SystemClock.uptimeMillis()
        val moved = step(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        super.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        if (!moved) stopRepeat()
    }

    private fun startRepeat(code: Int) {
        var delay = 380L
        val r = object : Runnable {
            override fun run() {
                if (held != code) return
                move(code, fresh = false)
                delay = 110L
                main.postDelayed(this, delay)
            }
        }
        repeater = r
        main.postDelayed(r, delay)
    }

    private fun stopRepeat() { repeater?.let { main.removeCallbacks(it) }; repeater = null }

    private fun dirIndex(code: Int) = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> 0; KeyEvent.KEYCODE_DPAD_DOWN -> 1
        KeyEvent.KEYCODE_DPAD_LEFT -> 2; KeyEvent.KEYCODE_DPAD_RIGHT -> 3
        else -> -1
    }
    private fun recentlyMoved(code: Int) = SystemClock.uptimeMillis() - lastMove[dirIndex(code)] < 90
    private fun markMoved(code: Int) { lastMove[dirIndex(code)] = SystemClock.uptimeMillis() }

    override fun onPause() { stopRepeat(); held = 0; super.onPause() }

    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }

    override fun onStart() {
        super.onStart()
        // Dual-screen handhelds: put the buttons and downloads on the bottom screen.
        com.vaultview.app.ui.DeckController.show(this)
    }

    override fun onStop() {
        // Leaving the app (or starting a game): give the bottom screen back, e.g. to a DS emulator.
        if (!isChangingConfigurations) com.vaultview.app.ui.DeckController.hide()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Back from an emulator: count the time played.
        com.vaultview.app.data.Playtime.finish()
        // Might be back from installing an emulator.
        com.vaultview.app.data.Emulators.refresh()
    }
}
