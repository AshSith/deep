package com.dsh.companion.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.data.GroupChat
import com.dsh.companion.ui.components.CharacterThumb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    vm: GroupViewModel,
    onOpen: (String) -> Unit,
) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val personas by vm.personas.collectAsStateWithLifecycle()
    val characters by vm.characters.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GroupChat?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("群聊") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建群聊") },
            )
        },
    ) { padding ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "还没有群聊。\n把几个角色拉到一个群里，让她们自己接话。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(groups, key = { it.id }) { group ->
                    val members = group.memberIds.mapNotNull { id -> personas.firstOrNull { it.id == id } }
                    Card(
                        onClick = { onOpen(group.id) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row {
                                members.take(3).forEach { persona ->
                                    val character = characters.firstOrNull { it.id == persona.characterId }
                                        ?: characters.firstOrNull()
                                    Box(Modifier.padding(end = 2.dp)) {
                                        CharacterThumb(character = character, size = 40.dp)
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                            ) {
                                Text(
                                    text = group.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${members.size} 个成员" +
                                        if (group.autoChat) " · 自动聊天" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deleteTarget = group }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除群聊")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateGroupDialog(
            personas = personas.map { it.name to it.id },
            onDismiss = { showCreate = false },
            onCreate = { title, memberIds, autoChat ->
                showCreate = false
                val group = vm.createGroup(title, memberIds, autoChat)
                onOpen(group.id)
            },
        )
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除群聊？") },
            text = { Text("「${target.title}」的聊天记录会一起删掉。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        vm.deleteGroup(target.id)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CreateGroupDialog(
    personas: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>, Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var autoChat by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建群聊") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("群名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "选成员（至少 2 个）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(personas, key = { it.second }) { (name, id) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (id in selected) selected - id else selected + id
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = id in selected,
                                onCheckedChange = {
                                    selected = if (id in selected) selected - id else selected + id
                                },
                            )
                            Text(name)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("我不在也可以聊", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "桌宠悬浮窗开着时才会自动聊",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoChat, onCheckedChange = { autoChat = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.size >= 2,
                onClick = {
                    onCreate(
                        title.trim().ifBlank { "新群聊" },
                        selected.toList(),
                        autoChat,
                    )
                },
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
