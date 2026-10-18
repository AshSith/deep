package com.dsh.companion.group

import android.content.Context
import com.dsh.companion.data.ChatMessage
import com.dsh.companion.data.GroupChat
import com.dsh.companion.data.GroupStore
import com.dsh.companion.data.Persona
import com.dsh.companion.data.PersonaStore
import com.dsh.companion.net.ChatClient
import java.io.IOException

class GroupChatEngine(context: Context) {
    private val groupStore = GroupStore(context)
    private val personaStore = PersonaStore(context)
    private val client = ChatClient()

    fun members(group: GroupChat): List<Persona> =
        group.memberIds.mapNotNull { personaStore.get(it) }

    fun responder(group: GroupChat, text: String): Persona? {
        val members = members(group).filter { it.apiKey.isNotBlank() }
        if (members.isEmpty()) return null
        val mentioned = members.filter { persona -> text.contains("@${persona.name}") }
        if (mentioned.isNotEmpty()) return mentioned.first()
        return members.random()
    }

    suspend fun reply(group: GroupChat, persona: Persona): ChatMessage {
        val members = members(group)
        val names = members.joinToString("、") { it.name }
        val system = buildString {
            append(persona.systemPrompt)
            append("\n\n【群聊】群名：")
            append(group.title)
            append("。成员：")
            append(names)
            append("。用户是群主。只代表你自己（")
            append(persona.name)
            append("）说话，1-2 句，口语；不要替别人说话，不要复述别人的话。")
        }
        val history = historyFor(group)
        val builder = StringBuilder()
        client.stream(persona.copy(systemPrompt = system), history).collect { builder.append(it) }
        val answer = builder.toString().trim()
        if (answer.isEmpty()) throw IOException("没有返回内容")
        return ChatMessage(
            role = ChatMessage.ROLE_ASSISTANT,
            content = answer,
            personaId = persona.id,
            senderName = persona.name,
        )
    }

    suspend fun autoMessage(group: GroupChat): ChatMessage? {
        val members = members(group).filter { it.apiKey.isNotBlank() }
        if (members.isEmpty()) return null
        val persona = members.random()
        val names = members.joinToString("、") { it.name }
        val system = buildString {
            append(persona.systemPrompt)
            append("\n\n【群聊】群名：")
            append(group.title)
            append("。成员：")
            append(names)
            append("。用户暂时不在。你可以主动说一句，1-2 句，口语，不要问用户问题，不要连续刷屏，不要替别人说话。")
        }
        val history = historyFor(group, 20)
        val builder = StringBuilder()
        client.stream(persona.copy(systemPrompt = system), history).collect { builder.append(it) }
        val answer = builder.toString().trim()
        if (answer.isEmpty()) return null
        return ChatMessage(
            role = ChatMessage.ROLE_ASSISTANT,
            content = answer,
            personaId = persona.id,
            senderName = persona.name,
        )
    }

    private fun historyFor(group: GroupChat, limit: Int = 30): List<ChatMessage> =
        groupStore.messages(group.id)
            .takeLast(limit)
            .mapNotNull { message ->
                when (message.role) {
                    ChatMessage.ROLE_USER -> ChatMessage(
                        role = ChatMessage.ROLE_USER,
                        content = message.content,
                    )

                    ChatMessage.ROLE_ASSISTANT -> if (message.error) {
                        null
                    } else {
                        ChatMessage(
                            role = ChatMessage.ROLE_ASSISTANT,
                            content = "${message.senderName ?: "成员"}：${message.content}",
                        )
                    }

                    else -> null
                }
            }
}
