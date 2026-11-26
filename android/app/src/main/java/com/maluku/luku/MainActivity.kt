package com.maluku.luku

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.maluku.luku.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AppRoot() }
    }

    /** Afstandsbediening tijdens een video: eerst naar de speler (anders blijft hij in het menu erachter hangen). */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        playerKeys?.let { if (it(event)) return true }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        var playerKeys: ((android.view.KeyEvent) -> Boolean)? = null
    }

    override fun onDestroy() {
        if (isFinishing) App.instance.torrents.stop()
        super.onDestroy()
    }
}
