package com.dsh.companion.data

import android.content.Context
import java.io.File

class GroupStore(context: Context) {
    private val dir = File(context.filesDir, "groups").apply { mkdirs() }
    private val indexFile = File(dir, "groups.json")
    private val lock = Any()

    fun groups(): List<GroupChat> =
        JsonStore.read<List<GroupChat>>(indexFile, emptyList())
            .sortedByDescending { it.updatedAt }

    fun create(title: String, memberIds: List<String>, autoChat: Boolean): GroupChat =
        synchronized(lock) {
            val group = GroupChat(
                title = title.trim().ifBlank { "新群聊" },
                memberIds = memberIds,
                autoChat = autoChat,
            )
            val all = groups().toMutableList()
            all.add(group)
            writeIndex(all)
            group
        }

    fun update(group: GroupChat) {
        synchronized(lock) {
            val all = groups().toMutableList()
            val index = all.indexOfFirst { it.id == group.id }
            if (index < 0) return
            all[index] = group
            writeIndex(all)
        }
    }

    fun delete(groupId: String) {
        synchronized(lock) {
            File(dir, "${safe(groupId)}.json").delete()
            writeIndex(groups().filterNot { it.id == groupId })
        }
    }

    fun messages(groupId: String): List<ChatMessage> =
        JsonStore.read(File(dir, "${safe(groupId)}.json"), emptyList())

    fun saveMessages(groupId: String, messages: List<ChatMessage>) {
        synchronized(lock) {
            JsonStore.write(File(dir, "${safe(groupId)}.json"), messages.takeLast(MAX_MESSAGES))
            val all = groups().toMutableList()
            val index = all.indexOfFirst { it.id == groupId }
            if (index >= 0) {
                all[index] = all[index].copy(updatedAt = System.currentTimeMillis())
                writeIndex(all)
            }
        }
    }

    private fun writeIndex(all: List<GroupChat>) {
        runCatching { JsonStore.write(indexFile, all) }
    }

    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        const val MAX_MESSAGES = 300
    }
}
