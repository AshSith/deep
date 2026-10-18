package com.dsh.companion.net

import com.dsh.companion.data.Persona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudStt {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(persona: Persona, file: File): String = withContext(Dispatchers.IO) {
        val base = persona.sttBaseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/audio/transcriptions")) {
            base
        } else {
            "$base/audio/transcriptions"
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", persona.sttModel.ifBlank { "whisper-1" })
            .addFormDataPart("language", "zh")
            .addFormDataPart(
                "file",
                "audio.m4a",
                file.asRequestBody("audio/mp4".toMediaType()),
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${persona.sttApiKey}")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} · ${errorMessage(text)}")
            }
            runCatching {
                json.parseToJsonElement(text)
                    .jsonObject["text"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trim()
            }.getOrNull().orEmpty()
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
        return detail ?: body.take(200).ifBlank { "识别失败" }
    }
}
