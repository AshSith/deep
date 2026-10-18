package com.dsh.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsh.companion.MainViewModel
import com.dsh.companion.data.Character
import com.dsh.companion.data.ChatPreview
import com.dsh.companion.data.GroupChat
import com.dsh.companion.data.Persona
import com.dsh.companion.group.GroupViewModel
import com.dsh.companion.ui.components.CharacterThumb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class RecentEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val at: Long,
    val isGroup: Boolean,
    val character: Character?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    vm: MainViewModel,
    groupVm: GroupViewModel,
    onOpenSingle: () -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    val previews by vm.chatPreviews.collectAsStateWithLifecycle()
    val characters by vm.characters.collectAsStateWithLifecycle()
    val groups by groupVm.groups.collectAsStateWithLifecycle()
    val groupPersonas by groupVm.personas.collectAsStateWithLifecycle()
    val groupCharacters by groupVm.characters.collectAsStateWithLifecycle()

    val entries = buildList {
        previews.forEach { preview ->
            val persona = preview.persona
            val character = persona?.let { p ->
                characters.firstOrNull { it.id == p.characterId }
            } ?: characters.firstOrNull()
            add(
                RecentEntry(
                    id = preview.conversation.id,
                    title = preview.conversation.title,
                    subtitle = preview.lastMessage?.content?.replace('\n', ' ') ?: "还没有消息",
                    at = preview.conversation.updatedAt,
                    isGroup = false,
                    character = character,
                )
            )
        }
        groups.forEach { group ->
            val firstPersona = group.memberIds.firstOrNull()?.let { id ->
                groupPersonas.firstOrNull { it.id == id }
            }
            val character = firstPersona?.let { p ->
                groupCharacters.firstOrNull { it.id == p.characterId }
            } ?: groupCharacters.firstOrNull()
            add(
                RecentEntry(
                    id = group.id,
                    title = group.title,
                    subtitle = "${group.memberIds.size} 个成员" +
                        if (group.autoChat) " · 自动聊天" else "",
                    at = group.updatedAt,
                    isGroup = true,
                    character = character,
                )
            )
        }
    }.sortedByDescending { it.at }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息") },
                actions = {
                    IconButton(
                        onClick = {
                            vm.newChat()
                            onOpenSingle()
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新建对话")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "还没有对话。\n点右上角 + 开一个。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { (if (it.isGroup) "g-" else "s-") + it.id }) { entry ->
                    Card(
                        onClick = {
                            if (entry.isGroup) {
                                groupVm.openGroup(entry.id)
                                onOpenGroup(entry.id)
                            } else {
                                vm.openConversation(entry.id)
                                onOpenSingle()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                CharacterThumb(character = entry.character, size = 50.dp)
                                if (entry.isGroup) {
                                    Icon(
                                        Icons.Default.Group,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                            ) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = entry.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = chatListTime(entry.at),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val chatListFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

private fun chatListTime(at: Long): String = chatListFormat.format(Date(at))
