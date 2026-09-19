package com.noloxtreme.tts.reader.ui

import android.content.Context
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.AudioExporter
import com.noloxtreme.tts.reader.domain.BookNote
import com.noloxtreme.tts.reader.domain.BookNoteRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.EPUB_MIME_TYPE
import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.EpubSpineContent
import com.noloxtreme.tts.reader.domain.EpubTocEntry
import com.noloxtreme.tts.reader.domain.ExportError
import com.noloxtreme.tts.reader.domain.ExportState
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.ReadingProgress
import com.noloxtreme.tts.reader.domain.SpokenRange
import com.noloxtreme.tts.reader.domain.TimeProvider
import com.noloxtreme.tts.reader.domain.blockAtNarratableRank
import com.noloxtreme.tts.reader.domain.narratableBlocksBefore
import com.noloxtreme.tts.reader.domain.narrationOffsetInBlock
import com.noloxtreme.tts.reader.domain.narrationOffsetInParagraph
import com.noloxtreme.tts.reader.domain.narrationText
import com.noloxtreme.tts.reader.domain.usecase.LoadCoverPresence
import com.noloxtreme.tts.reader.domain.usecase.LoadImageResource
import com.noloxtreme.tts.reader.domain.usecase.LoadSpineContent
import com.noloxtreme.tts.reader.domain.usecase.LoadSpineCount
import com.noloxtreme.tts.reader.domain.usecase.LoadTableOfContents
import com.noloxtreme.tts.reader.domain.usecase.IncreaseSpeechRate
import com.noloxtreme.tts.reader.domain.usecase.ReaderContent
import com.noloxtreme.tts.reader.domain.usecase.ObserveReaderContent
import com.noloxtreme.tts.reader.domain.usecase.ObserveReadingProgress
import com.noloxtreme.tts.reader.domain.usecase.ObserveSettings
import com.noloxtreme.tts.reader.domain.usecase.OpenDocument
import com.noloxtreme.tts.reader.domain.usecase.PauseNarration
import com.noloxtreme.tts.reader.domain.usecase.RestartCompletedDocument
import com.noloxtreme.tts.reader.domain.usecase.SaveReadingProgress
import com.noloxtreme.tts.reader.domain.usecase.SeekNarration
import com.noloxtreme.tts.reader.domain.usecase.SkipSentence
import com.noloxtreme.tts.reader.domain.usecase.StartOrResumeNarration
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

sealed interface ReaderLoadState {
    data object Loading : ReaderLoadState
    data object Ready : ReaderLoadState
    data object MissingDocument : ReaderLoadState
}

