package com.dsh.companion.group

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.data.ChatMessage
import com.dsh.companion.data.GroupChat
import com.dsh.companion.ui.ChatAttachment
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
    val groupUserColor by vm.groupUserBubbleColor.collectAsStateWithLifecycle()
    val groupAssistantColor by vm.groupAssistantBubbleColor.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.sendAttachment(uri, "image")
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.sendAttachment(uri, "video")
    }

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
                        knownNames = personas.map { it.name },
                        userBubbleColor = groupUserColor,
                        assistantBubbleColor = groupAssistantColor,
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
                Box {
                    IconButton(onClick = { showAttach = true }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "添加附件")
                    }
                    DropdownMenu(expanded = showAttach, onDismissRequest = { showAttach = false }) {
                        DropdownMenuItem(
                            text = { Text("图片") },
                            onClick = {
                                showAttach = false
                                imagePicker.launch(arrayOf("image/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("视频") },
                            leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                            onClick = {
                                showAttach = false
                                videoPicker.launch(arrayOf("video/*"))
                            },
                        )
                    }
                }
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
    knownNames: List<String>,
    userBubbleColor: Int,
    assistantBubbleColor: Int,
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
            verticalAlignment = Alignment.Top,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "我",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Surface(
                    color = if (userBubbleColor != 0) Color(userBubbleColor) else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 5.dp,
                    ),
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        message.attachmentUri?.let { uri ->
                            ChatAttachment(
                                uri = uri,
                                type = message.attachmentType,
                                name = message.attachmentName,
                            )
                        }
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = if (userBubbleColor != 0) {
                                if (Color(userBubbleColor).luminance() > 0.55f) Color.Black else Color.White
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Box(Modifier.width(6.dp))
            ChatAvatar(uri = null, character = null, name = "我", size = 36.dp)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
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
                    color = when {
                        message.error -> MaterialTheme.colorScheme.errorContainer
                        assistantBubbleColor != 0 -> Color(assistantBubbleColor)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 5.dp,
                        bottomEnd = 18.dp,
                    ),
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        message.attachmentUri?.let { uri ->
                            ChatAttachment(
                                uri = uri,
                                type = message.attachmentType,
                                name = message.attachmentName,
                            )
                        }
                        Text(
                            text = cleanGroupText(message.content, knownNames),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = when {
                                message.error -> MaterialTheme.colorScheme.onErrorContainer
                                assistantBubbleColor != 0 ->
                                    if (Color(assistantBubbleColor).luminance() > 0.55f) Color.Black else Color.White
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun cleanGroupText(content: String, names: List<String>): String {
    var text = content.trim()
    val sorted = names.filter { it.isNotBlank() }.sortedByDescending { it.length }
    var changed = true
    var guard = 0
    while (changed && guard < 8) {
        changed = false
        guard++
        for (name in sorted) {
            for (sep in listOf("：", ":")) {
                val prefix = "$name$sep"
                if (text.startsWith(prefix)) {
                    text = text.removePrefix(prefix).trim()
                    changed = true
                }
            }
        }
    }
    return text.trim()
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
