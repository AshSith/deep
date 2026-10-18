package com.dsh.companion.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object JsonStore {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    inline fun <reified T> read(file: File, fallback: T): T {
        if (!file.isFile) return fallback
        return runCatching { json.decodeFromString<T>(file.readText(Charsets.UTF_8)) }
            .getOrDefault(fallback)
    }

    inline fun <reified T> write(file: File, value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(value), Charsets.UTF_8)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }
}
