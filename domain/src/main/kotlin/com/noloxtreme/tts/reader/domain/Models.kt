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

/**
 * The visual read-mode position inside an EPUB: which spine item (including
 * the synthesized cover slot at index 0) and which packed page of it.
 */
data class ReaderPosition(
    val spineIndex: Int,
    val pageIndex: Int
) {
    init {
        require(spineIndex >= 0)
        require(pageIndex >= 0)
    }
}

/** A user-created text and/or voice note anchored to one exact place in a book. */
data class BookNote(
    val id: String,
    val documentId: DocumentId,
    val position: DocumentPosition,
    val text: String?,
    /** Relative path inside private app storage; never a shared-storage URI. */
    val voiceRelativePath: String?,
    val voiceDurationMillis: Long?,
    val createdAtEpochMillis: Long
) {
    init {
        require(id.isNotBlank())
        require(text?.isNotBlank() == true || voiceRelativePath != null)
        require(voiceRelativePath == null || voiceDurationMillis != null)
        require(voiceDurationMillis == null || voiceDurationMillis >= 0L)
        require(createdAtEpochMillis >= 0L)
    }
}

const val FAST_JUMP_SENTENCE_COUNT = 5

/** Rates offered by the playback speed button, in the order it cycles through them. */
val SPEECH_RATE_CYCLE = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

/** Returns the next faster preset, looping from 2× back to normal speed. */
fun nextSpeechRate(currentRate: Float): Float =
    SPEECH_RATE_CYCLE.firstOrNull { it > currentRate } ?: SPEECH_RATE_CYCLE.first()

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
    /** Increases speech speed through [SPEECH_RATE_CYCLE] and persists the selected rate. */
    data object IncreaseSpeechRate : NarrationCommand
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
    /** Every paragraph of the document in narration order; used by full-book audio export. */
    suspend fun allParagraphs(id: DocumentId): List<Paragraph>
    suspend fun sentenceBefore(id: DocumentId, position: DocumentPosition): DocumentPosition
    suspend fun sentenceAfter(id: DocumentId, position: DocumentPosition): DocumentPosition
}

interface ProgressRepository {
    fun observeProgress(id: DocumentId): Flow<ReadingProgress?>
    suspend fun getProgress(id: DocumentId): ReadingProgress?
    suspend fun saveProgress(progress: ReadingProgress)
}

/**
 * Persists where the visual read mode was left, per document, so reopening a
 * book lands on the same chapter and page. Distinct from [ProgressRepository],
 * which tracks the narration position.
 */
interface ReaderPositionRepository {
    fun observe(id: DocumentId): Flow<ReaderPosition?>
    suspend fun save(id: DocumentId, position: ReaderPosition)
}

interface BookNoteRepository {
    fun observeNotes(documentId: DocumentId): Flow<List<BookNote>>
    suspend fun save(note: BookNote)
    /** Removes one note. Its optional private voice file is owned by the reader layer. */
    suspend fun delete(noteId: String): Boolean
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
