package com.dsh.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.MainViewModel
import com.dsh.companion.data.ChatMessage
import com.dsh.companion.ui.components.CharacterStage
import com.dsh.companion.ui.components.CharacterThumb
import com.dsh.companion.ui.components.ChatAvatar
import com.dsh.companion.voice.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: MainViewModel,
    onPersonas: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val persona by vm.activePersona.collectAsStateWithLifecycle()
    val character by vm.activeCharacter.collectAsStateWithLifecycle()
    val userAvatar by vm.userAvatarUri.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()
    val userBubbleColor by vm.chatUserBubbleColor.collectAsStateWithLifecycle()
    val assistantBubbleColor by vm.chatAssistantBubbleColor.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    val bgColor by vm.chatBackgroundColor.collectAsStateWithLifecycle()
    val bgImage by vm.chatBackgroundImage.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val voiceMessage by vm.voiceMessage.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val activeConversationId by vm.activeConversationId.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val beginVoice: () -> Unit = {
        vm.beginVoiceInput(
            onPartial = { input = it },
            onFinal = { text ->
                input = ""
                vm.send(text)
            },
            onError = { message ->
                scope.launch { snackbar.showSnackbar(message) }
            },
        )
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginVoice()
        } else {
            scope.launch { snackbar.showSnackbar("需要麦克风权限才能语音输入") }
        }
    }

    var showAttach by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var recordingVoice by remember { mutableStateOf(false) }
    val audioRecorder = remember { AudioRecorder(context) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.sendAttachment(uri, "image")
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.sendAttachment(uri, "video")
    }

    val send: () -> Unit = {
        val text = input.trim()
        if (text.isNotEmpty()) {
            input = ""
            vm.send(text)
        }
    }

    LaunchedEffect(partial) {
        if (listening && partial.isNotBlank()) input = partial
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.clearNotice()
        }
    }

    LaunchedEffect(voiceMessage) {
        voiceMessage?.let { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, streaming) {
        if (messages.isNotEmpty()) {
            if (streaming) {
                listState.scrollToItem(messages.lastIndex)
            } else {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ChatBackground(color = bgColor, imageUri = bgImage)

        Column(Modifier.fillMaxSize().imePadding()) {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回消息列表")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CharacterThumb(character = character, size = 34.dp)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                text = persona?.name ?: "DSH 伴侣",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = when {
                                    streaming -> "思考中…"
                                    recording -> "录音中…"
                                    listening -> "在听…"
                                    persona == null -> "还没有角色"
                                    persona?.apiKey.isNullOrBlank() -> "还没填 API Key"
                                    else -> "在线 · ${persona?.model.orEmpty()}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        enabled = persona != null,
                        onClick = {
                            persona?.let { vm.savePersona(it.copy(speakReplies = !it.speakReplies)) }
                        },
                    ) {
                        Icon(
                            imageVector = if (persona?.speakReplies == true) {
                                Icons.Default.VolumeUp
                            } else {
                                Icons.Default.VolumeOff
                            },
                            contentDescription = "朗读回复",
                        )
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "聊天记录")
                    }
                    IconButton(onClick = { vm.newChat() }) {
                        Icon(Icons.Default.Add, contentDescription = "新对话")
                    }
                    IconButton(onClick = onPersonas) {
                        Icon(Icons.Default.Person, contentDescription = "角色")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            if (persona != null && persona?.apiKey.isNullOrBlank()) {
                Card(
                    onClick = onPersonas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "还没填 API Key。点这里选一个角色，填上 Key 和模型，就能开口了。",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "说点什么吧。\n点右上角可以新建对话，历史在旁边的时钟里。",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        typing = streaming &&
                            message.role == ChatMessage.ROLE_ASSISTANT &&
                            message.content.isEmpty(),
                        assistantCharacter = character,
                        assistantAvatarUri = persona?.avatarUri,
                        assistantName = persona?.name ?: "DSH 伴侣",
                        userAvatarUri = userAvatar,
                        userName = userName,
                        userBubbleColor = userBubbleColor,
                        assistantBubbleColor = assistantBubbleColor,
                    )
                }
            }

            if (recordingVoice) {
                Text(
                    text = "录音中… 点 + →「停止并发送语音」",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(
                        enabled = persona != null,
                        onClick = { showAttach = true },
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "添加附件",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = showAttach,
                        onDismissRequest = { showAttach = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("图片") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
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
                        DropdownMenuItem(
                            text = { Text("表情包") },
                            leadingIcon = { Icon(Icons.Default.EmojiEmotions, contentDescription = null) },
                            onClick = {
                                showAttach = false
                                showStickers = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (recordingVoice) "停止并发送语音" else "语音消息") },
                            leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null) },
                            onClick = {
                                showAttach = false
                                if (!recordingVoice) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        runCatching { audioRecorder.start() }
                                            .onSuccess { recordingVoice = true }
                                            .onFailure {
                                                scope.launch { snackbar.showSnackbar("录音启动失败") }
                                            }
                                    }
                                } else {
                                    val file = audioRecorder.stop()
                                    recordingVoice = false
                                    if (file != null) {
                                        vm.sendAttachment(Uri.fromFile(file), "audio")
                                    }
                                }
                            },
                        )
                    }
                }

                IconButton(
                    enabled = persona != null,
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        when {
                            recording -> vm.finishVoiceInput(
                                onFinal = { text ->
                                    input = ""
                                    vm.send(text)
                                },
                                onError = { message ->
                                    scope.launch { snackbar.showSnackbar(message) }
                                },
                            )

                            listening -> vm.stopListening()
                            granted -> beginVoice()
                            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (recording || listening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "语音输入",
                        tint = if (recording || listening) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    placeholder = {
                        Text(
                            when {
                                recording -> "录音中… 再点一次结束"
                                listening -> "在听…"
                                partial == "识别中…" -> "识别中…"
                                else -> "跟她说点什么"
                            }
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )

                FilledIconButton(
                    onClick = { if (streaming) vm.stop() else send() },
                    enabled = streaming || input.isNotBlank(),
                ) {
                    Icon(
                        imageVector = if (streaming) Icons.Default.Stop else Icons.Default.Send,
                        contentDescription = if (streaming) "停止" else "发送",
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp),
        )
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "聊天记录",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            vm.newChat()
                            showHistory = false
                        },
                    ) { Text("新建对话") }
                }

                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(conversations, key = { it.id }) { conversation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.switchConversation(conversation.id)
                                    showHistory = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = conversation.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formatTime(conversation.updatedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (conversation.id == activeConversationId) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "当前对话",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { vm.deleteConversation(conversation.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除对话")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (showStickers) {
        val stickers = listOf("😀", "🥰", "😭", "😡", "🤔", "👍", "🐟", "💙", "✨", "🎉")
        AlertDialog(
            onDismissRequest = { showStickers = false },
            title = { Text("表情包") },
            text = {
                Column {
                    stickers.chunked(5).forEach { row ->
                        Row {
                            row.forEach { sticker ->
                                TextButton(onClick = {
                                    showStickers = false
                                    vm.send(sticker)
                                }) {
                                    Text(sticker, fontSize = 26.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStickers = false }) { Text("关闭") }
            },
        )
    }
}

private val chatTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

private fun formatTime(at: Long): String = chatTimeFormat.format(Date(at))

@Composable
fun ChatAttachment(uri: String, type: String?, name: String?) {
    val context = LocalContext.current
    when (type) {
        "image" -> {
            val bitmap by produceState<Bitmap?>(null, uri) {
                value = withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(uri) }.getOrNull()
                }
            }
            bitmap?.let { image ->
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = name ?: "图片",
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        "audio" -> {
            val player = remember { MediaPlayer() }
            var playing by remember { mutableStateOf(false) }
            DisposableEffect(Unit) {
                onDispose { runCatching { player.release() } }
            }
            Row(
                modifier = Modifier
                    .clickable {
                        if (playing) {
                            runCatching { player.pause() }
                            playing = false
                        } else {
                            runCatching {
                                player.reset()
                                player.setDataSource(uri)
                                player.setOnCompletionListener { playing = false }
                                player.prepare()
                                player.start()
                                playing = true
                            }.onFailure { playing = false }
                        }
                    }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放语音",
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(" 语音", style = MaterialTheme.typography.bodyMedium)
            }
        }

        else -> {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (type == "video") " 视频" else " ${name ?: "文件"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ChatBackground(color: Int, imageUri: String?) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, imageUri) {
        value = withContext(Dispatchers.IO) {
            imageUri?.let { uri ->
                runCatching {
                    val parsed = Uri.parse(uri)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(parsed)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sample = 1
                    while (bounds.outHeight / sample > 1800) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(parsed)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                }.getOrNull()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(color))) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    typing: Boolean,
    assistantCharacter: com.dsh.companion.data.Character?,
    assistantAvatarUri: String?,
    assistantName: String,
    userAvatarUri: String?,
    userName: String,
    userBubbleColor: Int,
    assistantBubbleColor: Int,
) {
    val isUser = message.role == ChatMessage.ROLE_USER
    val custom = if (isUser) userBubbleColor else assistantBubbleColor
    val container = when {
        message.error -> MaterialTheme.colorScheme.errorContainer
        custom != 0 -> Color(custom)
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        message.error -> MaterialTheme.colorScheme.onErrorContainer
        custom != 0 -> if (Color(custom).luminance() > 0.55f) Color(0xFF1B1B1B) else Color.White
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            ChatAvatar(
                uri = assistantAvatarUri,
                character = assistantCharacter,
                name = assistantName,
                size = 38.dp,
            )
            Box(Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 310.dp),
        ) {
            Text(
                text = if (isUser) userName else assistantName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Surface(
                color = container,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 5.dp,
                    bottomEnd = if (isUser) 5.dp else 18.dp,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    message.attachmentUri?.let { uri ->
                        ChatAttachment(
                            uri = uri,
                            type = message.attachmentType,
                            name = message.attachmentName,
                        )
                    }
                    if (message.content.isNotBlank() && message.content != "[图片]" &&
                        message.content != "[视频]" && message.content != "[语音]"
                    ) {
                        Text(
                            text = if (typing) "…" else message.content,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = contentColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (isUser) {
            Box(Modifier.width(6.dp))
            ChatAvatar(
                uri = userAvatarUri,
                character = null,
                name = userName,
                size = 38.dp,
            )
        }
    }
}
