package com.noloxtreme.tts.reader.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.util.UUID

data class VoiceNoteRecording(
    val file: File,
    val durationMillis: Long
)

/** Records a short AAC/M4A clip into cache until the note is saved or discarded. */
class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun start(): Boolean {
        if (recorder != null) return false
        val directory = File(context.cacheDir, "voice-note-drafts")
        if (!directory.exists() && !directory.mkdirs()) return false
        val file = File(directory, "${UUID.randomUUID()}.m4a")
        val nextRecorder = newMediaRecorder()
        return try {
            nextRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = nextRecorder
            outputFile = file
            startedAtMillis = SystemClock.elapsedRealtime()
            true
        } catch (_: Throwable) {
            runCatching { nextRecorder.reset() }
            nextRecorder.release()
            file.delete()
            false
        }
    }

    fun stop(): VoiceNoteRecording? {
        val activeRecorder = recorder ?: return null
        val file = outputFile
        val durationMillis = (SystemClock.elapsedRealtime() - startedAtMillis).coerceAtLeast(0L)
        recorder = null
        outputFile = null
        return try {
            activeRecorder.stop()
            file?.takeIf { it.isFile && it.length() > 0L }
                ?.let { VoiceNoteRecording(it, durationMillis) }
        } catch (_: RuntimeException) {
            file?.delete()
            null
        } finally {
            activeRecorder.reset()
            activeRecorder.release()
        }
    }

    fun discard() {
        val activeRecorder = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        runCatching { activeRecorder?.stop() }
        runCatching { activeRecorder?.reset() }
        runCatching { activeRecorder?.release() }
        file?.delete()
    }

    @Suppress("DEPRECATION")
    private fun newMediaRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        MediaRecorder()
    }
}

class VoiceNotePlayer(
    private val onPlaybackEnded: () -> Unit
) {
    private var mediaPlayer: MediaPlayer? = null
    var playingNoteId: String? = null
        private set

    fun toggle(noteId: String, file: File): Boolean {
        if (playingNoteId == noteId) {
            stop()
            return false
        }
        stop(notify = false)
        return runCatching {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    this@VoiceNotePlayer.stop(notify = true)
                }
                start()
            }
            playingNoteId = noteId
        }.isSuccess
    }

    fun stop() = stop(notify = true)

    private fun stop(notify: Boolean) {
        mediaPlayer?.run {
            runCatching { stop() }
            release()
        }
        mediaPlayer = null
        val hadPlayback = playingNoteId != null
        playingNoteId = null
        if (notify && hadPlayback) onPlaybackEnded()
    }
}
