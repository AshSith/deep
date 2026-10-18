package com.dsh.companion.data

import android.content.Context
import java.io.File

class ChatStore(context: Context) {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }

    fun load(personaId: String): List<ChatMessage> {
        val file = File(dir, "${safe(personaId)}.json")
        return JsonStore.read<List<ChatMessage>>(file, emptyList())
    }

    fun save(personaId: String, messages: List<ChatMessage>) {
        val file = File(dir, "${safe(personaId)}.json")
        runCatching { JsonStore.write(file, messages.takeLast(MAX_MESSAGES)) }
    }

    fun clear(personaId: String) {
        File(dir, "${safe(personaId)}.json").delete()
    }

    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        const val MAX_MESSAGES = 200
    }
}
