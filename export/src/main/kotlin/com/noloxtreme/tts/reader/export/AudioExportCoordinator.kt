package com.noloxtreme.tts.reader.export

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.noloxtreme.tts.reader.domain.AudioExporter
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.ExportError
import com.noloxtreme.tts.reader.domain.ExportState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Enqueues and observes audio-export jobs. One unique work chain per document:
 * a new export replaces any finished one, and the UI guards against starting a
 * second export while one is running.
 */
@Singleton
class AudioExportCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: ExportNotifier
) : AudioExporter {

    override fun export(documentId: DocumentId) {
        notifier.ensureChannels()
        val request = OneTimeWorkRequestBuilder<AudioExportWorker>()
            .setInputData(workDataOf(ExportKeys.DOCUMENT_ID to documentId.value))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(documentId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancel(documentId: DocumentId) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(documentId))
    }

    override fun observe(documentId: DocumentId): Flow<ExportState> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(uniqueName(documentId))
            .map { infos -> infos.lastOrNull()?.toExportState() ?: ExportState.Idle }
            .distinctUntilChanged()

    private fun WorkInfo.toExportState(): ExportState = when (state) {
        WorkInfo.State.ENQUEUED -> ExportState.Enqueued
        WorkInfo.State.RUNNING -> ExportState.Running(progress.getInt(ExportKeys.PERCENT, 0))
        WorkInfo.State.SUCCEEDED -> ExportState.Succeeded(
            displayName = outputData.getString(ExportKeys.DISPLAY_NAME).orEmpty(),
            contentUri = outputData.getString(ExportKeys.CONTENT_URI).orEmpty()
        )
        WorkInfo.State.FAILED -> ExportState.Failed(
            outputData.getString(ExportKeys.ERROR)
                ?.let { name -> runCatching { ExportError.valueOf(name) }.getOrDefault(ExportError.UNKNOWN) }
                ?: ExportError.UNKNOWN
        )
        WorkInfo.State.CANCELLED -> ExportState.Cancelled
        WorkInfo.State.BLOCKED -> ExportState.Enqueued
    }

    companion object {
        fun uniqueName(documentId: String): String = "audio-export-$documentId"
        fun uniqueName(documentId: DocumentId): String = uniqueName(documentId.value)
    }
}
