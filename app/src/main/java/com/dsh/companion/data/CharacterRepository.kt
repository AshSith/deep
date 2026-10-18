package com.dsh.companion.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import kotlinx.serialization.decodeFromString
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

data class Character(
    val id: String,
    val meta: CharacterMeta,
    val assetRoot: String?,
    val dir: File?,
) {
    val isBuiltIn: Boolean get() = assetRoot != null
    val name: String get() = meta.name.ifBlank { id }
}

class CharacterRepository(private val context: Context) {
    private val charactersDir = File(context.filesDir, "characters").apply { mkdirs() }

    fun list(): List<Character> {
        val result = mutableListOf<Character>()
        context.assets.list("characters")
            ?.sorted()
            ?.forEach { id -> readBuiltIn(id)?.let { result.add(it) } }

        charactersDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.forEach { folder ->
                if (result.none { it.id == folder.name }) {
                    readImported(folder)?.let { result.add(it) }
                }
            }
        return result
    }

    fun get(id: String?): Character? {
        if (id.isNullOrBlank()) return null
        return list().firstOrNull { it.id == id }
    }

    fun loadClip(character: Character, clipName: String): Drawable? {
        val meta = character.meta
        val clip = meta.clips[clipName]
            ?: meta.clips[meta.defaultClip]
            ?: meta.clips.values.firstOrNull()
            ?: return null

        return runCatching {
            val drawable = ImageDecoder.decodeDrawable(sourceFor(character, clip.file)) { decoder, info, _ ->
                val maxHeight = 900
                if (info.size.height > maxHeight) {
                    val scale = maxHeight.toFloat() / info.size.height
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        maxHeight,
                    )
                }
            }
            if (drawable is AnimatedImageDrawable) {
                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            }
            drawable
        }.getOrNull()
    }

    fun previewBitmap(character: Character): Bitmap? {
        val meta = character.meta
        val clip = meta.clips[meta.defaultClip] ?: meta.clips.values.firstOrNull() ?: return null
        return runCatching {
            ImageDecoder.decodeBitmap(sourceFor(character, clip.file)) { decoder, _, _ ->
                decoder.setTargetSampleSize(2)
            }
        }.getOrElse {
            runCatching {
                val stream = if (character.assetRoot != null) {
                    context.assets.open("${character.assetRoot}/${clip.file}")
                } else {
                    File(character.dir, clip.file).inputStream()
                }
                stream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }

    fun importZip(uri: Uri): Character {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0L

        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("打不开这个文件")
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (entry.isDirectory) continue
                if (name.startsWith("/") || name.contains("..")) continue
                if (ALLOWED.none { name.endsWith(it, ignoreCase = true) }) continue
                val bytes = zip.readBytes()
                total += bytes.size
                if (total > MAX_TOTAL) throw IOException("角色包太大了（上限 300MB）")
                entries[name] = bytes
            }
        }

        val metaKey = entries.keys
            .filter { it == "character.json" || it.endsWith("/character.json") }
            .minByOrNull { it.count { ch -> ch == '/' } }
            ?: throw IOException("角色包里没有 character.json")

        val text = String(entries.getValue(metaKey), Charsets.UTF_8)
        val rawMeta = JsonStore.json.decodeFromString<CharacterMeta>(text)

        val prefix = if (metaKey.contains('/')) metaKey.substringBeforeLast('/') + "/" else ""

        rawMeta.clips.values.forEach { clip ->
            val rel = clip.file.replace('\\', '/')
            if (!entries.containsKey(prefix + rel)) {
                throw IOException("角色包缺少动画文件：${clip.file}")
            }
        }
        if (rawMeta.clips.isEmpty()) throw IOException("角色包没有 clips")

        val baseId = rawMeta.id.ifBlank { "custom" }
            .replace(Regex("[^A-Za-z0-9_-]"), "-")
            .take(32)
            .ifBlank { "custom" }

        var folderName = baseId
        var index = 2
        while (File(charactersDir, folderName).exists()) {
            folderName = "$baseId-$index"
            index += 1
        }

        val temp = File(charactersDir, "$folderName.tmp")
        temp.deleteRecursively()
        temp.mkdirs()

        try {
            entries.forEach { (name, bytes) ->
                if (!name.startsWith(prefix)) return@forEach
                val rel = name.removePrefix(prefix)
                if (rel.isBlank() || rel == "character.json") return@forEach
                val target = File(temp, rel)
                val canonical = target.canonicalFile
                if (!canonical.path.startsWith(temp.canonicalFile.path)) return@forEach
                canonical.parentFile?.mkdirs()
                canonical.writeBytes(bytes)
            }

            val meta = rawMeta.copy(id = folderName)
            JsonStore.write(File(temp, "character.json"), meta)

            val finalDir = File(charactersDir, folderName)
            if (!temp.renameTo(finalDir)) {
                temp.copyRecursively(finalDir, overwrite = true)
                temp.deleteRecursively()
            }
            return Character(folderName, meta, null, finalDir)
        } catch (error: Exception) {
            temp.deleteRecursively()
            throw error
        }
    }

    fun delete(character: Character): Boolean {
        if (character.isBuiltIn) return false
        return character.dir?.deleteRecursively() ?: false
    }

    private fun sourceFor(character: Character, file: String): ImageDecoder.Source {
        val root = character.assetRoot
        return if (root != null) {
            ImageDecoder.createSource(context.assets, "$root/$file")
        } else {
            ImageDecoder.createSource(File(character.dir, file))
        }
    }

    private fun readBuiltIn(id: String): Character? = runCatching {
        val text = context.assets.open("characters/$id/character.json")
            .use { it.readBytes().toString(Charsets.UTF_8) }
        val meta = JsonStore.json.decodeFromString<CharacterMeta>(text)
        Character(id, meta, "characters/$id", null)
    }.getOrNull()

    private fun readImported(folder: File): Character? = runCatching {
        val meta = JsonStore.json.decodeFromString<CharacterMeta>(
            File(folder, "character.json").readText(Charsets.UTF_8)
        )
        Character(folder.name, meta, null, folder)
    }.getOrNull()

    private companion object {
        val ALLOWED = listOf(".webp", ".png", ".json", ".md", ".txt")
        const val MAX_TOTAL = 300L * 1024 * 1024
    }
}
