package com.dsh.companion.net

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import com.dsh.companion.data.Persona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible /audio/speech client.
 * If [Persona.ttsVoice2] is set, the same text is synthesized twice and both
 * tracks are played together (a simple two-voice chorus).
 */
class CloudTts(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var playerA: MediaPlayer? = null
    private var playerB: MediaPlayer? = null

    suspend fun speak(persona: Persona, text: String) {
        val clean = text
            .replace(Regex("\\[\\[记住\\]\\][\\s\\S]*$"), "")
            .trim()
        if (clean.isBlank()) return

        val voiceA = persona.ttsVoice.ifBlank { "alloy" }
        val fileA = synthesize(persona, clean, voiceA)
        val fileB = persona.ttsVoice2.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { voice -> runCatching { synthesize(persona, clean, voice) }.getOrNull() }

        withContext(Dispatchers.Main) {
            play(fileA, fileB, persona.ttsMix.coerceIn(0f, 1f))
        }
    }

    fun stop() {
        playerA?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        playerB?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        playerA = null
        playerB = null
    }

    private fun play(fileA: File, fileB: File?, mix: Float) {
        stop()
        val volumeA = if (fileB != null) (1f - mix).coerceIn(0.05f, 0.95f) else 0.95f
        playerA = createPlayer(fileA, volumeA)
        if (fileB != null) {
            playerB = createPlayer(fileB, mix.coerceIn(0.05f, 0.95f))
        }
    }

    private fun createPlayer(file: File, volume: Float): MediaPlayer {
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setDataSource(file.absolutePath)
        player.setVolume(volume, volume)
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener {
            it.release()
            file.delete()
        }
        player.setOnErrorListener { mp, _, _ ->
            runCatching { mp.release() }
            file.delete()
            true
        }
        player.prepareAsync()
        return player
    }

    private suspend fun synthesize(persona: Persona, text: String, voice: String): File =
        withContext(Dispatchers.IO) {
            val base = persona.ttsBaseUrl.trim().trimEnd('/')
            val url = if (base.endsWith("/audio/speech")) base else "$base/audio/speech"

            val payload = buildJsonObject {
                put("model", persona.ttsModel.ifBlank { "tts-1" })
                put("input", text)
                put("voice", voice)
                put("response_format", "mp3")
                put("speed", persona.speechRate.coerceIn(0.5f, 2f))
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${persona.ttsApiKey}")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code} · ${errorMessage(body)}")
                }
                val bytes = response.body?.bytes() ?: throw IOException("服务器没有返回音频")
                if (bytes.isEmpty()) throw IOException("返回的音频是空的")
                val file = File(context.cacheDir, "tts-${UUID.randomUUID()}.mp3")
                file.writeBytes(bytes)
                file
            }
        }

    private fun errorMessage(body: String): String {
        val detail = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
        return detail ?: body.take(200).ifBlank { "语音合成失败" }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
