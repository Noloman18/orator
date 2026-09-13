package com.noloxtreme.tts.reader.export

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.ExportError
import com.noloxtreme.tts.reader.domain.SettingsRepository
import com.noloxtreme.tts.reader.playback.FileSynthesizer
import com.noloxtreme.tts.reader.playback.SpeechConfiguration
import com.noloxtreme.tts.reader.playback.SpeechInitialization
import com.noloxtreme.tts.reader.playback.SynthesisResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Exports a full document to a single AAC (.m4a) file:
 *
 * 1. reads the book text from Room (no WorkManager input-size limits),
 * 2. synthesizes each bounded chunk to a WAV in cache via [FileSynthesizer],
 * 3. concatenates/encodes all WAVs with [M4aAssembler] (Media3 Transformer),
 * 4. publishes the result to Music/Orator via [MediaStoreSaver],
 * 5. notifies completion or failure.
 *
 * Runs as a foreground service (mediaProcessing on Android 15+, dataSync below)
 * so the OS does not reap it while the user leaves the app or the screen is off.
 * A per-segment state file gives cheap resume after process death or reboot:
 * completed WAVs are skipped on the next run. User cancellation cleans up.
 *
 * Every failure path carries the underlying exception (class name and message)
 * in the notification and result data, and the full stack trace is logged under
 * the [TAG] tag for logcat diagnosis.
 */
