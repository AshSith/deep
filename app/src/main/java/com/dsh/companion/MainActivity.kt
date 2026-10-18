package com.dsh.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dsh.companion.ui.AppRoot
import com.dsh.companion.ui.theme.CompanionTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompanionTheme {
                AppRoot(viewModel)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_CHAT = "com.dsh.companion.OPEN_CHAT"
    }
}
