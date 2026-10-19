package com.dsh.companion

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.companion.data.Character
import com.dsh.companion.data.CharacterRepository
import com.dsh.companion.data.ChatMessage
import com.dsh.companion.data.ChatPreview
import com.dsh.companion.data.ChatStore
import com.dsh.companion.data.Conversation
import com.dsh.companion.data.Persona
import com.dsh.companion.data.PersonaStore
import com.dsh.companion.data.SettingsStore
import com.dsh.companion.net.ChatClient
import com.dsh.companion.net.CloudStt
import com.dsh.companion.net.CloudTts
import com.dsh.companion.pet.PetBus
import com.dsh.companion.pet.PetEvent
import com.dsh.companion.pet.PetOverlayService
import com.dsh.companion.voice.AudioRecorder
import com.dsh.companion.voice.Speaker
import com.dsh.companion.voice.VoiceRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val personaStore = PersonaStore(app)
    private val chatStore = ChatStore(app)
    private val characterRepository = CharacterRepository(app)
    private val settings = SettingsStore(app)
    private val client = ChatClient()
    private val speaker = Speaker(app)
    private val recognizer = VoiceRecognizer(app)
    private val recorder = AudioRecorder(app)
    private val cloudStt = CloudStt()
    private val cloudTts = CloudTts(app)

    val personas: StateFlow<List<Persona>> = personaStore.personas

    private val _activeId = MutableStateFlow(settings.activePersonaId)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _activePersona = MutableStateFlow<Persona?>(personaStore.first())
    val activePersona: StateFlow<Persona?> = _activePersona.asStateFlow()

    private val _characters = MutableStateFlow<List<Character>>(emptyList())
    val characters: StateFlow<List<Character>> = _characters.asStateFlow()

    val activeCharacter: StateFlow<Character?> = combine(_activePersona, _characters) { persona, list ->
        list.firstOrNull { it.id == persona?.characterId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _chatPreviews = MutableStateFlow<List<ChatPreview>>(emptyList())
    val chatPreviews: StateFlow<List<ChatPreview>> = _chatPreviews.asStateFlow()

    private val _desktopMotionFrequency = MutableStateFlow(settings.desktopMotionFrequency)
    val desktopMotionFrequency: StateFlow<Int> = _desktopMotionFrequency.asStateFlow()

    private val _userAvatarUri = MutableStateFlow(settings.userAvatarUri)
    val userAvatarUri: StateFlow<String?> = _userAvatarUri.asStateFlow()

    private val _userName = MutableStateFlow(settings.userName)
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _stageClip = MutableStateFlow("idle")
    val stageClip: StateFlow<String> = _stageClip.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _overlayEnabled = MutableStateFlow(settings.overlayEnabled)
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    private val _overlayScale = MutableStateFlow(settings.overlayScale)
    val overlayScale: StateFlow<Float> = _overlayScale.asStateFlow()

    private val _chatBackgroundColor = MutableStateFlow(settings.chatBackgroundColor)
    val chatBackgroundColor: StateFlow<Int> = _chatBackgroundColor.asStateFlow()

    private val _themeColor = MutableStateFlow(settings.themeColor)
    val themeColor: StateFlow<Int> = _themeColor.asStateFlow()

    private val _chatBackgroundImage = MutableStateFlow(settings.chatBackgroundImage)
    val chatBackgroundImage: StateFlow<String?> = _chatBackgroundImage.asStateFlow()

    private val _bubbleColor = MutableStateFlow(settings.bubbleColor)
    val bubbleColor: StateFlow<Int> = _bubbleColor.asStateFlow()

    private val _bubbleTextColor = MutableStateFlow(settings.bubbleTextColor)
    val bubbleTextColor: StateFlow<Int> = _bubbleTextColor.asStateFlow()

    val ttsReady: StateFlow<Boolean> = speaker.ready
    val voices = speaker.voices
    val voiceMessage = speaker.message

    private var sendJob: Job? = null
    private var speechJob: Job? = null
    private var loadedFor: String? = null

    init {
        speaker.init()
        _characters.value = characterRepository.list()
        refreshChatPreviews()

        viewModelScope.launch {
            personaStore.personas.collect { list ->
                val id = _activeId.value
                val chosen = list.firstOrNull { it.id == id } ?: list.firstOrNull()
                if (chosen == null) {
                    _activePersona.value = null
                    return@collect
                }
                if (loadedFor != chosen.id) {
                    loadedFor = chosen.id
                    _activeId.value = chosen.id
                    settings.activePersonaId = chosen.id
                    val conversation = withContext(Dispatchers.IO) {
                        chatStore.ensure(chosen.id, chosen.name)
                    }
                    _activeConversationId.value = conversation.id
                    _messages.value = withContext(Dispatchers.IO) { chatStore.load(conversation.id) }
                    _conversations.value = withContext(Dispatchers.IO) {
                        chatStore.conversations(chosen.id)
                    }
                }
                _activePersona.value = chosen
            }
        }
    }

    fun setActive(id: String) {
        if (_activeId.value == id) return
        val persona = personaStore.get(id) ?: return
        sendJob?.cancel()
        sendJob = null
        _streaming.value = false
        _activeId.value = id
        settings.activePersonaId = id
        loadedFor = id
        val conversation = chatStore.ensure(persona.id, persona.name)
        _activeConversationId.value = conversation.id
        _messages.value = chatStore.load(conversation.id)
        _conversations.value = chatStore.conversations(persona.id)
        _activePersona.value = persona
        _stageClip.value = "idle"
        PetBus.emit(PetEvent.Clip("idle", once = false))
        refreshChatPreviews()
    }

    fun setUserAvatarUri(uri: String?) {
        settings.userAvatarUri = uri
        _userAvatarUri.value = uri
    }

    fun setUserName(name: String) {
        val next = name.trim().ifBlank { "我" }.take(20)
        settings.userName = next
        _userName.value = next
    }

    fun setDesktopMotionFrequency(value: Int) {
        val next = value.coerceIn(0, 3)
        settings.desktopMotionFrequency = next
        _desktopMotionFrequency.value = next
        PetBus.emit(PetEvent.MotionFrequencyChanged)
    }

    fun openConversation(conversationId: String) {
        val conversation = chatStore.allConversations()
            .firstOrNull { it.id == conversationId } ?: return
        val persona = personaStore.get(conversation.personaId) ?: return
        sendJob?.cancel()
        sendJob = null
        _streaming.value = false
        _activeId.value = persona.id
        settings.activePersonaId = persona.id
        loadedFor = persona.id
        _activeConversationId.value = conversation.id
        _messages.value = chatStore.load(conversation.id)
        _conversations.value = chatStore.conversations(persona.id)
        _activePersona.value = persona
        _stageClip.value = "idle"
        PetBus.emit(PetEvent.Clip("idle", once = false))
        refreshChatPreviews()
    }

    fun toggleConversationPinned(conversationId: String) {
        val conversation = chatStore.allConversations().firstOrNull { it.id == conversationId } ?: return
        chatStore.update(conversation.copy(pinned = !conversation.pinned))
        refreshChatPreviews()
    }

    fun toggleConversationUnread(conversationId: String) {
        val conversation = chatStore.allConversations().firstOrNull { it.id == conversationId } ?: return
        chatStore.update(conversation.copy(unread = !conversation.unread))
        refreshChatPreviews()
    }

    fun deleteConversation(conversationId: String) {
        chatStore.delete(conversationId)
        if (_activeConversationId.value == conversationId) {
            _activeConversationId.value = null
            _messages.value = emptyList()
        }
        refreshChatPreviews()
    }

    fun openLatestConversation(personaId: String) {
        val persona = personaStore.get(personaId) ?: return
        val latest = chatStore.conversations(persona.id).firstOrNull()
        if (latest != null) {
            openConversation(latest.id)
            return
        }
        sendJob?.cancel()
        sendJob = null
        _streaming.value = false
        _activeId.value = persona.id
        settings.activePersonaId = persona.id
        loadedFor = persona.id
        _activePersona.value = persona
        newChat()
    }

    private fun refreshChatPreviews() {
        val people = personaStore.personas.value
        _chatPreviews.value = chatStore.allConversations().map { conversation ->
            ChatPreview(
                conversation = conversation,
                persona = people.firstOrNull { it.id == conversation.personaId },
                lastMessage = chatStore.load(conversation.id).lastOrNull(),
            )
        }
    }

    fun savePersona(persona: Persona) {
        val existing = personaStore.get(persona.id) != null
        personaStore.upsert(persona)
        refreshCharacters()
        if (!existing) setActive(persona.id)
        if (_activeId.value == persona.id) {
            _activePersona.value = persona
            if (persona.apiKey.isNotBlank()) {
                _messages.value = _messages.value.filterNot {
                    it.error && it.content.contains("API Key")
                }
            }
            PetBus.emit(PetEvent.Clip("idle", once = false))
        }
        refreshChatPreviews()
    }

    fun deletePersona(id: String) {
        personaStore.delete(id)
        chatStore.conversations(id).forEach { chatStore.delete(it.id) }
        if (_activeId.value == id) {
            loadedFor = null
            _activeId.value = null
            _activeConversationId.value = null
            _conversations.value = emptyList()
            _messages.value = emptyList()
        }
        refreshChatPreviews()
    }

    fun refreshCharacters() {
        _characters.value = characterRepository.list()
    }

    fun deleteCharacter(id: String) {
        val character = characterRepository.get(id) ?: return
        if (character.isBuiltIn) return
        characterRepository.delete(character)
        val remaining = characterRepository.list()
        val fallback = remaining.firstOrNull()?.id
        if (fallback != null) {
            personaStore.personas.value
                .filter { it.characterId == id }
                .forEach { personaStore.upsert(it.copy(characterId = fallback)) }
        }
        _characters.value = remaining
    }

    fun importCharacter(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { characterRepository.importZip(uri) }
            }.onSuccess { character ->
                refreshCharacters()
                onResult("已导入「${character.name}」")
            }.onFailure {
                onResult("导入失败：${it.message ?: "未知错误"}")
            }
        }
    }

    fun send(raw: String) {
        sendInternal(raw, null)
    }

    fun sendAttachment(uri: Uri, type: String) {
        val file = copyAttachment(uri, type) ?: run {
            _notice.value = "读取文件失败"
            return
        }
        val label = when (type) {
            "image" -> "[图片]"
            "video" -> "[视频]"
            "audio" -> "[语音]"
            else -> "[文件]"
        }
        val attachment = ChatMessage(
            role = ChatMessage.ROLE_USER,
            content = label,
            attachmentUri = file.absolutePath,
            attachmentType = type,
            attachmentName = file.name,
        )
        sendInternal(label, attachment)
    }

    private fun copyAttachment(uri: Uri, type: String): File? {
        val ext = when (type) {
            "image" -> "jpg"
            "video" -> "mp4"
            "audio" -> "m4a"
            else -> "bin"
        }
        val dir = File(getApplication<Application>().filesDir, "chat_media").apply { mkdirs() }
        val file = File(dir, "media-${System.currentTimeMillis()}.$ext")
        return runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (file.length() > 0) file else null
        }.getOrNull()
    }

    private fun sendInternal(raw: String, attachment: ChatMessage?) {
        val text = raw.trim()
        if ((text.isEmpty() && attachment == null) || _streaming.value) return
        val persona = _activePersona.value ?: return
        val conversationId = _activeConversationId.value ?: return

        if (persona.apiKey.isBlank()) {
            _notice.value = "还没有填 API Key。去「角色」里选一个角色，把 Key 填上。"
            _stageClip.value = "error"
            viewModelScope.launch {
                delay(1800)
                if (_stageClip.value == "error") _stageClip.value = "idle"
            }
            return
        }

        val user = attachment ?: ChatMessage(role = ChatMessage.ROLE_USER, content = text)
        val assistant = ChatMessage(role = ChatMessage.ROLE_ASSISTANT, content = "")
        val history = _messages.value + user

        _messages.value = history + assistant
        val firstUserMessage = history.count { it.role == ChatMessage.ROLE_USER } == 1
        chatStore.touch(conversationId, if (firstUserMessage) user.content.take(18) else null)
        _conversations.value = chatStore.conversations(persona.id)
        _streaming.value = true
        _stageClip.value = "thinking"
        PetBus.emit(PetEvent.Thinking)

        sendJob = viewModelScope.launch {
            val builder = StringBuilder()
            try {
                client.stream(persona, history).collect { delta ->
                    builder.append(delta)
                    updateMessage(assistant.id) { it.copy(content = builder.toString()) }
                }
                val reply = builder.toString().trim()
                if (reply.isEmpty()) throw IOException("模型没有返回内容")

                updateMessage(assistant.id) { it.copy(content = reply) }
                _stageClip.value = "success"
                PetBus.emit(PetEvent.Success)
                if (!AppVisibility.foreground.value) {
                    PetBus.emit(
                        PetEvent.Say(
                            text = reply,
                            conversationId = _activeConversationId.value,
                            personaId = persona.id,
                        )
                    )
                }
                if (persona.speakReplies) speakReply(persona, reply)

                delay(1100)
                if (_stageClip.value == "success") _stageClip.value = "idle"
            } catch (cancelled: CancellationException) {
                _stageClip.value = "idle"
                throw cancelled
            } catch (error: Exception) {
                val message = error.message ?: "请求失败"
                updateMessage(assistant.id) {
                    it.copy(content = "呜……出错了：$message", error = true)
                }
                _stageClip.value = "error"
                PetBus.emit(PetEvent.Error)
                delay(1500)
                if (_stageClip.value == "error") _stageClip.value = "idle"
            } finally {
                _streaming.value = false
                if (_activeConversationId.value == conversationId) {
                    chatStore.save(conversationId, _messages.value)
                    _conversations.value = chatStore.conversations(persona.id)
                }
                refreshChatPreviews()
            }
        }
    }

    fun stop() {
        sendJob?.cancel()
        sendJob = null
        speechJob?.cancel()
        speechJob = null
        _streaming.value = false
        _stageClip.value = "idle"
        speaker.stop()
        cloudTts.stop()
    }

    fun newChat() {
        val persona = _activePersona.value ?: return
        sendJob?.cancel()
        sendJob = null
        _streaming.value = false
        val conversation = chatStore.create(persona.id, persona.name)
        _activeConversationId.value = conversation.id
        _conversations.value = chatStore.conversations(persona.id)
        _messages.value = emptyList()
        _stageClip.value = "idle"
        refreshChatPreviews()
    }

    fun switchConversation(conversationId: String) {
        if (_activeConversationId.value == conversationId) return
        val persona = _activePersona.value ?: return
        sendJob?.cancel()
        sendJob = null
        _streaming.value = false
        _activeConversationId.value = conversationId
        _messages.value = chatStore.load(conversationId)
        _conversations.value = chatStore.conversations(persona.id)
        _stageClip.value = "idle"
        refreshChatPreviews()
    }

    fun deleteConversation(conversationId: String) {
        val persona = _activePersona.value ?: return
        chatStore.delete(conversationId)
        var remaining = chatStore.conversations(persona.id)
        if (remaining.isEmpty()) {
            remaining = listOf(chatStore.create(persona.id, persona.name))
        }
        _conversations.value = remaining
        if (_activeConversationId.value == conversationId) {
            val next = remaining.first()
            _activeConversationId.value = next.id
            _messages.value = chatStore.load(next.id)
            _stageClip.value = "idle"
        }
        refreshChatPreviews()
    }

    fun playOnce(clip: String) {
        _stageClip.value = clip
        viewModelScope.launch {
            delay(2400)
            if (_stageClip.value == clip) _stageClip.value = "idle"
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        settings.overlayEnabled = enabled
        _overlayEnabled.value = enabled
        val context = getApplication<Application>()
        if (enabled) {
            PetOverlayService.start(context)
        } else {
            PetOverlayService.stop(context)
        }
    }

    fun setOverlayScale(scale: Float) {
        settings.overlayScale = scale
        _overlayScale.value = scale
    }

    fun setChatBackgroundColor(color: Int) {
        settings.chatBackgroundColor = color
        _chatBackgroundColor.value = color
    }

    fun setThemeColor(color: Int) {
        settings.themeColor = color
        _themeColor.value = color
    }

    fun setChatBackgroundImage(uri: String?) {
        settings.chatBackgroundImage = uri
        _chatBackgroundImage.value = uri
    }

    fun setBubbleColor(color: Int) {
        settings.bubbleColor = color
        _bubbleColor.value = color
    }

    fun setBubbleTextColor(color: Int) {
        settings.bubbleTextColor = color
        _bubbleTextColor.value = color
    }

    fun restartOverlay() {
        if (!_overlayEnabled.value) return
        val context = getApplication<Application>()
        PetOverlayService.stop(context)
        viewModelScope.launch {
            delay(250)
            PetOverlayService.start(context)
        }
    }

    fun previewVoice(voiceName: String?, pitch: Float, rate: Float) {
        speaker.preview(voiceName, pitch, rate)
    }

    fun previewPersonaVoice(persona: Persona) {
        if (persona.ttsBaseUrl.isNotBlank() && persona.ttsApiKey.isNotBlank()) {
            viewModelScope.launch {
                runCatching {
                    cloudTts.speak(persona, "你好呀，我是你的伙伴。今天想聊点什么？")
                }.onFailure {
                    _notice.value = "云端朗读失败：${it.message ?: "未知错误"}"
                }
            }
        } else {
            speaker.preview(persona.voiceName, persona.speechPitch, persona.speechRate)
            if (!speaker.ready.value) {
                _notice.value = "系统 TTS 还没就绪；可以在角色里填云端 TTS 的 API"
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    private fun speakReply(persona: Persona, text: String) {
        speechJob?.cancel()
        if (persona.ttsBaseUrl.isNotBlank() && persona.ttsApiKey.isNotBlank()) {
            speechJob = viewModelScope.launch {
                runCatching { cloudTts.speak(persona, text) }
                    .onFailure {
                        _notice.value = "云端朗读失败：${it.message ?: "未知错误"}"
                    }
            }
        } else {
            speaker.speak(text, persona)
            if (!speaker.ready.value) {
                _notice.value = "系统 TTS 还没就绪。可以在角色里填云端 TTS 的 API。"
            }
        }
    }

    fun refreshVoices() {
        speaker.refreshVoices()
    }

    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (_listening.value) {
            recognizer.stop()
            return
        }
        _listening.value = true
        _partial.value = ""
        recognizer.start(
            onPartial = {
                _partial.value = it
                onPartial(it)
            },
            onFinal = {
                _listening.value = false
                _partial.value = ""
                onFinal(it)
            },
            onError = {
                _listening.value = false
                _partial.value = ""
                onError(it)
            },
        )
    }

    fun stopListening() {
        _listening.value = false
        _partial.value = ""
        recognizer.stop()
    }

    fun beginVoiceInput(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val persona = _activePersona.value
        if (persona != null && persona.sttApiKey.isNotBlank() && persona.sttBaseUrl.isNotBlank()) {
            runCatching { recorder.start() }
                .onSuccess { _recording.value = true }
                .onFailure { onError("录音启动失败：${it.message ?: "未知错误"}") }
        } else {
            startListening(
                onPartial = onPartial,
                onFinal = onFinal,
                onError = onError,
            )
        }
    }

    fun finishVoiceInput(onFinal: (String) -> Unit, onError: (String) -> Unit) {
        if (!_recording.value) return
        val file = recorder.stop()
        _recording.value = false
        val persona = _activePersona.value
        if (file == null || persona == null) {
            onError("没有录到声音")
            return
        }
        _partial.value = "识别中…"
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    cloudStt.transcribe(persona, file)
                }
            }.onSuccess { text ->
                _partial.value = ""
                if (text.isBlank()) onError("没听清，再说一次") else onFinal(text)
            }.onFailure {
                _partial.value = ""
                onError("识别失败：${it.message ?: "未知错误"}")
            }
            file.delete()
        }
    }

    fun cancelVoiceInput() {
        if (_recording.value) {
            recorder.cancel()
            _recording.value = false
        }
        stopListening()
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.value = _messages.value.map { message ->
            if (message.id == id) transform(message) else message
        }
    }

    override fun onCleared() {
        sendJob?.cancel()
        speechJob?.cancel()
        recorder.cancel()
        cloudTts.stop()
        recognizer.destroy()
        speaker.shutdown()
        super.onCleared()
    }
}
