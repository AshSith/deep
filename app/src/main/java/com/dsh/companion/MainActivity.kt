package com.dsh.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
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
            CompanionTheme(primary = Color(themeColor)) {
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

    companion object {
        const val EXTRA_OPEN_CHAT = "com.dsh.companion.OPEN_CHAT"
    }
}
