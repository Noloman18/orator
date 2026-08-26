package com.noloxtreme.tts.reader.export

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
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

    override suspend fun doWork(): Result {
        val documentId = DocumentId(inputData.getString(ExportKeys.DOCUMENT_ID).orEmpty())
        if (documentId.value.isBlank()) return failure(ExportError.DOCUMENT_MISSING)
        val document = documentRepository.getDocument(documentId)
        if (document == null) return failure(ExportError.DOCUMENT_MISSING)
        val chunks = AudioChunker.chunk(
            contentRepository.allParagraphs(documentId).map { it.text }
        )
        if (chunks.isEmpty()) return failure(ExportError.UNKNOWN)

        notifier.ensureChannels()
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

        setForeground(notifier.foregroundInfo(0, document.title, documentId.value))
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
                    val result = synthesizeWithRetry(chunks[index].text, "export-$index", wav, configuration)
                    if (result != SynthesisResult.Success) {
                        return failure(ExportError.SYNTHESIS_FAILED)
                    }
                }
                next = (index + 1).toLong()
                stateFile.writeLong(next)
                val percent = (next * 100 / chunks.size).toInt().coerceIn(0, 100)
                setProgress(workDataOf(ExportKeys.PERCENT to percent))
                setForeground(notifier.foregroundInfo(percent, document.title, documentId.value))
            }

            val wavFiles = (0 until chunks.size).map { index ->
                File(segmentsDir, "%06d.wav".format(index))
            }
            val output = File(workDir, "export.m4a")
            if (assembler.assemble(wavFiles, output).isFailure) {
                return failure(ExportError.ENCODING_FAILED)
            }
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
                is MediaStoreSaver.SaveResult.Failed -> return failure(saved.error)
            }
        } catch (cancelled: CancellationException) {
            // User or system cancellation: drop partial work, let WorkManager mark it cancelled.
            workDir.deleteRecursively()
            throw cancelled
        } catch (error: Throwable) {
            workDir.deleteRecursively()
            return failure(ExportError.UNKNOWN)
        } finally {
            synthesizer.shutdown()
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

    private fun failure(error: ExportError): Result =
        Result.failure(workDataOf(ExportKeys.ERROR to error.name))

    private fun File.readLongOr(default: Long): Long =
        runCatching { readText().trim().toLong() }.getOrDefault(default)

    private fun File.writeLong(value: Long) {
        writeText(value.toString())
    }
}
