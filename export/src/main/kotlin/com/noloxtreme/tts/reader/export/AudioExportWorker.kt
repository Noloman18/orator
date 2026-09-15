package com.noloxtreme.tts.reader.export

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
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
 * Exports a document to one AAC (.m4a) file without constructing a
 * multi-gigabyte source WAV. Sentence-aligned chunks are synthesized in
 * bounded batches, each batch is encoded, and the resulting AAC samples are
 * remuxed into one final M4A without a second lossy encode.
 *
 * All temporary files live under one cache work directory. It is removed on
 * success, failure and cancellation; a new attempt first removes remnants of
 * a process that was killed before its cleanup block could run.
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
    private val remuxer: M4aRemuxer,
    private val saver: MediaStoreSaver,
    private val notifier: ExportNotifier
) : CoroutineWorker(appContext, params) {

    private var bookTitle: String = ""

    override suspend fun getForegroundInfo(): ForegroundInfo {
        notifier.ensureChannels()
        return notifier.preparingForegroundInfo(
            inputData.getString(ExportKeys.DOCUMENT_ID).orEmpty()
        )
    }

    override suspend fun doWork(): Result {
        notifier.ensureChannels()
        return try {
            exportDocument()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Audio export could not start its foreground service", error)
            if (error.javaClass.name == FOREGROUND_START_NOT_ALLOWED_EXCEPTION) {
                failure(ExportError.FOREGROUND_START_NOT_ALLOWED)
            } else {
                failure(ExportError.UNKNOWN, describe(error))
            }
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
            ?: return failure(ExportError.DOCUMENT_MISSING)
        bookTitle = document.title
        val chunks = AudioChunker.chunk(
            contentRepository.allParagraphs(documentId).map { it.text }
        )
        if (chunks.isEmpty()) {
            return failure(ExportError.UNKNOWN, "No readable text was found in this book")
        }

        val workDir = File(applicationContext.cacheDir, "audio-export/${documentId.value}")
        if (workDir.exists() && !workDir.deleteRecursively()) {
            return failure(ExportError.STORAGE_FAILED, "Could not clear a previous export attempt")
        }
        if (!workDir.mkdirs()) {
            return failure(ExportError.STORAGE_FAILED, "Could not create export working storage")
        }

        try {
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

            val encodedPartsDir = File(workDir, "encoded-parts").apply { mkdirs() }
            val encodedParts = mutableListOf<File>()
            var nextChunk = 0
            var batchIndex = 0
            while (nextChunk < chunks.size) {
                checkNotStopped()
                val batchDir = File(workDir, "batch-%04d".format(batchIndex)).apply { mkdirs() }
                val segmentsDir = File(batchDir, "segments").apply { mkdirs() }
                val batchWavFiles = mutableListOf<File>()
                var batchWordCount = 0
                var batchWavBytes = 0L

                while (
                    nextChunk < chunks.size &&
                    (batchWavFiles.isEmpty() || !AudioExportBatcher.shouldFinishBatch(
                        batchWordCount,
                        batchWavBytes
                    ))
                ) {
                    checkNotStopped()
                    val chunk = chunks[nextChunk]
                    val wav = File(segmentsDir, "%06d.wav".format(chunk.index))
                    val synthesis = synthesizeWithRetry(
                        chunk.text,
                        "export-${chunk.index}",
                        wav,
                        configuration
                    )
                    if (synthesis != SynthesisResult.Success) {
                        val detail = when (synthesis) {
                            is SynthesisResult.Failure -> "TTS error code ${synthesis.reason}"
                            SynthesisResult.Timeout -> "Speech synthesis timed out"
                            SynthesisResult.Success -> null
                        }
                        return failure(ExportError.SYNTHESIS_FAILED, detail)
                    }
                    val validationError = WavConcatenator.validationError(wav)
                    if (validationError != null) {
                        return failure(
                            ExportError.SYNTHESIS_FAILED,
                            "Speech engine produced an invalid WAV segment: $validationError"
                        )
                    }
                    batchWavFiles += wav
                    batchWordCount += AudioExportBatcher.wordCount(chunk.text)
                    batchWavBytes += WavConcatenator.normalizedPcm16DataSize(wav)
                    nextChunk++
                    updateProgress(
                        batchProgress(nextChunk, chunks.size),
                        document.title,
                        documentId.value,
                        ExportStage.SYNTHESIZING
                    )
                }

                val batchProgress = batchProgress(nextChunk, chunks.size)
                val mergedWav = File(batchDir, "speech.wav")
                try {
                    updateProgress(
                        batchProgress,
                        document.title,
                        documentId.value,
                        ExportStage.ENCODING
                    )
                    WavConcatenator.concatenate(batchWavFiles, mergedWav) {
                        checkNotStopped()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    return failure(ExportError.ENCODING_FAILED, describe(error))
                }
                // The source WAV is no longer needed once it has been encoded.
                segmentsDir.deleteRecursively()

                val encodedPart = File(encodedPartsDir, "%04d.m4a".format(batchIndex))
                val assembly = assembler.assemble(listOf(mergedWav), encodedPart) {
                    updateProgress(
                        batchProgress,
                        document.title,
                        documentId.value,
                        ExportStage.ENCODING
                    )
                }
                if (assembly.isFailure) {
                    encodedPart.delete()
                    return failure(
                        ExportError.ENCODING_FAILED,
                        assembly.exceptionOrNull()?.let(::describe)
                    )
                }
                mergedWav.delete()
                batchDir.deleteRecursively()
                encodedParts += encodedPart
                batchIndex++
            }

            val output = File(workDir, "export.m4a")
            val remuxed = remuxer.remux(encodedParts, output) { remuxProgress ->
                val overall = BATCH_PROCESSING_MAX_PERCENT +
                    (remuxProgress * REMUX_PROGRESS_WEIGHT / 100)
                updateProgress(
                    overall.coerceAtMost(MAX_IN_PROGRESS_PERCENT),
                    document.title,
                    documentId.value,
                    ExportStage.REMUXING
                )
            }
            if (remuxed.isFailure) {
                output.delete()
                return failure(
                    ExportError.ENCODING_FAILED,
                    remuxed.exceptionOrNull()?.let(::describe)
                )
            }

            checkNotStopped()
            updateProgress(
                MAX_IN_PROGRESS_PERCENT,
                document.title,
                documentId.value,
                ExportStage.SAVING
            )
            return when (val saved = saver.publish(output, document.title)) {
                is MediaStoreSaver.SaveResult.Saved -> {
                    notifier.completedNotification(saved.uri, saved.displayName)
                    Result.success(
                        workDataOf(
                            ExportKeys.CONTENT_URI to saved.uri,
                            ExportKeys.DISPLAY_NAME to saved.displayName
                        )
                    )
                }
                is MediaStoreSaver.SaveResult.Failed -> failure(saved.error, saved.detail)
            }
        } finally {
            // Nothing in cache is user content. Always remove partial source WAVs,
            // encoded batches and remux output from unsuccessful work.
            if (workDir.exists() && !workDir.deleteRecursively()) {
                Log.w(TAG, "Could not fully clean export working directory: ${workDir.absolutePath}")
            }
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

    private fun checkNotStopped() {
        if (isStopped) throw CancellationException("Audio export cancelled")
    }

    /** Progress of synthesis, per-batch encode and final AAC remuxing. */
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

    private fun batchProgress(completedChunks: Int, totalChunks: Int): Int =
        (completedChunks.toLong() * BATCH_PROCESSING_MAX_PERCENT / totalChunks)
            .toInt()
            .coerceIn(0, BATCH_PROCESSING_MAX_PERCENT)

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

    private fun describe(error: Throwable): String =
        generateSequence(error) { it.cause }
            .map { cause ->
                val message = cause.message?.trim().orEmpty()
                    .ifBlank { cause.javaClass.simpleName }
                "${cause.javaClass.simpleName}: $message"
            }
            .distinct()
            .joinToString(" ← ")
            .take(MAX_DETAIL_CHARS)

    private companion object {
        const val TAG = "OratorExport"
        const val MAX_DETAIL_CHARS = 300
        const val BATCH_PROCESSING_MAX_PERCENT = 90
        const val REMUX_PROGRESS_WEIGHT = 8
        const val MAX_IN_PROGRESS_PERCENT = 99
        const val FOREGROUND_START_NOT_ALLOWED_EXCEPTION =
            "android.app.ForegroundServiceStartNotAllowedException"
    }
}
