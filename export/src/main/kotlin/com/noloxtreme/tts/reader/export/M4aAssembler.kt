package com.noloxtreme.tts.reader.export

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concatenates the per-utterance WAV files into one AAC (.m4a) file using
 * Media3 Transformer — the maintained, platform-level replacement for FFmpeg.
 * The output is AAC in an M4A container. Sample rate and channel count follow
 * the engine's WAV format (all chunks in a run share the same engine and
 * format). The encoder settings are intentionally left to the device: older
 * Samsung codecs can reject an otherwise valid forced AAC profile during
 * configuration, whereas Media3's default factory negotiates a supported AAC
 * configuration and falls back when needed.
 *
 * Transformer falls back to the main looper when built on a thread without
 * one, and then requires every call on that thread — so all interaction here
 * happens on the main thread while the actual encode work stays internal.
 */
@Singleton
class M4aAssembler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun assemble(
        wavFiles: List<File>,
        outputFile: File,
        onProgress: suspend (Int) -> Unit = {}
    ): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            val completion = CompletableDeferred<Result<Unit>>()
            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(DefaultEncoderFactory.Builder(context).build())
                .build()
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    completion.complete(Result.success(Unit))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exception: ExportException
                ) {
                    completion.complete(Result.failure(exception))
                }
            })

            try {
                val items = wavFiles.map { item ->
                    EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(item))).build()
                }
                val composition = Composition.Builder(
                    EditedMediaItemSequence.withAudioFrom(items)
                ).build()
                transformer.start(composition, outputFile.absolutePath)

                var lastProgress = -1
                while (!completion.isCompleted) {
                    ensureActive()
                    val progressHolder = ProgressHolder()
                    if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val progress = progressHolder.progress.coerceIn(0, 100)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                    delay(PROGRESS_POLL_INTERVAL_MS)
                }
                completion.await()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                transformer.cancel()
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }

    private companion object {
        const val PROGRESS_POLL_INTERVAL_MS = 500L
    }
}
