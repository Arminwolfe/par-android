package com.par.chat.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.par.chat.data.ChatEngine
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Plays one voice note at a time. */
object VoicePlayer {
    private var player: MediaPlayer? = null
    val playing = MutableStateFlow<String?>(null)

    suspend fun toggle(engine: ChatEngine, fileId: String) {
        if (playing.value == fileId) {
            stop()
            return
        }
        stop()
        val file = engine.voiceFile(fileId)
        val p = MediaPlayer()
        p.setDataSource(file.absolutePath)
        p.setOnCompletionListener { stop() }
        p.prepare()
        p.start()
        player = p
        playing.value = fileId
    }

    fun stop() {
        try { player?.release() } catch (_: Exception) {}
        player = null
        playing.value = null
    }
}

/** Records AAC audio in an MP4 container (the host recognises it as a voice note). */
class VoiceRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean = try {
        val f = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = f
        startedAt = System.currentTimeMillis()
        true
    } catch (e: Exception) {
        release()
        false
    }

    val seconds: Long get() = if (recorder == null) 0 else (System.currentTimeMillis() - startedAt) / 1000

    /** Stops recording; returns the file, or null if it was too short or failed. */
    fun stop(): File? {
        val f = file
        val ok = try { recorder?.stop(); true } catch (e: RuntimeException) { false }
        val long = System.currentTimeMillis() - startedAt >= 700
        release()
        return if (ok && long && f != null && f.length() > 0) f else { f?.delete(); null }
    }

    fun cancel() {
        val f = file
        try { recorder?.stop() } catch (_: Exception) {}
        release()
        f?.delete()
    }

    private fun release() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }
}