/** EPUB-specific visual read-mode state; generic documents use the shared Read Mode state only. */
data class EpubReadingState(
    val toc: List<EpubTocEntry>,
    val spineCount: Int,
    val currentSpineIndex: Int,
    val content: EpubSpineContent?,
    val loadingContent: Boolean,
    val unavailable: Boolean,
    /** Number of spine slots taken by a synthesized cover page at index 0. */
    val coverOffset: Int
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel @Inject constructor(
    observeReaderContent: ObserveReaderContent,
    private val observeReadingProgress: ObserveReadingProgress,
    observeSettings: ObserveSettings,
    private val openDocument: OpenDocument,
    private val startOrResumeNarration: StartOrResumeNarration,
    private val pauseNarration: PauseNarration,
    private val seekNarration: SeekNarration,
    private val skipSentence: SkipSentence,
    private val increaseSpeechRateUseCase: IncreaseSpeechRate,
    private val restartCompletedDocument: RestartCompletedDocument,
    private val contentRepository: ContentRepository,
    private val loadTableOfContents: LoadTableOfContents,
    private val loadSpineContent: LoadSpineContent,
    private val loadSpineCount: LoadSpineCount,
    private val loadCoverPresence: LoadCoverPresence,
    private val loadImageResource: LoadImageResource,
    private val saveReadingProgress: SaveReadingProgress,
    val narrationController: NarrationController,
    private val audioExporter: AudioExporter,
    private val bookNoteRepository: BookNoteRepository,
    private val timeProvider: TimeProvider,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val pageRequest = MutableStateFlow<DocumentId?>(null)
    private val initialParagraph = MutableStateFlow(0)
    private val mutableReadMode = MutableStateFlow(false)
    private val mutableVisualReading = MutableStateFlow(VisualReadingState())
    private val mutableEpubReading = MutableStateFlow<EpubReadingState?>(null)
    private val mutableJumpTarget = MutableStateFlow<PageTextAnchor?>(null)
    private val mutableTextJumpTarget = MutableStateFlow<DocumentPosition?>(null)
    private val mutableActiveWordAnchor = MutableStateFlow<PageTextRange?>(null)
    /** Keeps EPUB image bytes bounded while chapter layouts inspect their dimensions. */
    private val imageCache = object : LruCache<String, ByteArray>(EPUB_IMAGE_CACHE_MAX_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private var visualReadingJob: Job? = null
    private var visualProgressCheckpointJob: Job? = null
    private var manualEpubPositionJob: Job? = null
    private var pendingVisualProgress: VisualProgressCheckpoint? = null
    private var spineLoadRequestId = 0L

    /** Whether the reader is in silent Read Mode rather than audio narration mode. */
    val readMode: StateFlow<Boolean> = mutableReadMode.asStateFlow()

    /** Current silent-reading cursor, play state, and visual pace. */
    internal val visualReading: StateFlow<VisualReadingState> = mutableVisualReading.asStateFlow()

    val epubReading: StateFlow<EpubReadingState?> = mutableEpubReading.asStateFlow()

    /** A pending block target for the scrollable EPUB reader; cleared once it is resolved. */
    val readerJumpTarget: StateFlow<PageTextAnchor?> = mutableJumpTarget.asStateFlow()

    /** A one-shot exact position for the generic text reader to bring into view. */
    val textJumpTarget: StateFlow<DocumentPosition?> = mutableTextJumpTarget.asStateFlow()

    /** The active word mapped into the current EPUB chapter's styled text. */
    val readerActiveWord: StateFlow<PageTextRange?> = mutableActiveWordAnchor.asStateFlow()

    private val narrationTransport = NarrationTransport(
        previousSentenceAction = ::previousSentence,
        nextSentenceAction = ::nextSentence,
        increaseSpeedAction = ::increaseSpeechRate
    )
    private val visualReadTransport = VisualReadingTransport(
        previousWordAction = ::previousReadUnit,
        nextWordAction = ::nextReadUnit,
        increasePaceAction = ::increaseVisualReadingPace
    )
    private val readerContent = pageRequest.flatMapLatest { id ->
        if (id == null) emptyFlow()
        else observeReaderContent.execute(id)
    }
    private val content: StateFlow<ReaderContent?> = readerContent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val document: StateFlow<Document?> = content
        .map { it?.document }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Read Mode moves by the selected visual unit; narration moves by sentence. */
    val transport: StateFlow<ReaderTransport> = combine(mutableReadMode, document) { reading, currentDocument ->
        when {
            !reading -> narrationTransport
            else -> visualReadTransport
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), narrationTransport)

    val loadState: StateFlow<ReaderLoadState> = content
        .map { value ->
            when {
                value == null -> ReaderLoadState.Loading
                value.document == null -> ReaderLoadState.MissingDocument
                else -> ReaderLoadState.Ready
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderLoadState.Loading)

    val progress: StateFlow<ReadingProgress?> = pageRequest.flatMapLatest { id ->
        if (id == null) emptyFlow()
        else observeReadingProgress.execute(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<OratorSettings> = observeSettings.execute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OratorSettings())

    val narration: StateFlow<NarrationState> = narrationController.state

    private val mutableExportMessages = MutableSharedFlow<ExportMessage>(extraBufferCapacity = 8)
    private val exportMessageGate = ExportMessageGate()

    /** Background export state for the loaded document; [ExportState.Idle] when none. */
    val exportState: StateFlow<ExportState> = pageRequest
        .flatMapLatest { id ->
            if (id == null) flowOf(ExportState.Idle) else audioExporter.observe(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportState.Idle)

    /** One-shot snackbar events for terminal export outcomes. */
    val exportMessages: SharedFlow<ExportMessage> = mutableExportMessages.asSharedFlow()

    private val mutableNoteMessages = MutableSharedFlow<NoteMessage>(extraBufferCapacity = 4)
    val noteMessages: SharedFlow<NoteMessage> = mutableNoteMessages.asSharedFlow()

    val notes: StateFlow<List<BookNote>> = pageRequest.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else bookNoteRepository.observeNotes(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun exportAudio() {
        pageRequest.value?.let { id ->
            audioExporter.export(id)
            mutableExportMessages.tryEmit(ExportMessage.Started)
        }
    }

    fun cancelExport() {
        pageRequest.value?.let { id -> audioExporter.cancel(id) }
    }

    /** Captures the current narration or silent-reading location for a new note. */
    fun currentNotePosition(): DocumentPosition = when {
        mutableReadMode.value -> mutableVisualReading.value.activeWord?.position
            ?: progress.value?.position
        else -> narrationController.state.value.positionOrNull() ?: progress.value?.position
    } ?: DocumentPosition(paragraphIndex = 0, offsetInParagraph = 0, absoluteOffset = 0L)

    /** Freezes the current reading point before a note is written or recorded. */
    fun captureNotePositionAndPause(): DocumentPosition {
        val position = currentNotePosition()
        if (mutableReadMode.value) {
            stopVisualReading(clearCursor = false)
        } else {
            pauseNarration.execute()
        }
        return position
    }

    fun saveNote(
        text: String,
        voiceRecording: VoiceNoteRecording?,
        position: DocumentPosition
    ) {
        val documentId = pageRequest.value ?: return
        val normalizedText = text.trim().takeIf { it.isNotEmpty() }
        if (normalizedText == null && voiceRecording == null) return
        viewModelScope.launch {
            val noteId = UUID.randomUUID().toString()
            var storedVoice: StoredVoice? = null
            try {
                storedVoice = withContext(Dispatchers.IO) {
                    voiceRecording?.let { storeVoiceRecording(documentId, noteId, it) }
                }
                bookNoteRepository.save(
                    BookNote(
                        id = noteId,
                        documentId = documentId,
                        position = position,
                        text = normalizedText,
                        voiceRelativePath = storedVoice?.relativePath,
                        voiceDurationMillis = storedVoice?.durationMillis,
                        createdAtEpochMillis = timeProvider.nowEpochMillis()
                    )
                )
                withContext(Dispatchers.IO) { voiceRecording?.file?.delete() }
                mutableNoteMessages.emit(NoteMessage.Saved)
            } catch (_: Throwable) {
                withContext(Dispatchers.IO) {
                    storedVoice?.file?.delete()
                }
                mutableNoteMessages.emit(NoteMessage.Failed)
            }
        }
    }

    /** Removes a note and its optional recording without touching other notes for the book. */
    fun deleteNote(note: BookNote) {
        if (note.documentId != pageRequest.value) return
        viewModelScope.launch {
            val removed = try {
                withContext(Dispatchers.IO) {
                    if (!bookNoteRepository.delete(note.id)) return@withContext false
                    deleteStoredVoice(note)
                    true
                }
            } catch (_: Throwable) {
                false
            }
            mutableNoteMessages.emit(if (removed) NoteMessage.Deleted else NoteMessage.DeleteFailed)
        }
    }

    /** Returns the reader to the position a note was captured from. */
    fun goToNote(position: DocumentPosition) {
        if (mutableReadMode.value) {
            seekVisualToAbsoluteOffset(position.absoluteOffset)
        } else {
            mutableTextJumpTarget.value = position
            seekNarration.execute(position)
        }
    }

    init {
        viewModelScope.launch {
            exportState.collect { state ->
                exportMessageGate.onState(state)?.let { message ->
                    mutableExportMessages.tryEmit(message)
                }
            }
        }
    }

    val sectionTitle: StateFlow<String?> = combine(pageRequest, narrationController.state) { id, state ->
        id to state
    }.map { (id, state) ->
        if (id == null || state.documentIdOrNull() != id) {
            null
        } else {
            val paragraphIndex = state.paragraphIndexOrNull() ?: return@map null
            contentRepository.paragraph(id, paragraphIndex)
                ?.let { paragraph -> contentRepository.section(id, paragraph.sectionIndex)?.title }
                ?.takeIf { it.isNotBlank() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val paragraphs: Flow<PagingData<Paragraph>> = pageRequest.flatMapLatest { id ->
        if (id == null) emptyFlow()
        else contentRepository.pagedParagraphs(id, initialParagraph.value)
    }.cachedIn(viewModelScope)

    fun load(id: DocumentId) {
        if (pageRequest.value == id) return
        viewModelScope.launch {
            stopVisualReading(clearCursor = true)
            mutableReadMode.value = false
            mutableEpubReading.value = null
            mutableJumpTarget.value = null
            mutableTextJumpTarget.value = null
            mutableActiveWordAnchor.value = null
            val savedProgress = observeReadingProgress.execute(id).first()
            initialParagraph.value = savedProgress?.position?.paragraphIndex ?: 0
            pageRequest.value = id
            openDocument.execute(id)
            narrationController.dispatch(NarrationCommand.Load(id))
        }
    }

    fun retryLoad() {
        pageRequest.value?.let { id ->
            pageRequest.value = null
            load(id)
        }
    }

    private fun storeVoiceRecording(
        documentId: DocumentId,
        noteId: String,
        recording: VoiceNoteRecording
    ): StoredVoice {
        require(recording.file.isFile && recording.file.length() > 0L) { "Voice recording is empty" }
        val notesRoot = File(appContext.filesDir, "voice-notes").canonicalFile
        check(notesRoot.exists() || notesRoot.mkdirs()) { "Unable to create voice note storage" }
        val documentDirectory = File(notesRoot, documentId.value).canonicalFile
        require(documentDirectory.parentFile == notesRoot) { "Invalid document note path" }
        check(documentDirectory.exists() || documentDirectory.mkdirs()) { "Unable to create document note storage" }
        val destination = File(documentDirectory, "$noteId.m4a").canonicalFile
        require(destination.parentFile == documentDirectory) { "Invalid voice note destination" }
        recording.file.copyTo(destination, overwrite = false)
        return StoredVoice(
            relativePath = "voice-notes/${documentId.value}/$noteId.m4a",
            durationMillis = recording.durationMillis,
            file = destination
        )
    }

    /** Deletes only the file path that this note could have created. */
    private fun deleteStoredVoice(note: BookNote) {
        val relativePath = note.voiceRelativePath ?: return
        val notesRoot = File(appContext.filesDir, "voice-notes").canonicalFile
        val documentDirectory = File(notesRoot, note.documentId.value).canonicalFile
        val voiceFile = File(appContext.filesDir, relativePath).canonicalFile
        if (
            documentDirectory.parentFile != notesRoot ||
            voiceFile.parentFile != documentDirectory ||
            voiceFile.name != "${note.id}.m4a"
        ) return

        voiceFile.delete()
        if (documentDirectory.listFiles()?.isEmpty() == true) documentDirectory.delete()
    }

    fun play() = startOrResumeNarration.execute()

    fun pause() = pauseNarration.execute()

    fun previousSentence() = skipSentence.execute(previous = true)

    fun nextSentence() = skipSentence.execute(previous = false)

    fun increaseSpeechRate() = increaseSpeechRateUseCase.execute()

    fun restart() = restartCompletedDocument.execute()

    fun seekTo(position: DocumentPosition) {
        mutableTextJumpTarget.value = position
        seekNarration.execute(position)
    }

    fun seekToAbsoluteOffset(absoluteOffset: Long) {
        val id = pageRequest.value ?: return
        viewModelScope.launch {
            val paragraph = contentRepository.paragraphContaining(
                id,
                absoluteOffset.coerceAtLeast(0L)
            ) ?: return@launch
            if (pageRequest.value != id) return@launch
            val offset = (absoluteOffset - paragraph.absoluteStart)
                .coerceIn(0L, paragraph.text.length.toLong())
                .toInt()
            seekTo(
                DocumentPosition(
                    paragraphIndex = paragraph.paragraphIndex,
                    offsetInParagraph = offset,
                    absoluteOffset = paragraph.absoluteStart + offset
                )
            )
        }
    }

    /** Moves the silent-reading cursor without seeking or starting narration. */
    fun seekVisualToAbsoluteOffset(absoluteOffset: Long) {
        if (!mutableReadMode.value) return
        val id = pageRequest.value ?: return
        stopVisualReading(clearCursor = true)
        viewModelScope.launch {
            val paragraph = contentRepository.paragraphContaining(
                id,
                absoluteOffset.coerceAtLeast(0L)
            ) ?: return@launch
            if (pageRequest.value != id || !mutableReadMode.value) return@launch
            val offset = (absoluteOffset - paragraph.absoluteStart)
                .coerceIn(0L, paragraph.text.length.toLong())
                .toInt()
            val position = DocumentPosition(
                paragraphIndex = paragraph.paragraphIndex,
                offsetInParagraph = offset,
                absoluteOffset = paragraph.absoluteStart + offset
            )
            setVisualStart(position)
            checkpointVisualProgress(position, immediately = true)
            val reading = mutableEpubReading.value
            if (reading == null) {
                mutableTextJumpTarget.value = position
                return@launch
            }
            val targetSpine = paragraph.sectionIndex + reading.coverOffset
            if (targetSpine != reading.currentSpineIndex) {
                openSpineItem(targetSpine, narrationJump = position)
            } else {
                resolveNarrationJump(id, reading.content?.blocks, position)
                    ?.let { target -> mutableJumpTarget.value = target }
            }
        }
    }

    fun enterReadMode() {
        if (mutableReadMode.value) return
        val id = pageRequest.value ?: return
        val isEpub = document.value?.mimeType == EPUB_MIME_TYPE
        mutableReadMode.value = true
        // Read Mode is deliberately silent. Never leave a TTS utterance
        // running underneath the moving visual word pointer.
        pauseNarration.execute()
        stopVisualReading(clearCursor = true)
        viewModelScope.launch {
            val startPosition = visualStartPosition(id)
            if (pageRequest.value != id || !mutableReadMode.value) return@launch
            setVisualStart(startPosition)
            if (isEpub) enterEpubReadMode(id, startPosition)
        }
    }

    private suspend fun enterEpubReadMode(id: DocumentId, startPosition: DocumentPosition?) {
        val coverOffset = if (runCatching { loadCoverPresence.execute(id) }.getOrDefault(false)) 1 else 0
        mutableEpubReading.value = EpubReadingState(
            toc = emptyList(),
            spineCount = 0,
            currentSpineIndex = 0,
            content = null,
            loadingContent = true,
            unavailable = false,
            coverOffset = coverOffset
        )
        val toc = runCatching { loadTableOfContents.execute(id) }.getOrDefault(emptyList())
        val spineCount = maxOf(
            toc.maxOfOrNull { it.spineIndex }?.plus(1) ?: 0,
            runCatching { loadSpineCount.execute(id) }.getOrDefault(0)
        )
        if (pageRequest.value != id || !mutableReadMode.value || mutableEpubReading.value == null) return
        mutableEpubReading.update {
            it?.copy(toc = toc, spineCount = spineCount)
        }
        // Enter at the silent reader's cursor, then fall back to the current
        // chapter (including the synthesized cover when present).
        val cursorSpine = startPosition?.let { position ->
            contentRepository.paragraph(id, position.paragraphIndex)
                ?.sectionIndex?.plus(coverOffset)
        }
        val startSpineIndex = when {
            cursorSpine != null && cursorSpine < spineCount -> {
                cursorSpine
            }
            else -> {
                currentSpineIndexFor(id, coverOffset)
            }
        }
        openSpineItem(startSpineIndex, narrationJump = startPosition)
    }

    fun exitReadMode() {
        stopVisualReading(clearCursor = true)
        mutableReadMode.value = false
        mutableEpubReading.value = null
        mutableJumpTarget.value = null
        mutableTextJumpTarget.value = null
        mutableActiveWordAnchor.value = null
    }

    private suspend fun visualStartPosition(id: DocumentId): DocumentPosition? {
        val narration = narrationController.state.value
        val narrated = when (narration) {
            is NarrationState.Playing -> narration.safePosition
            is NarrationState.Preparing -> narration.requestedPosition
            else -> null
        }?.takeIf { narration.documentIdOrNull() == id }
        if (narrated != null) return narrated
        return observeReadingProgress.execute(id).first()?.position
            ?: contentRepository.paragraph(id, 0)?.let { paragraph ->
                DocumentPosition(
                    paragraphIndex = paragraph.paragraphIndex,
                    offsetInParagraph = 0,
                    absoluteOffset = paragraph.absoluteStart
                )
            }
    }

    private fun setVisualStart(position: DocumentPosition?) {
        mutableVisualReading.value = VisualReadingState(
            pace = mutableVisualReading.value.pace,
            unit = mutableVisualReading.value.unit
        )
        mutableActiveWordAnchor.value = null
        visualCursorStart = position
    }

    private var visualCursorStart: DocumentPosition? = null

    /** Starts or pauses the shared silent reading pointer; it never starts TTS. */
    fun toggleVisualReading() {
        if (!mutableReadMode.value) return
        if (mutableVisualReading.value.isPlaying) {
            stopVisualReading(clearCursor = false)
            return
        }
        visualReadingJob?.cancel()
        visualReadingJob = viewModelScope.launch {
            if (mutableVisualReading.value.completed) {
                val id = pageRequest.value ?: return@launch
                setVisualStart(visualStartPosition(id))
            }
            mutableVisualReading.update { it.copy(isPlaying = true, completed = false) }
            while (isActive && mutableReadMode.value && mutableVisualReading.value.isPlaying) {
                val selection = nextVisualUnit() ?: run {
                    mutableVisualReading.update { it.copy(isPlaying = false, completed = true) }
                    break
                }
                setActiveVisualWord(selection)
                delay(
                    visualReadingUnitDelayMillis(
                        pace = mutableVisualReading.value.pace,
                        unit = mutableVisualReading.value.unit,
                        text = selection.text,
                        range = TextWordRange(
                            selection.range.startInParagraph,
                            selection.range.endExclusiveInParagraph
                        )
                    )
                )
            }
        }
    }

    fun increaseVisualReadingPace() {
        mutableVisualReading.update { reading ->
            reading.copy(pace = nextVisualReadingPace(reading.pace))
        }
    }

    /** Switches between single-word RSVP and sentence-at-a-time chunked reading. */
    fun toggleVisualReadingUnit() {
        if (!mutableReadMode.value) return
        val current = mutableVisualReading.value
        val resumePosition = current.activeWord?.position ?: visualCursorStart
        val resumePlayback = current.isPlaying
        stopVisualReading(clearCursor = false)
        mutableVisualReading.value = VisualReadingState(
            pace = current.pace,
            unit = current.unit.next()
        )
        visualCursorStart = resumePosition
        if (resumePlayback) toggleVisualReading()
    }

    fun previousReadUnit() {
        moveVisualWord(previous = true)
    }

    fun nextReadUnit() {
        moveVisualWord(previous = false)
    }

    private fun moveVisualWord(previous: Boolean) {
        stopVisualReading(clearCursor = false)
        viewModelScope.launch {
            val selection = if (previous) previousVisualUnit() else nextVisualUnit()
            if (selection != null) setActiveVisualWord(selection)
        }
    }

    private fun stopVisualReading(clearCursor: Boolean) {
        (mutableVisualReading.value.activeWord?.position ?: visualCursorStart)
            ?.let { position -> checkpointVisualProgress(position, immediately = true) }
        visualReadingJob?.cancel()
        visualReadingJob = null
        mutableVisualReading.update {
            if (clearCursor) {
                VisualReadingState(pace = it.pace, unit = it.unit)
            } else {
                it.copy(isPlaying = false)
            }
        }
        if (clearCursor) mutableActiveWordAnchor.value = null
    }

    /**
     * Records where a user stopped manually scrolling. Read Mode moves its
     * visual cursor; narration mode moves the paused resume position. Neither
     * path starts playback or requests another UI scroll.
     */
    fun rememberManualReadingPosition(position: DocumentPosition) {
        val id = pageRequest.value ?: return
        if (mutableReadMode.value) {
            if (mutableVisualReading.value.isPlaying) return
            setVisualStart(position)
            checkpointVisualProgress(position, immediately = true)
            return
        }
        val narration = narrationController.state.value
        if (narration is NarrationState.Playing || narration is NarrationState.Preparing) return
        if (narration.documentIdOrNull()?.let { it != id } == true) return
        seekNarration.execute(position)
    }

    /** Clears a generic-reader jump after the composable has positioned the list. */
    fun resolveTextJump() {
        mutableTextJumpTarget.value = null
    }

    private suspend fun nextVisualUnit(): VisualWord? {
        val id = pageRequest.value ?: return null
        val active = mutableVisualReading.value.activeWord
        val start = active?.position ?: visualCursorStart ?: visualStartPosition(id) ?: return null
        var paragraphIndex = start.paragraphIndex
        var offset = active?.range?.endExclusiveInParagraph ?: start.offsetInParagraph
        while (true) {
            val paragraph = contentRepository.paragraph(id, paragraphIndex) ?: return null
            val range = when (mutableVisualReading.value.unit) {
                VisualReadingUnit.WORD -> wordAtOrAfter(paragraph.text, offset)
                VisualReadingUnit.SENTENCE -> sentenceAtOrAfter(paragraph.text, offset)
            }
            if (range != null) return visualWord(paragraph, range)
            paragraphIndex += 1
            offset = 0
        }
    }

    private suspend fun previousVisualUnit(): VisualWord? {
        val id = pageRequest.value ?: return null
        val active = mutableVisualReading.value.activeWord
        val start = active?.position ?: visualCursorStart ?: return null
        var paragraphIndex = start.paragraphIndex
        var offset = active?.range?.startInParagraph ?: start.offsetInParagraph
        while (paragraphIndex >= 0) {
            val paragraph = contentRepository.paragraph(id, paragraphIndex) ?: return null
            val range = when (mutableVisualReading.value.unit) {
                VisualReadingUnit.WORD -> wordBefore(paragraph.text, offset)
                VisualReadingUnit.SENTENCE -> sentenceBefore(paragraph.text, offset)
            }
            if (range != null) return visualWord(paragraph, range)
            paragraphIndex -= 1
            offset = Int.MAX_VALUE
        }
        return null
    }

    private fun visualWord(paragraph: Paragraph, range: TextWordRange): VisualWord = VisualWord(
        position = DocumentPosition(
            paragraphIndex = paragraph.paragraphIndex,
            offsetInParagraph = range.start,
            absoluteOffset = paragraph.absoluteStart + range.start
        ),
        range = SpokenRange(paragraph.paragraphIndex, range.start, range.endExclusive),
        text = paragraph.text
    )

    private suspend fun setActiveVisualWord(word: VisualWord) {
        mutableVisualReading.update { it.copy(activeWord = word, completed = false) }
        checkpointVisualProgress(word.position)
        val id = pageRequest.value ?: return
        val reading = mutableEpubReading.value ?: return
        val paragraph = contentRepository.paragraph(id, word.position.paragraphIndex) ?: return
        val targetSpine = paragraph.sectionIndex + reading.coverOffset
        if (targetSpine != reading.currentSpineIndex) {
            openSpineItem(targetSpine, narrationJump = word.position)
            return
        }
        resolveVisualWordAnchor(id, reading, word)?.let { anchor ->
            mutableActiveWordAnchor.value = anchor
        }
    }

    private suspend fun resolveVisualWordAnchor(
        id: DocumentId,
        reading: EpubReadingState,
        word: VisualWord
    ): PageTextRange? {
        val blocks = reading.content?.blocks ?: return null
        val paragraph = contentRepository.paragraph(id, word.position.paragraphIndex) ?: return null
        val sectionIndex = reading.currentSpineIndex - reading.coverOffset
        if (paragraph.sectionIndex != sectionIndex) return null
        val section = contentRepository.section(id, sectionIndex) ?: return null
        val rank = paragraph.paragraphIndex - section.firstParagraphIndex
        val blockIndex = blockAtNarratableRank(blocks, rank) ?: return null
        val blockText = blocks[blockIndex].narrationText
        if (paragraph.text != blockText.trim()) return null
        val start = narrationOffsetInBlock(blockText, word.range.startInParagraph)
        val end = narrationOffsetInBlock(blockText, word.range.endExclusiveInParagraph)
        return PageTextRange(blockIndex, start, end)
    }

    fun retryCurrentSpine() {
        mutableEpubReading.value?.let { state ->
            openSpineItem(state.currentSpineIndex)
        }
    }

    fun openTocEntry(entry: EpubTocEntry) {
        val id = pageRequest.value ?: return
        openSpineItem(entry.spineIndex)
        val readingState = mutableEpubReading.value
        if (readingState != null) {
            viewModelScope.launch {
                val section = contentRepository.section(id, entry.spineIndex - readingState.coverOffset)
                    ?: return@launch
                setVisualStart(
                    DocumentPosition(
                        paragraphIndex = section.firstParagraphIndex,
                        offsetInParagraph = 0,
                        absoluteOffset = section.absoluteStart
                    )
                )
            }
        }
    }

    fun nextSpineItem() {
        val state = mutableEpubReading.value ?: return
        val target = state.currentSpineIndex + 1
        if (state.spineCount > 0 && target < state.spineCount) openSpineItem(target)
    }

    fun previousSpineItem() {
        val state = mutableEpubReading.value ?: return
        val target = state.currentSpineIndex - 1
        if (target >= 0) openSpineItem(target)
    }

    /**
     * Stores the text line currently at the top of a manually scrolled EPUB
     * chapter. While visual reading is playing, its exact word position remains
     * the source of truth instead.
     */
    fun rememberVisibleEpubPosition(anchor: PageTextAnchor, immediately: Boolean) {
        if (!mutableReadMode.value || mutableVisualReading.value.isPlaying) return
        val id = pageRequest.value ?: return
        val reading = mutableEpubReading.value ?: return
        val blocks = reading.content?.blocks ?: return
        val sectionIndex = reading.currentSpineIndex - reading.coverOffset
        if (sectionIndex < 0) return
        val narratableBlock = blocks.indices.firstOrNull { index ->
            index >= anchor.blockIndex && blocks[index].narrationText.isNotEmpty()
        } ?: blocks.indices.lastOrNull { index ->
            blocks[index].narrationText.isNotEmpty()
        } ?: return
        val rank = narratableBlocksBefore(blocks, narratableBlock)
        manualEpubPositionJob?.cancel()
        manualEpubPositionJob = viewModelScope.launch {
            val section = contentRepository.section(id, sectionIndex) ?: return@launch
            val paragraph = contentRepository.paragraph(id, section.firstParagraphIndex + rank) ?: return@launch
            if (
                pageRequest.value != id ||
                !mutableReadMode.value ||
                mutableVisualReading.value.isPlaying ||
                mutableEpubReading.value?.currentSpineIndex != reading.currentSpineIndex
            ) {
                return@launch
            }
            val blockText = blocks[narratableBlock].narrationText
            val offsetInParagraph = if (narratableBlock == anchor.blockIndex) {
                narrationOffsetInParagraph(blockText, anchor.charStart)
                    .coerceIn(0, paragraph.text.length)
            } else {
                0
            }
            val position = DocumentPosition(
                paragraphIndex = paragraph.paragraphIndex,
                offsetInParagraph = offsetInParagraph,
                absoluteOffset = paragraph.absoluteStart + offsetInParagraph
            )
            setVisualStart(position)
            checkpointVisualProgress(position, immediately = immediately)
        }
    }

    suspend fun imageBytes(resourcePath: String): ByteArray? {
        val id = pageRequest.value ?: return null
        val cacheKey = id.value + "/" + resourcePath
        imageCache.get(cacheKey)?.let { return it }
        // LruCache and ConcurrentHashMap both reject null values. Failed image
        // reads are intentionally not cached so they cannot crash Read Mode.
        val bytes = runCatching { loadImageResource.execute(id, resourcePath) }.getOrNull() ?: return null
        imageCache.put(cacheKey, bytes)
        return bytes
    }

    /** Opens a chapter and, when supplied, scrolls to its narration position. */
    private fun openSpineItem(
        spineIndex: Int,
        narrationJump: DocumentPosition? = null
    ) {
        val id = pageRequest.value ?: return
        val requestId = ++spineLoadRequestId
        mutableJumpTarget.value = null
        mutableActiveWordAnchor.value = null
        // Dispose the old chapter while the next one loads, so a stale block
        // cannot briefly appear at the top of the newly selected chapter.
        mutableEpubReading.update {
            it?.copy(
                currentSpineIndex = spineIndex,
                content = null,
                loadingContent = true,
                unavailable = false
            )
        }
        viewModelScope.launch {
            val content = runCatching { loadSpineContent.execute(id, spineIndex) }.getOrNull()
            if (
                pageRequest.value != id ||
                !mutableReadMode.value ||
                mutableEpubReading.value?.currentSpineIndex != spineIndex ||
                requestId != spineLoadRequestId
            ) {
                return@launch
            }
            mutableEpubReading.update {
                it?.takeIf { state -> state.currentSpineIndex == spineIndex }
                    ?.copy(content = content, loadingContent = false, unavailable = content == null)
            }
            narrationJump?.let { position ->
                resolveNarrationJump(id, content?.blocks, position)
                    ?.let { target -> mutableJumpTarget.value = target }
            }
            val activeWord = mutableVisualReading.value.activeWord
            if (activeWord != null) setActiveVisualWord(activeWord)
        }
    }

    /**
     * Maps a narration position to the block and character offset inside the
     * chapter's blocks that the reader should jump to. Returns null when the
     * stored narration text no longer matches the visual blocks (for example,
     * an EPUB imported before the extraction was unified), so the reader
     * stays at the chapter start instead of landing on the wrong page.
     */
    private suspend fun resolveNarrationJump(
        id: DocumentId,
        blocks: List<EpubBlock>?,
        position: DocumentPosition
    ): PageTextAnchor? {
        if (blocks == null) return null
        val paragraph = contentRepository.paragraph(id, position.paragraphIndex) ?: return null
        val section = contentRepository.section(id, paragraph.sectionIndex) ?: return null
        val rank = position.paragraphIndex - section.firstParagraphIndex
        val blockIndex = blockAtNarratableRank(blocks, rank) ?: return null
        val blockText = blocks[blockIndex].narrationText
        if (paragraph.text != blockText.trim()) return null
        val charStart = narrationOffsetInBlock(blockText, position.offsetInParagraph)
            .coerceIn(0, blockText.length)
        return PageTextAnchor(blockIndex, charStart)
    }

    /** The scrollable reader consumed a narration jump. */
    fun resolveReaderJump() {
        mutableJumpTarget.value = null
    }

    /**
     * Checkpoint the silent reader no more than once per second while it advances,
     * but flush immediately when the user pauses, exits, seeks, or makes a note.
     */
    private fun checkpointVisualProgress(position: DocumentPosition, immediately: Boolean = false) {
        val documentId = pageRequest.value ?: return
        val checkpoint = VisualProgressCheckpoint(documentId, position)
        pendingVisualProgress = checkpoint
        if (immediately) {
            visualProgressCheckpointJob?.cancel()
            visualProgressCheckpointJob = viewModelScope.launch { saveVisualProgress(checkpoint) }
        } else if (visualProgressCheckpointJob?.isActive != true) {
            visualProgressCheckpointJob = viewModelScope.launch {
                delay(VISUAL_PROGRESS_CHECKPOINT_INTERVAL_MS)
                val pending = pendingVisualProgress
                if (pending != null) saveVisualProgress(pending)
            }
        }
    }

    private suspend fun saveVisualProgress(checkpoint: VisualProgressCheckpoint) {
        saveReadingProgress.execute(
            ReadingProgress(
                documentId = checkpoint.documentId,
                position = checkpoint.position,
                updatedAtEpochMillis = timeProvider.nowEpochMillis(),
                completed = false
            )
        )
    }

    /**
     * The read-mode spine index matching the current narration position. Real
     * section indices are shifted by the synthesized cover page when the book
     * has one, so a fresh book (no progress) lands on the cover at index 0.
     */
    private suspend fun currentSpineIndexFor(id: DocumentId, coverOffset: Int): Int {
        val narratedIndex = narrationController.state.value.paragraphIndexOrNull()
            ?.takeIf { narrationController.state.value.documentIdOrNull() == id }
        val progressIndex = observeReadingProgress.execute(id).first()?.position?.paragraphIndex
            ?: narratedIndex
            ?: return 0
        return contentRepository.paragraph(id, progressIndex)?.sectionIndex?.plus(coverOffset) ?: 0
    }
}

sealed interface NoteMessage {
    data object Saved : NoteMessage
    data object Failed : NoteMessage
    data object Deleted : NoteMessage
    data object DeleteFailed : NoteMessage
}

private data class StoredVoice(
    val relativePath: String,
    val durationMillis: Long,
    val file: File
)

private data class VisualProgressCheckpoint(
    val documentId: DocumentId,
    val position: DocumentPosition
)

private const val EPUB_IMAGE_CACHE_MAX_BYTES = 8 * 1024 * 1024
private const val VISUAL_PROGRESS_CHECKPOINT_INTERVAL_MS = 1_000L

private fun NarrationState.documentIdOrNull(): DocumentId? = when (this) {
    is NarrationState.Preparing -> documentId
    is NarrationState.Playing -> documentId
    is NarrationState.Paused -> documentId
    is NarrationState.Completed -> documentId
    is NarrationState.Error -> documentId
    NarrationState.Idle -> null
}

private fun NarrationState.paragraphIndexOrNull(): Int? = when (this) {
    is NarrationState.Preparing -> requestedPosition.paragraphIndex
    is NarrationState.Playing -> safePosition.paragraphIndex
    is NarrationState.Paused -> resumePosition.paragraphIndex
    else -> null
}

private fun NarrationState.positionOrNull(): DocumentPosition? = when (this) {
    is NarrationState.Preparing -> requestedPosition
    is NarrationState.Playing -> safePosition
    is NarrationState.Paused -> resumePosition
    else -> null
}
