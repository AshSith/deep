package com.dsh.companion.net

import android.util.Base64
import com.dsh.companion.data.ChatMessage
import com.dsh.companion.data.Persona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun stream(persona: Persona, history: List<ChatMessage>): Flow<String> = flow {
        if (persona.apiKey.isBlank()) {
            throw IOException("还没有填 API Key，去「设置 → 角色」里填一下")
        }

        val messages = buildJsonArray {
            addJsonObject {
                put("role", "system")
                put("content", persona.systemPrompt)
            }
            history
                .filter { it.content.isNotBlank() && !it.error && it.role != "system" }
                .takeLast(persona.historyLimit.coerceIn(2, 60))
                .forEach { message ->
                    addJsonObject {
                        put("role", message.role)
                        val imageFile = message.attachmentUri
                            ?.takeIf { persona.visionEnabled && message.attachmentType == "image" }
                            ?.let(::File)
                        val imageBytes = imageFile
                            ?.takeIf { it.isFile && it.length() in 1L..6_000_000L }
                            ?.let { runCatching { it.readBytes() }.getOrNull() }
                        if (imageBytes != null) {
                            put(
                                "content",
                                buildJsonArray {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", message.content.ifBlank { "请看这张图片" })
                                    }
                                    addJsonObject {
                                        put("type", "image_url")
                                        put(
                                            "image_url",
                                            buildJsonObject {
                                                put(
                                                    "url",
                                                    "data:image/jpeg;base64," +
                                                        Base64.encodeToString(imageBytes, Base64.NO_WRAP),
                                                )
                                            },
                                        )
                                    }
                                },
                            )
                        } else {
                            put("content", message.content)
                        }
                    }
                }
        }

        val payload = buildJsonObject {
            put("model", persona.model)
            put("stream", true)
            put("temperature", persona.temperature)
            put("max_tokens", persona.maxTokens)
            put("messages", messages)
        }

        val request = Request.Builder()
            .url(persona.endpoint)
            .addHeader("Authorization", "Bearer ${persona.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val call = client.newCall(request)
        val cancelOnExit = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw IOException(errorMessage(response.code, body))
                }

                val source = response.body?.source() ?: throw IOException("服务器没有返回内容")
                while (currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break

                    val chunk = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                        ?: continue
                    val choice = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
                    val delta = choice["delta"]?.jsonObject ?: continue
                    val content = delta["content"]?.jsonPrimitive?.contentOrNull
                    if (!content.isNullOrEmpty()) emit(content)
                }
            }
        } finally {
            cancelOnExit?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
        return "HTTP $code · ${detail ?: body.take(300).ifBlank { "请求失败" }}"
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
