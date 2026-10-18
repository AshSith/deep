package com.dsh.companion.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.dsh.companion.data.Persona
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class Speaker(context: Context) {
    data class VoiceOption(
        val name: String,
        val label: String,
        val locale: String,
        val needsNetwork: Boolean,
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun init() {
        if (tts != null) return
        var created: TextToSpeech? = null
        created = TextToSpeech(appContext) { status ->
            mainHandler.post { created?.let { handleInit(it, status) } }
        }
        tts = created
    }

    private fun handleInit(engine: TextToSpeech, status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _message.value = "系统语音引擎初始化失败，去系统设置里看看 TTS"
            return
        }
        runCatching {
            val result = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                _message.value = "系统缺少中文语音包，音色可能不可用"
            }
        }
        refreshVoices(engine)
        _ready.value = true
    }

    fun refreshVoices() {
        tts?.let { refreshVoices(it) }
    }

    private fun refreshVoices(engine: TextToSpeech) {
        val all = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val chinese = all.filter { it.locale.language == Locale.CHINESE.language }
        val source = if (chinese.isNotEmpty()) chinese else all
        _voices.value = source.map { voice ->
            VoiceOption(
                name = voice.name,
                label = "${voice.locale.displayName} · ${voice.name.substringAfterLast('.')}",
                locale = voice.locale.toLanguageTag(),
                needsNetwork = voice.isNetworkConnectionRequired,
            )
        }.sortedBy { it.label }
    }

    fun speak(text: String, persona: Persona) {
        speakRaw(text, persona.voiceName, persona.speechPitch, persona.speechRate)
    }

    fun preview(voiceName: String?, pitch: Float, rate: Float) {
        speakRaw("你好呀，我是你的伙伴。今天想聊点什么？", voiceName, pitch, rate)
    }

    fun speakRaw(text: String, voiceName: String?, pitch: Float, rate: Float) {
        val engine = tts ?: return
        if (!_ready.value) {
            _message.value = "语音还在准备，稍等一下"
            return
        }
        val clean = text.replace(Regex("\\[\\[记住\\]\\][\\s\\S]*$"), "").trim()
        if (clean.isBlank()) return

        runCatching {
            engine.stop()
            engine.setSpeechRate(rate.coerceIn(0.5f, 2f))
            engine.setPitch(pitch.coerceIn(0.5f, 2f))
            if (!voiceName.isNullOrBlank()) {
                val voice: Voice? = engine.voices?.firstOrNull { it.name == voiceName }
                if (voice != null) engine.voice = voice
            }
            engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "companion-${System.currentTimeMillis()}")
        }.onFailure {
            _message.value = "朗读失败：${it.message ?: "未知错误"}"
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        _ready.value = false
    }
}
