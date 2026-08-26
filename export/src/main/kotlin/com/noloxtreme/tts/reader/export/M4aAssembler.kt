package com.noloxtreme.tts.reader.export

import android.content.Context
import android.media.MediaCodecInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Concatenates the per-utterance WAV files into one AAC-LC (.m4a) file using
 * Media3 Transformer — the maintained, platform-level replacement for FFmpeg.
 * The output is AAC-LC at 64 kbps; sample rate and channel count follow the
 * engine's WAV format (all chunks in a run share the same engine and format).
 *
 * Transformer falls back to the main looper when built on a thread without
 * one, and then requires every call on that thread — so all interaction here
 * happens on the main thread while the actual encode work stays internal.
 */
@Singleton
class M4aAssembler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun assemble(wavFiles: List<File>, outputFile: File): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedAudioEncoderSettings(
                        AudioEncoderSettings.Builder()
                            .setBitrate(EXPORT_BITRATE)
                            .setProfile(MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                            .build()
                    )
                    .build()
                val transformer = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .build()
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(Result.success(Unit))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exception: ExportException
                    ) {
                        if (continuation.isActive) continuation.resume(Result.failure(exception))
                    }
                })
                continuation.invokeOnCancellation {
                    // Cancellation runs on the cancelling thread; Transformer must
                    // stay on the main thread.
                    Handler(Looper.getMainLooper()).post { transformer.cancel() }
                }
                try {
                    val items = wavFiles.map { item ->
                        EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(item))).build()
                    }
                    val composition = Composition.Builder(
                        EditedMediaItemSequence.withAudioFrom(items)
                    ).build()
                    transformer.start(composition, outputFile.absolutePath)
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resume(Result.failure(error))
                }
            }
        }

    private companion object {
        const val EXPORT_BITRATE = 64_000
    }
}
