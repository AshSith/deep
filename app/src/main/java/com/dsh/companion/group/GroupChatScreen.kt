package com.dsh.companion.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.data.ChatMessage
import com.dsh.companion.data.GroupChat
import com.dsh.companion.ui.components.CharacterThumb
import com.dsh.companion.ui.components.ChatAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    vm: GroupViewModel,
    groupId: String,
    onBack: () -> Unit,
) {
    val group by vm.activeGroup.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val personas by vm.personas.collectAsStateWithLifecycle()
    val characters by vm.characters.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(groupId) {
        vm.openGroup(groupId)
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val current = group
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("群聊不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val members = current.memberIds.mapNotNull { id -> personas.firstOrNull { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${members.size} 个成员" +
                                if (current.autoChat) " · 自动聊天" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "群设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "群聊开始了。\n@某个成员她会先回，不 @ 就随机一个人接话。",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                items(messages, key = { it.id }) { message ->
                    GroupMessageRow(
                        message = message,
                        personas = members,
                        characters = characters,
                    )
                }

                if (sending) {
                    item {
                        Text(
                            text = "有人在打字…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text("在群里说点什么…（@名字 指定谁回）") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                )
                FilledIconButton(
                    enabled = input.isNotBlank() && !sending,
                    onClick = {
                        val text = input.trim()
                        input = ""
                        vm.send(text)
                    },
                ) {
                    Icon(Icons.Default.Send, contentDescription = "发送")
                }
            }
        }
    }

    if (showSettings) {
        GroupSettingsDialog(
            group = current,
            members = members.map { it.name },
            onDismiss = { showSettings = false },
            onAutoChat = { vm.updateAutoChat(it) },
            onDelete = {
                showSettings = false
                vm.deleteGroup(current.id)
                onBack()
            },
        )
    }
}

@Composable
private fun GroupMessageRow(
    message: ChatMessage,
    personas: List<com.dsh.companion.data.Persona>,
    characters: List<com.dsh.companion.data.Character>,
) {
    val isUser = message.role == ChatMessage.ROLE_USER
    val persona = personas.firstOrNull { it.id == message.personaId }
    val character = persona?.let { p ->
        characters.firstOrNull { it.id == p.characterId }
    } ?: characters.firstOrNull()

    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box(Modifier.width(6.dp))
            ChatAvatar(uri = null, character = null, name = "我", size = 36.dp)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            ChatAvatar(
                uri = persona?.avatarUri,
                character = character,
                name = persona?.name ?: message.senderName ?: "成员",
                size = 36.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) {
                Text(
                    text = message.senderName ?: persona?.name ?: "成员",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    color = if (message.error) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = if (message.error) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupSettingsDialog(
    group: GroupChat,
    members: List<String>,
    onDismiss: () -> Unit,
    onAutoChat: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("群设置") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("我不在也可以聊", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "桌宠悬浮窗开着时才生效",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = group.autoChat,
                        onCheckedChange = onAutoChat,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("成员", style = MaterialTheme.typography.titleSmall)
                members.forEach { name ->
                    Text(
                        text = "· $name",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                TextButton(onClick = onDelete) { Text("删除群聊") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
