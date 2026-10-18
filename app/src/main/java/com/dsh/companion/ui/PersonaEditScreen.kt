package com.dsh.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.MainViewModel
import com.dsh.companion.data.Persona
import com.dsh.companion.ui.components.CharacterThumb
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
    var voiceName by remember(base.id) { mutableStateOf(base.voiceName) }
    var pitch by remember(base.id) { mutableStateOf(base.speechPitch) }
    var rate by remember(base.id) { mutableStateOf(base.speechRate) }
    var speak by remember(base.id) { mutableStateOf(base.speakReplies) }
    var showKey by remember { mutableStateOf(false) }
    var showVoices by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val selectedCharacter = characters.firstOrNull { it.id == characterId } ?: characters.firstOrNull()

    fun save() {
        val draft = base.copy(
            name = name.trim().ifBlank { "未命名角色" },
            characterId = selectedCharacter?.id ?: characterId,
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
        )
        vm.savePersona(draft)
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
                    TextButton(onClick = { vm.previewVoice(voiceName, pitch, rate) }) { Text("试听") }
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
        AlertDialog(
            onDismissRequest = { showVoices = false },
            title = { Text("选择音色") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    item {
                        VoiceRow(
                            label = "跟随系统默认",
                            selected = voiceName == null,
                            onClick = {
                                voiceName = null
                                showVoices = false
                            },
                        )
                    }
                    items(voices, key = { it.name }) { voice ->
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
            },
            confirmButton = {
                TextButton(onClick = { showVoices = false }) { Text("关闭") }
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
