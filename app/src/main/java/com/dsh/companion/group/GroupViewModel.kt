package com.dsh.companion.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.companion.data.Character
import com.dsh.companion.data.CharacterRepository
import com.dsh.companion.data.ChatMessage
import com.dsh.companion.data.GroupChat
import com.dsh.companion.data.GroupStore
import com.dsh.companion.data.Persona
import com.dsh.companion.data.PersonaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GroupStore(app)
    private val personaStore = PersonaStore(app)
    private val characterRepository = CharacterRepository(app)
    private val engine = GroupChatEngine(app)

    val personas: StateFlow<List<Persona>> = personaStore.personas

    private val _characters = MutableStateFlow<List<Character>>(characterRepository.list())
    val characters: StateFlow<List<Character>> = _characters.asStateFlow()

    private val _groups = MutableStateFlow(store.groups())
    val groups: StateFlow<List<GroupChat>> = _groups.asStateFlow()

    private val _activeGroup = MutableStateFlow<GroupChat?>(null)
    val activeGroup: StateFlow<GroupChat?> = _activeGroup.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun refreshGroups() {
        _groups.value = store.groups()
        _characters.value = characterRepository.list()
    }

    fun createGroup(title: String, memberIds: List<String>, autoChat: Boolean): GroupChat {
        val group = store.create(title, memberIds, autoChat)
        refreshGroups()
        openGroup(group)
        return group
    }

    fun openGroup(groupId: String) {
        val group = store.groups().firstOrNull { it.id == groupId } ?: return
        _activeGroup.value = group
        _messages.value = store.messages(group.id)
    }

    fun openGroup(group: GroupChat) {
        _activeGroup.value = group
        _messages.value = store.messages(group.id)
    }

    fun updateAutoChat(enabled: Boolean) {
        val group = _activeGroup.value ?: return
        val updated = group.copy(autoChat = enabled)
        store.update(updated)
        _activeGroup.value = updated
        refreshGroups()
    }

    fun deleteGroup(groupId: String) {
        store.delete(groupId)
        if (_activeGroup.value?.id == groupId) {
            _activeGroup.value = null
            _messages.value = emptyList()
        }
        refreshGroups()
    }

    fun send(text: String) {
        val group = _activeGroup.value ?: return
        val content = text.trim()
        if (content.isEmpty() || _sending.value) return

        val user = ChatMessage(role = ChatMessage.ROLE_USER, content = content)
        _messages.value = _messages.value + user
        store.saveMessages(group.id, _messages.value)
        _sending.value = true

        viewModelScope.launch {
            try {
                val responder = engine.responder(group, content)
                if (responder == null) {
                    appendError(group, "群里还没有配置 API Key 的成员。")
                    return@launch
                }
                val reply = engine.reply(group, responder)
                _messages.value = _messages.value + reply
                store.saveMessages(group.id, _messages.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                appendError(group, error.message ?: "群聊请求失败")
            } finally {
                _sending.value = false
                refreshGroups()
            }
        }
    }

    private fun appendError(group: GroupChat, message: String) {
        val error = ChatMessage(
            role = ChatMessage.ROLE_ASSISTANT,
            content = message,
            error = true,
            senderName = "系统",
        )
        _messages.value = _messages.value + error
        store.saveMessages(group.id, _messages.value)
    }
}
