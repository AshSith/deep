package com.dsh.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.ui.AppRoot
import com.dsh.companion.ui.theme.CompanionTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var pendingConversation by mutableStateOf<String?>(null)
    private var pendingGroup by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
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
                AppRoot(
                    vm = viewModel,
                    pendingConversation = pendingConversation,
                    pendingGroup = pendingGroup,
                    onPendingConsumed = {
                        pendingConversation = null
                        pendingGroup = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_OPEN_CONVERSATION)?.let { pendingConversation = it }
        intent.getStringExtra(EXTRA_OPEN_GROUP)?.let { pendingGroup = it }
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
        const val EXTRA_OPEN_CONVERSATION = "com.dsh.companion.OPEN_CONVERSATION"
        const val EXTRA_OPEN_GROUP = "com.dsh.companion.OPEN_GROUP"
        const val EXTRA_OPEN_PERSONA = "com.dsh.companion.OPEN_PERSONA"
    }
}
