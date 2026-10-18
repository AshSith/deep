package com.dsh.companion.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dsh.companion.MainViewModel

private data class Tab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val tabs = listOf(
    Tab("chat", "聊天", Icons.Default.Chat),
    Tab("personas", "角色", Icons.Default.Person),
    Tab("settings", "设置", Icons.Default.Settings),
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "chat",
            modifier = Modifier.padding(padding),
        ) {
            composable("chat") {
                ChatScreen(
                    vm = vm,
                    onPersonas = {
                        navController.navigate("personas") { launchSingleTop = true }
                    },
                )
            }
            composable("personas") {
                PersonasScreen(
                    vm = vm,
                    onEdit = { id -> navController.navigate("persona/$id") },
                    onNew = { navController.navigate("persona/new") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    onAppearance = { navController.navigate("appearance") },
                )
            }
            composable("appearance") {
                AppearanceScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("persona/{id}") { entry ->
                PersonaEditScreen(
                    vm = vm,
                    personaId = entry.arguments?.getString("id") ?: "new",
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
