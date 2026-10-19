package com.dsh.companion.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsh.companion.MainViewModel
import com.dsh.companion.group.GroupChatScreen
import com.dsh.companion.group.GroupListScreen
import com.dsh.companion.group.GroupViewModel

private data class Tab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val tabs = listOf(
    Tab("chats", "消息", Icons.Default.Chat),
    Tab("groups", "群聊", Icons.Default.Group),
    Tab("personas", "角色", Icons.Default.Person),
    Tab("settings", "设置", Icons.Default.Settings),
)

@Composable
fun AppRoot(
    vm: MainViewModel,
    pendingConversation: String? = null,
    pendingGroup: String? = null,
    pendingPersona: String? = null,
    onPendingConsumed: () -> Unit = {},
) {
    val groupVm: GroupViewModel = viewModel()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    LaunchedEffect(pendingConversation, pendingGroup, pendingPersona) {
        val group = pendingGroup
        val conversation = pendingConversation
        val persona = pendingPersona
        if (group != null) {
            groupVm.openGroup(group)
            navController.navigate("group/$group") { launchSingleTop = true }
            onPendingConsumed()
        } else if (conversation != null) {
            vm.openConversation(conversation)
            navController.navigate("chat") { launchSingleTop = true }
            onPendingConsumed()
        } else if (persona != null) {
            vm.openLatestConversation(persona)
            navController.navigate("chat") { launchSingleTop = true }
            onPendingConsumed()
        }
    }

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
            startDestination = "chats",
            modifier = Modifier.padding(padding),
        ) {
            composable("chats") {
                ChatListScreen(
                    vm = vm,
                    groupVm = groupVm,
                    onOpenSingle = { navController.navigate("chat") },
                    onOpenGroup = { id -> navController.navigate("group/$id") },
                )
            }
            composable("chat") {
                ChatScreen(
                    vm = vm,
                    onPersonas = {
                        navController.navigate("personas") { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() },
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
                    onCharacters = { navController.navigate("characters") },
                )
            }
            composable("characters") {
                CharacterLibraryScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("appearance") {
                AppearanceScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("groups") {
                GroupListScreen(
                    vm = groupVm,
                    onOpen = { id -> navController.navigate("group/$id") },
                )
            }
            composable("group/{id}") { entry ->
                GroupChatScreen(
                    vm = groupVm,
                    groupId = entry.arguments?.getString("id") ?: "",
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
