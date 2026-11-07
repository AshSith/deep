package com.dsh.companion.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.bertvits2_infer_wrapper.impl.BertVITS2SimpleInferImpl

/**
 * Local Bert-VITS2 2.3 engine. The model assets are bundled in this personal
 * build; no network is used for synthesis.
 */
class BertTts(context: Context) {
    private val appContext = context.applicationContext
    private val engine = BertVITS2SimpleInferImpl(appContext)
    private var ready = false
    private var currentTrack: AudioTrack? = null

    suspend fun init(): Boolean {
        if (ready) return true
        ready = runCatching { engine.init() }.getOrDefault(false)
        return ready
    }

    fun isReady(): Boolean = ready

    fun speakers(): List<String> = runCatching { engine.getSpkNameList() }.getOrDefault(emptyList())

    suspend fun speak(
        text: String,
        speaker: String,
        speed: Float,
        volume: Float,
        pitch: Float,
    ): Boolean {
        if (!ready) return false
        val result = runCatching {
            engine.setAudioLengthScale((1f / speed.coerceIn(0.5f, 2f)).coerceIn(0.5f, 2f))
            engine.infer(text, speaker)
        }.getOrNull() ?: return false
        val pcm = result.first ?: return false
        val sampleRate = result.second
        if (pcm.isEmpty() || sampleRate <= 0) return false
        play(pcm, sampleRate, volume, pitch)
        return true
    }

    private fun play(pcm: FloatArray, sampleRate: Int, volume: Float, pitch: Float) {
        stop()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes((pcm.size * 4).coerceAtLeast(4096))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        currentTrack = track
        runCatching {
            track.setVolume(volume.coerceIn(0.1f, 1f))
            if (pitch != 1f) {
                track.playbackParams = track.playbackParams.setPitch(pitch.coerceIn(0.7f, 1.4f))
            }
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            track.play()
        }.onFailure {
            stop()
        }
    }

    fun stop() {
        currentTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        currentTrack = null
    }

    fun release() {
        stop()
        runCatching { engine.release() }
        ready = false
    }
}
