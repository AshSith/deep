package com.dsh.companion.data

import android.content.Context
import java.io.File

class ChatStore(context: Context) {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val lock = Any()

    fun conversations(): List<Conversation> =
        JsonStore.read<List<Conversation>>(indexFile, emptyList())

    fun conversations(personaId: String): List<Conversation> =
        conversations()
            .filter { it.personaId == personaId }
            .sortedByDescending { it.updatedAt }

    /** Returns the newest conversation for this persona, creating one (and migrating old history) if needed. */
    fun ensure(personaId: String, personaName: String): Conversation = synchronized(lock) {
        val all = conversations().toMutableList()
        all.filter { it.personaId == personaId }
            .maxByOrNull { it.updatedAt }
            ?.let { return@synchronized it }

        val legacy = File(dir, "${safe(personaId)}.json")
        val now = System.currentTimeMillis()
        val conversation = if (legacy.isFile) {
            val messages = JsonStore.read<List<ChatMessage>>(legacy, emptyList())
            Conversation(
                id = safe(personaId),
                personaId = personaId,
                title = titleFrom(messages) ?: "和${personaName}的旧对话",
                createdAt = legacy.lastModified().takeIf { it > 0 } ?: now,
                updatedAt = legacy.lastModified().takeIf { it > 0 } ?: now,
            )
        } else {
            Conversation(personaId = personaId, title = "和${personaName}的对话")
        }
        all.add(conversation)
        writeIndex(all)
        conversation
    }

    fun create(personaId: String, personaName: String): Conversation = synchronized(lock) {
        val conversation = Conversation(
            personaId = personaId,
            title = "和${personaName}的对话",
        )
        val all = conversations().toMutableList()
        all.add(conversation)
        writeIndex(all)
        conversation
    }

    fun touch(conversationId: String, title: String? = null) {
        synchronized(lock) {
            val all = conversations().toMutableList()
            val index = all.indexOfFirst { it.id == conversationId }
            if (index < 0) return
            all[index] = all[index].copy(
                updatedAt = System.currentTimeMillis(),
                title = title?.take(24)?.ifBlank { all[index].title } ?: all[index].title,
            )
            writeIndex(all)
        }
    }

    fun load(conversationId: String): List<ChatMessage> {
        val file = File(dir, "${safe(conversationId)}.json")
        return JsonStore.read<List<ChatMessage>>(file, emptyList())
            .filterNot { it.error && it.content.contains("API Key") }
    }

    fun save(conversationId: String, messages: List<ChatMessage>) {
        val file = File(dir, "${safe(conversationId)}.json")
        runCatching { JsonStore.write(file, messages.takeLast(MAX_MESSAGES)) }
        touch(conversationId)
    }

    fun delete(conversationId: String) {
        synchronized(lock) {
            File(dir, "${safe(conversationId)}.json").delete()
            val all = conversations().filterNot { it.id == conversationId }
            writeIndex(all)
        }
    }

    private fun writeIndex(all: List<Conversation>) {
        runCatching { JsonStore.write(indexFile, all) }
    }

    private fun titleFrom(messages: List<ChatMessage>): String? =
        messages.firstOrNull { it.role == ChatMessage.ROLE_USER && it.content.isNotBlank() }
            ?.content
            ?.replace('\n', ' ')
            ?.trim()
            ?.take(18)

    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        const val MAX_MESSAGES = 200
    }
}
