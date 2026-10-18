package com.dsh.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.MainViewModel
import com.dsh.companion.data.ChatMessage
import com.dsh.companion.ui.components.CharacterStage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: MainViewModel,
    onPersonas: () -> Unit,
) {
    val context = LocalContext.current
    val persona by vm.activePersona.collectAsStateWithLifecycle()
    val character by vm.activeCharacter.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    val clip by vm.stageClip.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val voiceMessage by vm.voiceMessage.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
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
        Column(Modifier.fillMaxSize().imePadding()) {
            TopAppBar(
                title = {
                    Column {
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
                    IconButton(onClick = { vm.newChat() }) {
                        Icon(Icons.Default.Add, contentDescription = "新对话")
                    }
                    IconButton(onClick = onPersonas) {
                        Icon(Icons.Default.Person, contentDescription = "角色")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(250.dp),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3A63A8).copy(alpha = 0.30f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height * 0.58f),
                            radius = size.minDimension * 0.72f,
                        ),
                        radius = size.minDimension * 0.72f,
                        center = Offset(size.width / 2f, size.height * 0.58f),
                    )
                }

                CharacterStage(
                    character = character,
                    clip = clip,
                    modifier = Modifier.fillMaxSize(),
                    onTap = { vm.playOnce("poke") },
                    onDoubleTap = { vm.playOnce("tail") },
                    onLongPress = { vm.playOnce("head_pat") },
                )

                if (persona == null) {
                    Text(
                        text = "还没有角色，点右上角去创建一个",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
                            text = "说点什么吧。\n戳她、摸头、双击尾巴，她会有反应。",
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
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
}

@Composable
private fun MessageBubble(message: ChatMessage, typing: Boolean) {
    val isUser = message.role == ChatMessage.ROLE_USER
    val container = when {
        message.error -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        message.error -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = container,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 5.dp,
                bottomEnd = if (isUser) 5.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = if (typing) "…" else message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
