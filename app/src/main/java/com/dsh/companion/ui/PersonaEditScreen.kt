package com.dsh.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.MainViewModel
import com.dsh.companion.data.Character
import com.dsh.companion.data.Persona
import com.dsh.companion.ui.components.CharacterThumb
import com.dsh.companion.ui.components.ChatAvatar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditScreen(
    vm: MainViewModel,
    personaId: String,
    onBack: () -> Unit,
) {
    val personas by vm.personas.collectAsStateWithLifecycle()
    val characters by vm.characters.collectAsStateWithLifecycle()
    val voices by vm.voices.collectAsStateWithLifecycle()
    val ttsReady by vm.ttsReady.collectAsStateWithLifecycle()
    val voiceMessage by vm.voiceMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isNew = personaId == "new"
    val base = remember(isNew, personaId) {
        if (isNew) Persona() else personas.firstOrNull { it.id == personaId } ?: Persona()
    }

    var name by remember(base.id) { mutableStateOf(base.name) }
    var prompt by remember(base.id) { mutableStateOf(base.systemPrompt) }
    var baseUrl by remember(base.id) { mutableStateOf(base.baseUrl) }
    var apiKey by remember(base.id) { mutableStateOf(base.apiKey) }
    var model by remember(base.id) { mutableStateOf(base.model) }
    var temperature by remember(base.id) { mutableStateOf(base.temperature) }
    var maxTokens by remember(base.id) { mutableStateOf(base.maxTokens) }
    var characterId by remember(base.id) { mutableStateOf(base.characterId) }
    var avatarUri by remember(base.id) { mutableStateOf(base.avatarUri) }
    var voiceName by remember(base.id) { mutableStateOf(base.voiceName) }
    var pitch by remember(base.id) { mutableStateOf(base.speechPitch) }
    var rate by remember(base.id) { mutableStateOf(base.speechRate) }
    var speak by remember(base.id) { mutableStateOf(base.speakReplies) }
    var sttBaseUrl by remember(base.id) { mutableStateOf(base.sttBaseUrl) }
    var sttApiKey by remember(base.id) { mutableStateOf(base.sttApiKey) }
    var sttModel by remember(base.id) { mutableStateOf(base.sttModel) }
    var ttsBaseUrl by remember(base.id) { mutableStateOf(base.ttsBaseUrl) }
    var ttsApiKey by remember(base.id) { mutableStateOf(base.ttsApiKey) }
    var ttsModel by remember(base.id) { mutableStateOf(base.ttsModel) }
    var ttsVoice by remember(base.id) { mutableStateOf(base.ttsVoice) }
    var ttsVoice2 by remember(base.id) { mutableStateOf(base.ttsVoice2) }
    var ttsMix by remember(base.id) { mutableStateOf(base.ttsMix) }
    var showKey by remember { mutableStateOf(false) }
    var showVoices by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Character?>(null) }

    val selectedCharacter = characters.firstOrNull { it.id == characterId } ?: characters.firstOrNull()

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            avatarUri = uri.toString()
        }
    }

    LaunchedEffect(characters) {
        if (characters.isNotEmpty() && characters.none { it.id == characterId }) {
            characterId = characters.first().id
        }
    }

    fun draft(): Persona = base.copy(
        name = name.trim().ifBlank { "未命名角色" },
        characterId = selectedCharacter?.id ?: characterId,
        avatarUri = avatarUri,
        systemPrompt = prompt,
        baseUrl = baseUrl.trim().ifBlank { "https://api.deepseek.com/v1" },
        apiKey = apiKey.trim(),
        model = model.trim().ifBlank { "deepseek-chat" },
        temperature = temperature,
        maxTokens = maxTokens,
        voiceName = voiceName,
        speechPitch = pitch,
        speechRate = rate,
        speakReplies = speak,
        sttBaseUrl = sttBaseUrl.trim(),
        sttApiKey = sttApiKey.trim(),
        sttModel = sttModel.trim().ifBlank { "whisper-1" },
        ttsBaseUrl = ttsBaseUrl.trim(),
        ttsApiKey = ttsApiKey.trim(),
        ttsModel = ttsModel.trim().ifBlank { "tts-1" },
        ttsVoice = ttsVoice.trim().ifBlank { "alloy" },
        ttsVoice2 = ttsVoice2.trim(),
        ttsMix = ttsMix,
    )

    fun save() {
        vm.savePersona(draft())
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建角色" else "编辑角色") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                    TextButton(onClick = { save() }) {
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(title = "形象") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "共 ${characters.size} 个形象",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showLibrary = true }) { Text("管理形象") }
                }
                if (characters.isEmpty()) {
                    Text("还没有角色素材包，去「角色」页导入一个。", color = MaterialTheme.colorScheme.error)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(characters, key = { it.id }) { character ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (character.id == selectedCharacter?.id) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (character.id == selectedCharacter?.id) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .clickable { characterId = character.id }
                                .padding(8.dp),
                        ) {
                            CharacterThumb(character = character, size = 64.dp)
                            Text(
                                text = character.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatAvatar(
                    uri = avatarUri,
                    character = selectedCharacter,
                    name = name,
                    size = 52.dp,
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("聊天头像", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (avatarUri == null) "默认使用角色形象" else "已使用自定义头像",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { avatarPicker.launch(arrayOf("image/*")) }) {
                    Text("自定义")
                }
                if (avatarUri != null) {
                    TextButton(onClick = { avatarUri = null }) { Text("默认") }
                }
            }

            Section(title = "基本") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型") },
                    supportingText = { Text("例如 deepseek-chat、gpt-4o-mini、qwen-plus") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 地址") },
                    supportingText = { Text("OpenAI 兼容接口，通常以 /v1 结尾") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "隐藏" else "显示",
                            )
                        }
                    },
                    supportingText = { Text("只存在这台手机的私有目录里，不会上传到别处") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(title = "人设") {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("System Prompt") },
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(title = "声音") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("音色", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = voiceName ?: "跟随系统默认",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showVoices = true }) { Text("选择") }
                    TextButton(onClick = { vm.previewPersonaVoice(draft()) }) { Text("试听") }
                }

                if (!ttsReady) {
                    Text(
                        text = voiceMessage ?: "系统 TTS 还在初始化…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text("音高 ${format(pitch)}", style = MaterialTheme.typography.bodySmall)
                Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.6f..1.8f)

                Text("语速 ${format(rate)}", style = MaterialTheme.typography.bodySmall)
                Slider(value = rate, onValueChange = { rate = it }, valueRange = 0.6f..1.8f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动朗读回复", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "关闭后只显示文字",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = speak, onCheckedChange = { speak = it })
                }

                Text(
                    text = "云端朗读（可选）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = "留空就用系统 TTS。填 OpenAI 兼容的 /audio/speech 接口后，音色、语速和混音都由云端决定；系统音高/音色就不再生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            ttsBaseUrl = "https://api.openai.com/v1"
                            ttsModel = "tts-1"
                            ttsVoice = "nova"
                        },
                    ) { Text("OpenAI 预设") }
                    TextButton(
                        onClick = {
                            ttsBaseUrl = "https://api.siliconflow.cn/v1"
                            ttsModel = "FunAudioLLM/CosyVoice2-0.5B"
                            ttsVoice = "FunAudioLLM/CosyVoice2-0.5B:anna"
                        },
                    ) { Text("硅基流动预设") }
                }
                Text(
                    text = "音色可以填服务商的音色 ID（包括你克隆出来的音色 ID）。真正的离线内置音色需要把语音模型打进 APK，体积会大很多；现在先走云端。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = ttsBaseUrl,
                    onValueChange = { ttsBaseUrl = it },
                    label = { Text("TTS API 地址") },
                    supportingText = { Text("例如 https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ttsApiKey,
                    onValueChange = { ttsApiKey = it },
                    label = { Text("TTS API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ttsModel,
                    onValueChange = { ttsModel = it },
                    label = { Text("TTS 模型") },
                    supportingText = { Text("tts-1 / gpt-4o-mini-tts / 服务商模型名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ttsVoice,
                    onValueChange = { ttsVoice = it },
                    label = { Text("音色 1") },
                    supportingText = { Text("alloy / nova / shimmer，或服务商给的克隆音色 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ttsVoice2,
                    onValueChange = { ttsVoice2 = it },
                    label = { Text("音色 2（可选）") },
                    supportingText = { Text("填了就会把两个音色混在一起播；留空是单音色") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ttsVoice2.isNotBlank()) {
                    Text("混音比例 ${format(ttsMix)}（0 = 只有音色 1，1 = 只有音色 2）", style = MaterialTheme.typography.bodySmall)
                    Slider(value = ttsMix, onValueChange = { ttsMix = it }, valueRange = 0f..1f)
                }
                TextButton(onClick = { vm.previewPersonaVoice(draft()) }) {
                    Text("用当前设置试听")
                }
            }

            Section(title = "语音识别（可选）") {
                Text(
                    text = "手机没有系统识别服务时，填一个 Whisper 兼容接口（OpenAI / 硅基流动 / Groq 等）。留空就用手机自带识别。DeepSeek 不支持音频接口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = sttBaseUrl,
                    onValueChange = { sttBaseUrl = it },
                    label = { Text("STT API 地址") },
                    supportingText = { Text("例如 https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sttApiKey,
                    onValueChange = { sttApiKey = it },
                    label = { Text("STT API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sttModel,
                    onValueChange = { sttModel = it },
                    label = { Text("STT 模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(title = "模型参数") {
                Text("温度 ${format(temperature)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..1.5f,
                )
                Text("最大回复长度 $maxTokens", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { maxTokens = it.toInt() },
                    valueRange = 128f..2048f,
                    steps = 14,
                )
            }

            Text(
                text = "角色素材来自社区，按 MIT 许可分发。导入的角色包只保存在本机。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }

    if (showVoices) {
        Dialog(onDismissRequest = { showVoices = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("选择音色", style = MaterialTheme.typography.titleMedium)
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) {
                        VoiceRow(
                            label = "跟随系统默认",
                            selected = voiceName == null,
                            onClick = {
                                voiceName = null
                                showVoices = false
                            },
                        )
                        voices.forEach { voice ->
                            VoiceRow(
                                label = voice.label + if (voice.needsNetwork) " · 需联网" else "",
                                selected = voiceName == voice.name,
                                onClick = {
                                    voiceName = voice.name
                                    showVoices = false
                                },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showVoices = false }) { Text("关闭") }
                    }
                }
            }
        }
    }

    if (showLibrary) {
        Dialog(onDismissRequest = { showLibrary = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "形象库",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showLibrary = false }) { Text("关闭") }
                    }
                    LazyColumn(Modifier.heightIn(max = 460.dp)) {
                        items(characters, key = { it.id }) { character ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        characterId = character.id
                                        showLibrary = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CharacterThumb(character = character, size = 52.dp)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp),
                                ) {
                                    Text(character.name)
                                    Text(
                                        text = if (character.isBuiltIn) {
                                            "内置形象"
                                        } else {
                                            "导入形象 · ${character.meta.fps}fps"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (character.id == selectedCharacter?.id) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "当前形象",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (!character.isBuiltIn) {
                                    IconButton(
                                        onClick = {
                                            deleteTarget = character
                                            showLibrary = false
                                        },
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除形象")
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这个形象？") },
            text = {
                Text("「${deleteTarget?.name}」的素材会从手机里删掉；正在使用它的角色会回退到第一个形象。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteTarget
                        deleteTarget = null
                        target?.let { vm.deleteCharacter(it.id) }
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个角色？") },
            text = { Text("她的聊天记录也会一起删掉，不能恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vm.deletePersona(base.id)
                        onBack()
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

private fun format(value: Float): String = String.format(Locale.US, "%.2f", value)
