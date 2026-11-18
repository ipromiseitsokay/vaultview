package com.vaultview.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vaultview.app.ui.VaultRoot
import com.vaultview.app.ui.VaultTheme

class MainActivity : ComponentActivity() {
    /**
     * Controller sounds. Android turns an unhandled A into DPAD_CENTER and B into BACK,
     * so only those are listened for, which keeps one press to one sound.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> Sfx.key(Sfx.Cue.MOVE)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                    if (event.repeatCount == 0) Sfx.key(Sfx.Cue.SELECT)
                KeyEvent.KEYCODE_BACK -> if (event.repeatCount == 0) Sfx.key(Sfx.Cue.BACK)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VaultTheme { VaultRoot() } }
    }
}
