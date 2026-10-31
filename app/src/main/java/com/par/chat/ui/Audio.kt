package com.par.chat.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.par.chat.data.ChatEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Plays one voice note at a time, with seeking and speed (1x / 1.5x / 2x). */
object VoicePlayer {
    private var player: MediaPlayer? = null
    val playing = MutableStateFlow<String?>(null)
    val positionMs = MutableStateFlow(0)
    val durationMs = MutableStateFlow(0)
    val speed = MutableStateFlow(1f)
    private var ticker: kotlinx.coroutines.Job? = null

    suspend fun toggle(engine: ChatEngine, fileId: String) {
        if (playing.value == fileId) {
            player?.let { if (it.isPlaying) it.pause() else it.start() }
            return
        }
        stop()
        val file = engine.voiceFile(fileId)
        val p = MediaPlayer()
        p.setDataSource(file.absolutePath)
        p.setOnCompletionListener { stop() }
        p.prepare()
        applySpeed(p)
        p.start()
        player = p
        durationMs.value = p.duration
        playing.value = fileId
        ticker = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            while (true) { positionMs.value = try { p.currentPosition } catch (_: Exception) { 0 }; delay(200) }
        }
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        p.seekTo((fraction.coerceIn(0f, 1f) * p.duration).toInt())
        positionMs.value = p.currentPosition
    }

    fun cycleSpeed() {
        speed.value = when (speed.value) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        player?.let { applySpeed(it) }
    }

    private fun applySpeed(p: MediaPlayer) {
        try {
            val wasPlaying = p.isPlaying
            p.playbackParams = p.playbackParams.setSpeed(speed.value)
            if (!wasPlaying) p.pause() // setting params can start playback
        } catch (_: Exception) {}
    }

    fun isPaused(): Boolean = player?.isPlaying != true

    fun stop() {
        ticker?.cancel()
        try { player?.release() } catch (_: Exception) {}
        player = null
        playing.value = null
        positionMs.value = 0
    }
}

/** Records AAC audio in an MP4 container (the host recognises it as a voice note). */
class VoiceRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    /** Length of the last recording; sent along because the file itself may lack it. */
    var lastMs = 0L
        private set

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
        lastMs = System.currentTimeMillis() - startedAt
        val long = lastMs >= 700
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
