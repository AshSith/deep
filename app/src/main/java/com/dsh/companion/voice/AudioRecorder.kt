package com.dsh.companion.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start(): File {
        cancel()
        val file = File(context.cacheDir, "stt-${System.currentTimeMillis()}.m4a")
        val engine = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            legacyRecorder()
        }
        try {
            engine.setAudioSource(MediaRecorder.AudioSource.MIC)
            engine.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            engine.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            engine.setAudioChannels(1)
            engine.setAudioSamplingRate(16_000)
            engine.setAudioEncodingBitRate(64_000)
            engine.setOutputFile(file.absolutePath)
            engine.prepare()
            engine.start()
        } catch (error: Exception) {
            runCatching { engine.release() }
            file.delete()
            throw error
        }
        recorder = engine
        output = file
        return file
    }

    fun stop(): File? {
        val engine = recorder ?: return null
        val file = output
        recorder = null
        output = null
        return try {
            engine.stop()
            file
        } catch (error: Exception) {
            file?.delete()
            null
        } finally {
            runCatching { engine.release() }
        }
    }

    fun cancel() {
        val engine = recorder
        recorder = null
        output?.delete()
        output = null
        if (engine != null) {
            runCatching { engine.stop() }
            runCatching { engine.release() }
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyRecorder(): MediaRecorder = MediaRecorder()
}
