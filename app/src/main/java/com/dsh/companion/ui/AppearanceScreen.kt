package com.dsh.companion.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.MainViewModel

private enum class ColorTarget(val label: String) {
    THEME("主题色"),
    CHAT_BG("聊天背景"),
    BUBBLE("气泡颜色"),
    BUBBLE_TEXT("气泡文字"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val themeColor by vm.themeColor.collectAsStateWithLifecycle()
    val chatBgColor by vm.chatBackgroundColor.collectAsStateWithLifecycle()
    val chatBgImage by vm.chatBackgroundImage.collectAsStateWithLifecycle()
    val bubbleColor by vm.bubbleColor.collectAsStateWithLifecycle()
    val bubbleTextColor by vm.bubbleTextColor.collectAsStateWithLifecycle()

    var target by remember { mutableStateOf<ColorTarget?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观自定义") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppearanceCard(title = "主题色") {
                ColorRow(
                    color = themeColor,
                    onPick = { target = ColorTarget.THEME },
                )
                Text(
                    text = "影响按钮、选中状态、输入框强调色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppearanceCard(title = "聊天背景") {
                ColorRow(
                    color = chatBgColor,
                    onPick = { target = ColorTarget.CHAT_BG },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                        Text(if (chatBgImage == null) "选择背景图片" else "更换背景图片")
                    }
                    if (chatBgImage != null) {
                        TextButton(onClick = { vm.setChatBackgroundImage(null) }) {
                            Text("清除图片")
                        }
                    }
                }
                Text(
                    text = "选图片后会铺满聊天背景，并加一层暗色遮罩。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppearanceCard(title = "桌宠气泡") {
                Text("气泡颜色", style = MaterialTheme.typography.bodySmall)
                ColorRow(color = bubbleColor, onPick = { target = ColorTarget.BUBBLE })

                Text("文字颜色", style = MaterialTheme.typography.bodySmall)
                ColorRow(color = bubbleTextColor, onPick = { target = ColorTarget.BUBBLE_TEXT })

                Text(
                    text = "预览",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = Color(bubbleColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.width(220.dp),
                ) {
                    Text(
                        text = "今天也一起待着吧。",
                        color = Color(bubbleTextColor),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            AppearanceCard(title = "预览") {
                Text(
                    text = "主题色会立刻生效；聊天背景和气泡下一次打开/冒泡时生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(themeColor))
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                    )
                    Text("当前主题色", modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }

    val currentTarget = target
    if (currentTarget != null) {
        val initial = when (currentTarget) {
            ColorTarget.THEME -> themeColor
            ColorTarget.CHAT_BG -> chatBgColor
            ColorTarget.BUBBLE -> bubbleColor
            ColorTarget.BUBBLE_TEXT -> bubbleTextColor
        }
        ColorPickerDialog(
            title = currentTarget.label,
            initial = initial,
            onDismiss = { target = null },
            onConfirm = { color ->
                when (currentTarget) {
                    ColorTarget.THEME -> vm.setThemeColor(color)
                    ColorTarget.CHAT_BG -> vm.setChatBackgroundColor(color)
                    ColorTarget.BUBBLE -> vm.setBubbleColor(color)
                    ColorTarget.BUBBLE_TEXT -> vm.setBubbleTextColor(color)
                }
                target = null
            },
        )
    }
}

@Composable
private fun ColorRow(color: Int, onPick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = String.format("#%06X", color and 0xFFFFFF),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onPick) { Text("调色") }
    }
}

@Composable
private fun AppearanceCard(title: String, content: @Composable () -> Unit) {
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

@Composable
private fun ColorPickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialHsv = remember(initial) { argbToHsv(initial) }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    var hex by remember { mutableStateOf(colorHex(initial)) }

    val color = Color.hsv(hue, saturation, value)
    val argb = color.toArgb()

    LaunchedEffect(argb) {
        hex = colorHex(argb)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(12.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                hue = (offset.x / size.width * 360f).coerceIn(0f, 359.9f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                hue = (change.position.x / size.width * 360f).coerceIn(0f, 359.9f)
                                change.consume()
                            }
                        },
                ) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red,
                            )
                        )
                    )
                    val x = size.width * (hue / 360f)
                    drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, size.height / 2f))
                    drawCircle(
                        Color.Black.copy(alpha = 0.5f),
                        radius = 7.dp.toPx(),
                        center = Offset(x, size.height / 2f),
                        style = Stroke(1.dp.toPx()),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Canvas(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(hue) {
                            detectTapGestures { offset -> updateSv(offset, size) { s, v -> saturation = s; value = v } }
                        }
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                updateSv(change.position, size) { s, v -> saturation = s; value = v }
                                change.consume()
                            }
                        },
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val thumb = Offset(size.width * saturation, size.height * (1f - value))
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = thumb, style = Stroke(2.dp.toPx()))
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { hex = it },
                        label = { Text("十六进制") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            parseHex(hex)?.let { parsed ->
                                val hsv = argbToHsv(parsed)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        },
                    ) { Text("应用") }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(parseHex(hex) ?: argb) }) { Text("确定") }
                }
            }
        }
    }
}

private fun updateSv(
    position: Offset,
    size: IntSize,
    update: (Float, Float) -> Unit,
) {
    val s = (position.x / size.width.toFloat()).coerceIn(0f, 1f)
    val v = (1f - position.y / size.height.toFloat()).coerceIn(0f, 1f)
    update(s, v)
}

private fun argbToHsv(argb: Int): FloatArray {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    return hsv
}

private fun colorHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val clean = text.trim().removePrefix("#")
    if (clean.length != 6) return null
    return runCatching { AndroidColor.parseColor("#$clean") }.getOrNull()
}
