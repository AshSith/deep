package com.dsh.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.ui.AppRoot
import com.dsh.companion.ui.theme.CompanionTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
            val primary = Color(themeColor)
            val light = primary.luminance() > 0.58f
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = light
                controller.isAppearanceLightNavigationBars = light
            }
            CompanionTheme(primary = primary) {
                AppRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppVisibility.foreground.value = true
    }

    override fun onPause() {
        AppVisibility.foreground.value = false
        super.onPause()
    }

    override fun onStop() {
        AppVisibility.foreground.value = false
        super.onStop()
    }

    companion object {
        const val EXTRA_OPEN_CHAT = "com.dsh.companion.OPEN_CHAT"
    }
}
