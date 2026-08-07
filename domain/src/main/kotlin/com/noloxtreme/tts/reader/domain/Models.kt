package com.noloxtreme.tts.reader.domain

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@JvmInline
value class DocumentId(val value: String)

data class Document(
    val id: DocumentId,
    val title: String,
    val originalFileName: String,
    val mimeType: String,
    val sha256: String,
    val languageTag: String?,
    val totalCharacterCount: Long,
    val sectionCount: Int,
    val importedAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long?
)

data class Section(
    val documentId: DocumentId,
    val sectionIndex: Int,
    val title: String?,
    val firstParagraphIndex: Int,
    val lastParagraphIndex: Int,
    val absoluteStart: Long,
    val absoluteEnd: Long
)

data class Paragraph(
    val documentId: DocumentId,
    val paragraphIndex: Int,
    val sectionIndex: Int,
    val text: String,
    val absoluteStart: Long,
    val absoluteEnd: Long
) {
    init {
        require(paragraphIndex >= 0)
        require(sectionIndex >= 0)
        require(text.isNotBlank())
        require(absoluteStart >= 0L)
        require(absoluteEnd == absoluteStart + text.length)
    }
}

data class DocumentPosition(
    val paragraphIndex: Int,
    val offsetInParagraph: Int,
    val absoluteOffset: Long
) {
    init {
        require(paragraphIndex >= 0)
        require(offsetInParagraph >= 0)
        require(absoluteOffset >= 0L)
    }
}

data class SpokenRange(
    val paragraphIndex: Int,
    val startInParagraph: Int,
    val endExclusiveInParagraph: Int
) {
    init {
        require(paragraphIndex >= 0)
        require(startInParagraph >= 0)
        require(endExclusiveInParagraph > startInParagraph)
    }
}

data class ReadingProgress(
    val documentId: DocumentId,
    val position: DocumentPosition,
    val updatedAtEpochMillis: Long,
    val completed: Boolean
)

const val FAST_JUMP_SENTENCE_COUNT = 5

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

enum class LineHeightPreference {
    COMPACT,
    COMFORTABLE,
    SPACIOUS
}

data class OratorSettings(
    val voiceName: String? = null,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val readerFontSizeSp: Int = 20,
    val lineHeight: LineHeightPreference = LineHeightPreference.COMFORTABLE,
    val followSpokenText: Boolean = true,
    val theme: ThemePreference = ThemePreference.SYSTEM
)

data class ImportSource(
    val opaqueHandle: String,
    val displayName: String,
    val mimeType: String,
    val reportedSizeBytes: Long?
)

sealed interface ImportState {
    data object AwaitingPicker : ImportState
    data class Copying(val bytesCopied: Long, val totalBytes: Long?) : ImportState
    data object Parsing : ImportState
    data object Saving : ImportState
    data class Success(val documentId: DocumentId) : ImportState
    data class ExistingDocument(val documentId: DocumentId) : ImportState
    data class Failure(val error: ImportError) : ImportState
}

enum class ImportError {
    UNSUPPORTED_FORMAT,
    FILE_TOO_LARGE,
    SOURCE_UNREADABLE,
    UNSUPPORTED_ENCODING,
    MALFORMED_DOCUMENT,
    EPUB_ENCRYPTED,
    EPUB_LIMIT_EXCEEDED,
    PDF_ENCRYPTED,
    PDF_LIMIT_EXCEEDED,
    NO_READABLE_TEXT,
    STORAGE_FULL,
    DATABASE_ERROR,
    CANCELLED
}

sealed interface NarrationState {
    data object Idle : NarrationState
    data class Preparing(
        val documentId: DocumentId,
        val requestedPosition: DocumentPosition
    ) : NarrationState
    data class Playing(
        val documentId: DocumentId,
        val safePosition: DocumentPosition,
        val activeRange: SpokenRange?
    ) : NarrationState
    data class Paused(
        val documentId: DocumentId,
        val resumePosition: DocumentPosition,
        val activeRange: SpokenRange?
    ) : NarrationState
    data class Completed(val documentId: DocumentId) : NarrationState
    data class Error(
        val documentId: DocumentId?,
        val code: PlaybackError,
        val recoverable: Boolean
    ) : NarrationState
}

enum class PlaybackError {
    TTS_UNAVAILABLE,
    TTS_LANGUAGE_MISSING,
    TTS_SPEAK_FAILED,
    AUDIO_FOCUS_DENIED,
    DOCUMENT_MISSING
}

sealed interface NarrationCommand {
    data class Load(val documentId: DocumentId) : NarrationCommand
    data object Play : NarrationCommand
    data class Pause(val userInitiated: Boolean = true) : NarrationCommand
    data object PreviousSentence : NarrationCommand
    data object NextSentence : NarrationCommand
    data class JumpSentences(
        val previous: Boolean,
        val count: Int
    ) : NarrationCommand {
        init {
            require(count > 0)
        }
    }
    data class SeekTo(val position: DocumentPosition) : NarrationCommand
    data object Stop : NarrationCommand
    data object RestartCompleted : NarrationCommand
}

interface DocumentRepository {
    fun observeLibrary(): Flow<List<Document>>
    fun observeDocument(id: DocumentId): Flow<Document?>
    suspend fun getDocument(id: DocumentId): Document?
    suspend fun updateLastOpened(id: DocumentId, epochMillis: Long)
    suspend fun deleteDocument(id: DocumentId)
}

interface DocumentImporter {
    fun import(source: ImportSource): Flow<ImportState>
    suspend fun cancelActiveImport()
}

interface ContentRepository {
    fun pagedParagraphs(
        id: DocumentId,
        initialParagraphIndex: Int
    ): Flow<PagingData<Paragraph>>

    suspend fun paragraph(id: DocumentId, index: Int): Paragraph?
    suspend fun paragraphContaining(id: DocumentId, absoluteOffset: Long): Paragraph?
    suspend fun section(id: DocumentId, index: Int): Section?
    suspend fun sentenceBefore(id: DocumentId, position: DocumentPosition): DocumentPosition
    suspend fun sentenceAfter(id: DocumentId, position: DocumentPosition): DocumentPosition
}

interface ProgressRepository {
    fun observeProgress(id: DocumentId): Flow<ReadingProgress?>
    suspend fun getProgress(id: DocumentId): ReadingProgress?
    suspend fun saveProgress(progress: ReadingProgress)
}

interface SettingsRepository {
    fun observeSettings(): Flow<OratorSettings>
    suspend fun updateSettings(transform: (OratorSettings) -> OratorSettings)
}

interface TimeProvider {
    fun nowEpochMillis(): Long
}

interface NarrationController {
    val state: StateFlow<NarrationState>
    fun dispatch(command: NarrationCommand)
}