@HiltWorker
class AudioExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val documentRepository: DocumentRepository,
    private val contentRepository: ContentRepository,
    private val settingsRepository: SettingsRepository,
    private val synthesizer: FileSynthesizer,
    private val assembler: M4aAssembler,
    private val saver: MediaStoreSaver,
    private val notifier: ExportNotifier
) : CoroutineWorker(appContext, params) {

    private var bookTitle: String = ""

    override suspend fun doWork(): Result {
        // Any throwable that escapes exportDocument becomes a FAILED result whose
        // detail names the exception, so the user-visible message is diagnosable.
        notifier.ensureChannels()
        return try {
            exportDocument()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, "Audio export failed", error)
            failure(ExportError.UNKNOWN, describe(error))
        } finally {
            synthesizer.shutdown()
        }
    }

    private suspend fun exportDocument(): Result {
        val documentId = DocumentId(inputData.getString(ExportKeys.DOCUMENT_ID).orEmpty())
        if (documentId.value.isBlank()) return failure(ExportError.DOCUMENT_MISSING)
        val document = documentRepository.getDocument(documentId)
        if (document == null) return failure(ExportError.DOCUMENT_MISSING)
        bookTitle = document.title
        val chunks = AudioChunker.chunk(
            contentRepository.allParagraphs(documentId).map { it.text }
        )
        if (chunks.isEmpty()) {
            return failure(ExportError.UNKNOWN, "No readable text was found in this book")
        }

        val workDir = File(applicationContext.cacheDir, "audio-export/${documentId.value}")
        val segmentsDir = File(workDir, "segments").apply { mkdirs() }
        val stateFile = File(workDir, "next-segment.txt")
        val settings = settingsRepository.observeSettings().first()
        val configuration = SpeechConfiguration(
            languageTag = document.languageTag,
            voiceName = settings.voiceName,
            rate = settings.speechRate,
            pitch = settings.speechPitch
        )

        updateProgress(0, document.title, documentId.value, ExportStage.SYNTHESIZING)
        when (synthesizer.initialize(configuration)) {
            SpeechInitialization.Ready -> Unit
            SpeechInitialization.EngineUnavailable -> return failure(ExportError.TTS_UNAVAILABLE)
            SpeechInitialization.LanguageUnavailable -> {
                return failure(ExportError.TTS_LANGUAGE_MISSING)
            }
        }

        try {
            var next = stateFile.readLongOr(0L).coerceAtMost(chunks.size.toLong())
            while (next < chunks.size) {
                checkNotStopped(workDir)
                val index = next.toInt()
                val wav = File(segmentsDir, "%06d.wav".format(index))
                if (!wav.exists() || wav.length() == 0L) {
                    val result = synthesizeWithRetry(
                        chunks[index].text,
                        "export-$index",
                        wav,
                        configuration
                    )
                    if (result != SynthesisResult.Success) {
                        val detail = (result as? SynthesisResult.Failure)
                            ?.let { "TTS error code ${it.reason}" }
                        return failure(ExportError.SYNTHESIS_FAILED, detail)
                    }
                }
                next = (index + 1).toLong()
                stateFile.writeLong(next)
                val percent = (next * SYNTHESIS_PROGRESS_WEIGHT / chunks.size)
                    .toInt()
                    .coerceIn(0, SYNTHESIS_PROGRESS_WEIGHT)
                updateProgress(percent, document.title, documentId.value, ExportStage.SYNTHESIZING)
            }

            val wavFiles = (0 until chunks.size).map { index ->
                File(segmentsDir, "%06d.wav".format(index))
            }
            val output = File(workDir, "export.m4a")
            // A previous encoding attempt may have left a partial container. It
            // cannot be resumed safely, while the synthesized WAV segments can.
            output.delete()
            updateProgress(
                SYNTHESIS_PROGRESS_WEIGHT,
                document.title,
                documentId.value,
                ExportStage.ENCODING
            )
            val assembleResult = assembler.assemble(wavFiles, output) { encodingPercent ->
                val overallPercent = SYNTHESIS_PROGRESS_WEIGHT +
                    (encodingPercent * ENCODING_PROGRESS_WEIGHT / 100)
                updateProgress(
                    overallPercent.coerceAtMost(MAX_IN_PROGRESS_PERCENT),
                    document.title,
                    documentId.value,
                    ExportStage.ENCODING
                )
            }
            if (assembleResult.isFailure) {
                output.delete()
                val exception = assembleResult.exceptionOrNull()
                return failure(ExportError.ENCODING_FAILED, exception?.let { describe(it) })
            }
            checkNotStopped(workDir)
            updateProgress(
                MAX_IN_PROGRESS_PERCENT,
                document.title,
                documentId.value,
                ExportStage.SAVING
            )
            when (val saved = saver.publish(output, document.title)) {
                is MediaStoreSaver.SaveResult.Saved -> {
                    notifier.completedNotification(saved.uri, saved.displayName)
                    workDir.deleteRecursively()
                    return Result.success(
                        workDataOf(
                            ExportKeys.CONTENT_URI to saved.uri,
                            ExportKeys.DISPLAY_NAME to saved.displayName
                        )
                    )
                }
                is MediaStoreSaver.SaveResult.Failed -> {
                    workDir.deleteRecursively()
                    return failure(saved.error, saved.detail)
                }
            }
        } catch (cancelled: CancellationException) {
            // User or system cancellation: drop partial work, let WorkManager mark it cancelled.
            workDir.deleteRecursively()
            throw cancelled
        }
    }

    private suspend fun synthesizeWithRetry(
        text: String,
        utteranceId: String,
        destination: File,
        configuration: SpeechConfiguration
    ): SynthesisResult {
        var result = synthesizer.synthesizeToFile(utteranceId, text, destination)
        if (result != SynthesisResult.Success &&
            synthesizer.initialize(configuration) == SpeechInitialization.Ready
        ) {
            result = synthesizer.synthesizeToFile("$utteranceId-r", text, destination)
        }
        return result
    }

    private fun checkNotStopped(workDir: File) {
        if (isStopped) {
            workDir.deleteRecursively()
            throw CancellationException("Audio export cancelled")
        }
    }

    /**
     * Leaves room below 100% for AAC packaging and MediaStore publication.
     * The progress notification therefore reaches 100% only when its separate
     * completion notification is shown, never while more work remains.
     */
    private suspend fun updateProgress(
        percent: Int,
        title: String,
        documentId: String,
        stage: ExportStage
    ) {
        val safePercent = percent.coerceIn(0, MAX_IN_PROGRESS_PERCENT)
        setProgress(workDataOf(ExportKeys.PERCENT to safePercent))
        setForeground(notifier.foregroundInfo(safePercent, title, documentId, stage))
    }

    private fun failure(error: ExportError, detail: String? = null): Result {
        notifier.failedNotification(error, bookTitle, detail)
        val data = if (detail.isNullOrBlank()) {
            workDataOf(ExportKeys.ERROR to error.name)
        } else {
            workDataOf(
                ExportKeys.ERROR to error.name,
                ExportKeys.ERROR_DETAIL to detail.take(MAX_DETAIL_CHARS)
            )
        }
        return Result.failure(data)
    }

    private fun describe(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
            .ifBlank { error.javaClass.simpleName }
        return "${error.javaClass.simpleName}: $message".take(MAX_DETAIL_CHARS)
    }

    private fun File.readLongOr(default: Long): Long =
        runCatching { readText().trim().toLong() }.getOrDefault(default)

    private fun File.writeLong(value: Long) {
        writeText(value.toString())
    }

    private companion object {
        const val TAG = "OratorExport"
        const val MAX_DETAIL_CHARS = 300
        const val SYNTHESIS_PROGRESS_WEIGHT = 85
        const val ENCODING_PROGRESS_WEIGHT = 14
        const val MAX_IN_PROGRESS_PERCENT = 99
    }
}
