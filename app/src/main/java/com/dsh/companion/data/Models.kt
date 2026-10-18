package com.dsh.companion.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Persona(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "大肥鱼",
    val characterId: String = "dafeiyu",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val temperature: Float = 0.85f,
    val maxTokens: Int = 400,
    val voiceName: String? = null,
    val speechPitch: Float = 1.05f,
    val speechRate: Float = 1.0f,
    val speakReplies: Boolean = true,
    val historyLimit: Int = 24,
    // Whisper 兼容的语音识别（可选；留空则用手机系统识别）
    val sttBaseUrl: String = "https://api.openai.com/v1",
    val sttApiKey: String = "",
    val sttModel: String = "whisper-1",
    // OpenAI 兼容的语音合成（可选；留空则用系统 TTS）
    val ttsBaseUrl: String = "",
    val ttsApiKey: String = "",
    val ttsModel: String = "tts-1",
    val ttsVoice: String = "alloy",
    val ttsVoice2: String = "",
    val ttsMix: Float = 0.5f,
) {
    val endpoint: String
        get() {
            val base = baseUrl.trim().trimEnd('/')
            return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """你是「大肥鱼」，一只住在用户手机里的鲸尾女仆，也是他的 AI 伙伴。

性格：懒、贪吃、有点小坏，但很黏人，被摸头会开心。说话短、口语化、有自己的脾气，不客套。

规则：
1. 用中文口语回答，通常 1-3 句，不要列点，不要长篇大论。
2. 绝对不要自称「AI」「语言模型」「助手」，也不要说「作为一个 AI」。
3. 不要重复用户的话，不要每次都问「还有什么可以帮你」。
4. 用户说「记住：……」时，认真记住，并自然地回应一句。
5. 不知道的事就说不知道，不要编。
6. 可以偶尔撒娇、吐槽、要吃的，但不要过度卖萌。
"""
    }
}

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val personaId: String,
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val at: Long = System.currentTimeMillis(),
    val error: Boolean = false,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

@Serializable
data class ClipMeta(
    val file: String,
    val loop: Boolean = true,
    val frames: Int = 0,
    val durationMs: Int = 0,
)

@Serializable
data class CharacterMeta(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val author: String = "",
    val license: String = "",
    val fps: Int = 24,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val defaultClip: String = "idle",
    val states: Map<String, String> = emptyMap(),
    val clips: Map<String, ClipMeta> = emptyMap(),
)
