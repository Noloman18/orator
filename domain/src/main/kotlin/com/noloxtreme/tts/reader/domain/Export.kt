package com.noloxtreme.tts.reader.domain

import kotlinx.coroutines.flow.Flow

/**
 * The lifecycle of a background "export book to audio" job, observed from the UI.
 * Content URIs are carried as strings so the domain stays free of Android types.
 */
sealed interface ExportState {
    /** No export has ever been started for this document. */
    data object Idle : ExportState

    /** The work request is queued and will start shortly. */
    data object Enqueued : ExportState

    /** The worker is synthesizing/encoding; [percent] is 0..100. */
    data class Running(val percent: Int) : ExportState

    /** The finished audio file has been published to device storage. */
    data class Succeeded(val displayName: String, val contentUri: String) : ExportState

    data class Failed(val error: ExportError) : ExportState

    data object Cancelled : ExportState
}

enum class ExportError {
    DOCUMENT_MISSING,
    TTS_UNAVAILABLE,
    TTS_LANGUAGE_MISSING,
    SYNTHESIS_FAILED,
    ENCODING_FAILED,
    STORAGE_FAILED,
    UNKNOWN
}

/**
 * Kicks off and observes full-document audio exports. The heavy work runs in a
 * WorkManager foreground-service worker so the UI never blocks and the job
 * survives app backgrounding, process death, and device reboot.
 */
interface AudioExporter {
    fun export(documentId: DocumentId)

    fun cancel(documentId: DocumentId)

    fun observe(documentId: DocumentId): Flow<ExportState>
}
