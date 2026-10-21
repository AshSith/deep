package com.dsh.companion.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.MainViewModel
import com.dsh.companion.ui.components.ChatAvatar
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onAppearance: () -> Unit, onCharacters: () -> Unit) {
    val context = LocalContext.current
    val overlayEnabled by vm.overlayEnabled.collectAsStateWithLifecycle()
    val scale by vm.overlayScale.collectAsStateWithLifecycle()
    val persona by vm.activePersona.collectAsStateWithLifecycle()
    val chatBgColor by vm.chatBackgroundColor.collectAsStateWithLifecycle()
    val chatBgImage by vm.chatBackgroundImage.collectAsStateWithLifecycle()
    val bubbleColor by vm.bubbleColor.collectAsStateWithLifecycle()
    val bubbleTextColor by vm.bubbleTextColor.collectAsStateWithLifecycle()
    val ttsReady by vm.ttsReady.collectAsStateWithLifecycle()
    val duckSound by vm.duckSoundEnabled.collectAsStateWithLifecycle()
    val personas by vm.personas.collectAsStateWithLifecycle()
    val extraPets by vm.extraPetPersonaIds.collectAsStateWithLifecycle()
    var showPetPicker by remember { mutableStateOf(false) }
    var selectedExtra by remember(extraPets) { mutableStateOf(extraPets) }
    val voices by vm.voices.collectAsStateWithLifecycle()
    val voiceMessage by vm.voiceMessage.collectAsStateWithLifecycle()
    val motionFrequency by vm.desktopMotionFrequency.collectAsStateWithLifecycle()
    val userAvatar by vm.userAvatarUri.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var pendingEnable by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun enableOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.setOverlayEnabled(true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDraw = Settings.canDrawOverlays(context)
                if (pendingEnable) {
                    pendingEnable = false
                    if (canDraw) {
                        enableOverlay()
                    } else {
                        scope.launch { snackbar.showSnackbar("没有悬浮窗权限，桌宠起不来") }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val importZip = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importCharacter(uri) { message ->
                scope.launch { snackbar.showSnackbar(message) }
            }
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.setChatBackgroundImage(uri.toString())
        }
    }

    val userAvatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.setUserAvatarUri(uri.toString())
        }
    }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsCard(title = "手机桌宠") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("悬浮在所有应用上", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (overlayEnabled) "已开启" else "关闭",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = overlayEnabled,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    vm.setOverlayEnabled(false)
                                } else if (!Settings.canDrawOverlays(context)) {
                                    pendingEnable = true
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}"),
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                } else {
                                    enableOverlay()
                                }
                            },
                        )
                    }

                    if (!canDraw) {
                        Text(
                            text = "需要「显示在其他应用上层」权限。开启后：点一下戳她（会冒气泡），双击打开聊天，长按摸头，点气泡也能进聊天。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (overlayEnabled) {
                        Text("大小 ${String.format(Locale.US, "%.2f", scale)}×", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = scale,
                            onValueChange = { vm.setOverlayScale(it) },
                            onValueChangeFinished = { vm.restartOverlay() },
                            valueRange = 0.4f..3.0f,
                        )
                        Text(
                            text = "有些手机会在后台杀掉悬浮窗，需要在系统设置里允许自启动、关闭电池优化。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "桌面自主动作频率",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                0 to "关",
                                1 to "低",
                                2 to "中",
                                3 to "高",
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = motionFrequency == value,
                                    onClick = { vm.setDesktopMotionFrequency(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Text(
                            text = "低约 5 分钟一次，中约 2 分钟，高约 45 秒；只在桌宠悬浮窗开启、App 在后台时触发。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("点击音效（小黄鸭）", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "戳桌宠时播放一声鸭叫",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = duckSound,
                                onCheckedChange = { vm.setDuckSoundEnabled(it) },
                            )
                        }
                    }
                }
            }

            item {
                SettingsCard(title = "多桌宠") {
                    Text(
                        text = "除了当前角色，还可以在桌面同时放最多 3 只（每个角色一个独立窗口）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("已选择 ${extraPets.size} 只", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        selectedExtra = extraPets
                        showPetPicker = true
                    }) { Text("选择桌宠") }
                }
            }

            item {
                SettingsCard(title = "角色") {
                    Text(
                        text = "查看已有形象、导入新的角色包、删除导入的形象。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCharacters) { Text("管理形象") }
                }
            }

            item {
                SettingsCard(title = "我的资料") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChatAvatar(uri = userAvatar, character = null, name = userName, size = 54.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("我的头像", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "聊天界面里显示在你说的话旁边",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = { userAvatarPicker.launch(arrayOf("image/*")) }) {
                            Text("选择头像")
                        }
                        if (userAvatar != null) {
                            TextButton(onClick = { vm.setUserAvatarUri(null) }) { Text("恢复默认") }
                        }
                    }
                }
            }

            item {
                SettingsCard(title = "外观") {
                    Text(
                        text = "主题色、聊天背景、桌宠气泡颜色，都在独立页面里调，不占设置空间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onAppearance) { Text("外观自定义") }
                }
            }

            item {
                SettingsCard(title = "语音") {
                    Text(
                        text = if (ttsReady) "系统 TTS 已就绪 · ${voices.size} 个中文音色" else "系统 TTS 未就绪",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (voiceMessage != null) {
                        Text(
                            text = voiceMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.refreshVoices() }) { Text("刷新音色") }
                        TextButton(onClick = { persona?.let { vm.previewPersonaVoice(it) } }) {
                            Text("测试朗读")
                        }
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                                }.onFailure {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            },
                        ) { Text("系统语音设置") }
                    }
                    Text(
                        text = if (persona?.ttsBaseUrl.isNullOrBlank()) {
                            "当前角色用系统 TTS。音色在「角色 → 声音」里选；不会出声通常是手机没装中文语音引擎。"
                        } else {
                            "当前角色用云端 TTS：${persona?.ttsBaseUrl}，音色 ${persona?.ttsVoice}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsCard(title = "角色包") {
                    Text(
                        text = "支持导入 .zip 角色包：里面要有 character.json 和动画 WebP。导入后会出现在角色列表里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            importZip.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                )
                            )
                        },
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null)
                        Text("导入角色包", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }

            item {
                SettingsCard(title = "关于") {
                    Text("DSH 伴侣 $version", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "一个能自己接 API 的手机桌宠。角色、人设、记忆都存在本机；API Key 也只存在本机私有目录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "内置「神神」动画素材来自 MerZlin/dsh-pet-indesktop，MIT 许可；角色名与 DeepSeek 官方无关。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPetPicker) {
        AlertDialog(
            onDismissRequest = { showPetPicker = false },
            title = { Text("选择桌面桌宠") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(personas, key = { it.id }) { persona ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedExtra = if (persona.id in selectedExtra) {
                                        selectedExtra - persona.id
                                    } else {
                                        selectedExtra + persona.id
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = persona.id in selectedExtra,
                                onCheckedChange = {
                                    selectedExtra = if (persona.id in selectedExtra) {
                                        selectedExtra - persona.id
                                    } else {
                                        selectedExtra + persona.id
                                    }
                                },
                            )
                            Text("${persona.name} · ${persona.characterId}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPetPicker = false
                    vm.setExtraPetPersonas(selectedExtra)
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showPetPicker = false }) { Text("取消") }
            },
        )
    }
}

private val chatBgColors = listOf(
    0xFF0B1020.toInt(),
    0xFF172033.toInt(),
    0xFF2A1B2E.toInt(),
    0xFF102A26.toInt(),
    0xFF2E2414.toInt(),
    0xFF1A1A1A.toInt(),
)

private val bubbleColors = listOf(
    0xF21B2438.toInt(),
    0xF2000000.toInt(),
    0xF2FFE4EC.toInt(),
    0xF2DDF4E4.toInt(),
    0xF2FFFFFF.toInt(),
)

private val bubbleTextColors = listOf(
    0xFFEAF2FF.toInt(),
    0xFF1A1A1A.toInt(),
    0xFF3A2A1A.toInt(),
    0xFF1B2438.toInt(),
)

@Composable
private fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = 2.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
