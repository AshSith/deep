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

    fun members(group: GroupChat): List<Persona> {
        personaStore.reload()
        return group.memberIds.mapNotNull { personaStore.get(it) }
    }

    fun responder(group: GroupChat, text: String): Persona? {
        val members = members(group).filter { it.apiKey.isNotBlank() }
        if (members.isEmpty()) return null
        val mentions = Regex("@([^\\s@，,。！!？?]+)").findAll(text)
            .map { it.groupValues[1] }
            .toList()
        if (mentions.isNotEmpty()) {
            val scored = members.map { persona ->
                val score = mentions.maxOfOrNull { mention ->
                    when {
                        persona.name.equals(mention, ignoreCase = true) -> 3
                        persona.name.startsWith(mention, ignoreCase = true) -> 2
                        persona.name.contains(mention, ignoreCase = true) -> 1
                        else -> 0
                    }
                } ?: 0
                persona to score
            }.filter { it.second > 0 }
            if (scored.isNotEmpty()) {
                val best = scored.maxOf { it.second }
                return scored.filter { it.second == best }.random().first
            }
        }
        return members.random()
    }

    suspend fun reply(group: GroupChat, persona: Persona): ChatMessage {
        val members = members(group)
        val names = members.joinToString("、") { it.name }
        val others = members.filter { it.id != persona.id }.joinToString("、") { it.name }
        val system = buildString {
            append("【身份锁定】你的固定身份是「")
            append(persona.name)
            append("」，不是任何其他成员，也不要扮演其他成员。\n\n")
            append(persona.systemPrompt)
            append("\n\n【群聊模式｜必须遵守】群名：")
            append(group.title)
            append("。成员：")
            append(names)
            append("。用户是群主。")
            append("你是「")
            append(persona.name)
            append("」，身份固定。历史消息里的「名字：」只表示谁在说话，不代表你的设定。")
            if (others.isNotBlank()) {
                append("不要模仿或沿用")
                append(others)
                append("的自称、口癖、称呼和关系设定；不要替他们说话。")
            }
            append("只输出「")
            append(persona.name)
            append("」这一句回复，1-2 句，口语；不要写任何名字前缀。")
        }
        val history = historyFor(group, 30, persona.id) + ChatMessage(
            role = ChatMessage.ROLE_USER,
            content = "【群聊提示】现在只由「${persona.name}」回复，请始终以${persona.name}的身份说话。",
        )
        val builder = StringBuilder()
        client.stream(persona.copy(systemPrompt = system), history).collect { builder.append(it) }
        val answer = cleanReply(builder.toString().trim(), members)
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
        val others = members.filter { it.id != persona.id }.joinToString("、") { it.name }
        val system = buildString {
            append("【身份锁定】你的固定身份是「")
            append(persona.name)
            append("」，不是任何其他成员。\n\n")
            append(persona.systemPrompt)
            append("\n\n【群聊模式｜必须遵守】群名：")
            append(group.title)
            append("。成员：")
            append(names)
            append("。用户暂时不在。你是「")
            append(persona.name)
            append("」，身份固定。")
            if (others.isNotBlank()) {
                append("不要模仿或沿用")
                append(others)
                append("的自称、口癖、称呼和关系设定；不要替他们说话。")
            }
            append("主动说一句，1-2 句，口语；不要问用户问题，不要连续刷屏；不要写任何名字前缀。")
        }
        val history = historyFor(group, 20, persona.id) + ChatMessage(
            role = ChatMessage.ROLE_USER,
            content = "【群聊提示】现在只由「${persona.name}」主动说一句，请始终以${persona.name}的身份说话。",
        )
        val builder = StringBuilder()
        client.stream(persona.copy(systemPrompt = system), history).collect { builder.append(it) }
        val answer = cleanReply(builder.toString().trim(), members)
        if (answer.isEmpty()) return null
        return ChatMessage(
            role = ChatMessage.ROLE_ASSISTANT,
            content = answer,
            personaId = persona.id,
            senderName = persona.name,
        )
    }

    private fun cleanReply(raw: String, members: List<Persona>): String {
        var text = raw.trim()
        val names = members.map { it.name }.sortedByDescending { it.length }
        for (name in names) {
            for (sep in listOf("：", ":")) {
                val prefix = "$name$sep"
                if (text.startsWith(prefix)) {
                    text = text.removePrefix(prefix).trim()
                }
            }
        }
        return text.trim()
    }

    private fun historyFor(
        group: GroupChat,
        limit: Int = 30,
        selfPersonaId: String? = null,
    ): List<ChatMessage> =
        groupStore.messages(group.id)
            .takeLast(limit)
            .mapNotNull { message ->
                when (message.role) {
                    ChatMessage.ROLE_USER -> ChatMessage(
                        role = ChatMessage.ROLE_USER,
                        content = message.content,
                        attachmentUri = message.attachmentUri,
                        attachmentType = message.attachmentType,
                        attachmentName = message.attachmentName,
                    )

                    ChatMessage.ROLE_ASSISTANT -> if (message.error) {
                        null
                    } else if (selfPersonaId != null && message.personaId == selfPersonaId) {
                        ChatMessage(
                            role = ChatMessage.ROLE_ASSISTANT,
                            content = message.content,
                            attachmentUri = message.attachmentUri,
                            attachmentType = message.attachmentType,
                            attachmentName = message.attachmentName,
                        )
                    } else {
                        // Other members are context, not this model's own past output.
                        // Keeping them as assistant turns makes the model continue their voice.
                        ChatMessage(
                            role = ChatMessage.ROLE_USER,
                            content = "${message.senderName ?: "成员"}：${message.content}",
                            attachmentUri = message.attachmentUri,
                            attachmentType = message.attachmentType,
                            attachmentName = message.attachmentName,
                        )
                    }

                    else -> null
                }
            }
}
