package com.noloxtreme.tts.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.EPUB_MIME_TYPE
import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.EpubSpineContent
import com.noloxtreme.tts.reader.domain.EpubTocEntry
import com.noloxtreme.tts.reader.domain.FAST_JUMP_SENTENCE_COUNT
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.ReaderPosition
import com.noloxtreme.tts.reader.domain.ReadingProgress
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
import com.noloxtreme.tts.reader.domain.usecase.ObserveReaderPosition
import com.noloxtreme.tts.reader.domain.usecase.ReaderContent
import com.noloxtreme.tts.reader.domain.usecase.ObserveReaderContent
import com.noloxtreme.tts.reader.domain.usecase.ObserveReadingProgress
import com.noloxtreme.tts.reader.domain.usecase.ObserveSettings
import com.noloxtreme.tts.reader.domain.usecase.OpenDocument
import com.noloxtreme.tts.reader.domain.usecase.PauseNarration
import com.noloxtreme.tts.reader.domain.usecase.RestartCompletedDocument
import com.noloxtreme.tts.reader.domain.usecase.SaveReaderPosition
import com.noloxtreme.tts.reader.domain.usecase.SeekNarration
import com.noloxtreme.tts.reader.domain.usecase.SkipSentence
import com.noloxtreme.tts.reader.domain.usecase.StartOrResumeNarration
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReaderLoadState {
    data object Loading : ReaderLoadState
    data object Ready : ReaderLoadState
    data object MissingDocument : ReaderLoadState
}

/** Visual read-mode state for EPUB documents; null while narration mode is active. */
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
    private val restartCompletedDocument: RestartCompletedDocument,
    private val contentRepository: ContentRepository,
    private val loadTableOfContents: LoadTableOfContents,
    private val loadSpineContent: LoadSpineContent,
    private val loadSpineCount: LoadSpineCount,
    private val loadCoverPresence: LoadCoverPresence,
    private val loadImageResource: LoadImageResource,
    private val observeReaderPosition: ObserveReaderPosition,
    private val saveReaderPosition: SaveReaderPosition,
    val narrationController: NarrationController
) : ViewModel() {
    private val pageRequest = MutableStateFlow<DocumentId?>(null)
    private val initialParagraph = MutableStateFlow(0)
    private val mutableEpubReading = MutableStateFlow<EpubReadingState?>(null)
    private val mutableReaderPageIndex = MutableStateFlow(0)
    private val mutableReaderPageCount = MutableStateFlow(0)
    private val mutablePageAnchor = MutableStateFlow<PageTextAnchor?>(null)
    private val mutableJumpTarget = MutableStateFlow<PageTextAnchor?>(null)
    private val imageCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray?>()

    val epubReading: StateFlow<EpubReadingState?> = mutableEpubReading.asStateFlow()

    /** The read-mode page shown inside the current spine item. */
    val readerPageIndex: StateFlow<Int> = mutableReaderPageIndex.asStateFlow()

    /** How many pages the current spine item was packed into (0 until measured). */
    val readerPageCount: StateFlow<Int> = mutableReaderPageCount.asStateFlow()

    /** A pending jump target for the paged reader; cleared once it is resolved. */
    val readerJumpTarget: StateFlow<PageTextAnchor?> = mutableJumpTarget.asStateFlow()

    private val narrationTransport = NarrationTransport(
        rewindAction = ::rewind,
        previousSentenceAction = ::previousSentence,
        nextSentenceAction = ::nextSentence,
        fastForwardAction = ::fastForward
    )
    private val chapterTransport = ChapterTransport(
        previousChapter = ::previousSpineItem,
        nextChapter = ::nextSpineItem,
        previousPage = ::previousReaderPage,
        nextPage = ::nextReaderPage
    )

    /**
     * The transport strategy matching the current mode: chapter navigation
     * while the visual read mode is open, sentence skipping otherwise.
     */
    val transport: StateFlow<ReaderTransport> = mutableEpubReading
        .map { reading -> if (reading == null) narrationTransport else chapterTransport }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), narrationTransport)

    private val readerContent = pageRequest.flatMapLatest { id ->
        if (id == null) emptyFlow()
        else observeReaderContent.execute(id)
    }
    private val content: StateFlow<ReaderContent?> = readerContent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val document: StateFlow<Document?> = content
        .map { it?.document }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun play() = startOrResumeNarration.execute()

    /**
     * Starts narration from the page the reader is currently showing. Resolves
     * the page's first text to a narration position, seeks there, then plays.
     * Falls back to the chapter start when the visual and narration text no
     * longer align (for example, an EPUB imported before the extraction was
     * unified).
     */
    fun playFromCurrentPage() {
        viewModelScope.launch {
            currentPagePosition()?.let { position -> seekNarration.execute(position) }
            startOrResumeNarration.execute()
        }
    }

    /**
     * Maps the current reader page to a narration [DocumentPosition]. Only
     * returns a position when the block's inline text matches the stored
     * paragraph exactly, so a stale or misaligned import degrades gracefully
     * instead of seeking to the wrong text.
     */
    private suspend fun currentPagePosition(): DocumentPosition? {
        val id = pageRequest.value ?: return null
        val reading = mutableEpubReading.value ?: return null
        val blocks = reading.content?.blocks ?: return null
        val anchor = mutablePageAnchor.value ?: return null
        val block = blocks.getOrNull(anchor.blockIndex) ?: return null
        val blockText = block.narrationText
        if (blockText.isEmpty()) return null
        val sectionIndex = reading.currentSpineIndex - reading.coverOffset
        val section = contentRepository.section(id, sectionIndex) ?: return null
        val sectionStart = DocumentPosition(
            paragraphIndex = section.firstParagraphIndex,
            offsetInParagraph = 0,
            absoluteOffset = section.absoluteStart
        )
        val paragraphIndex = section.firstParagraphIndex + narratableBlocksBefore(blocks, anchor.blockIndex)
        val paragraph = contentRepository.paragraph(id, paragraphIndex) ?: return sectionStart
        if (paragraph.sectionIndex != sectionIndex) return sectionStart
        if (paragraph.text != blockText.trim()) return sectionStart
        val offset = narrationOffsetInParagraph(blockText, anchor.charStart)
            .coerceIn(0, paragraph.text.length)
        return DocumentPosition(paragraphIndex, offset, paragraph.absoluteStart + offset)
    }

    fun pause() = pauseNarration.execute()

    fun previousSentence() = skipSentence.execute(previous = true)

    fun nextSentence() = skipSentence.execute(previous = false)

    fun rewind() = skipSentence.execute(
        previous = true,
        count = FAST_JUMP_SENTENCE_COUNT
    )

    fun fastForward() = skipSentence.execute(
        previous = false,
        count = FAST_JUMP_SENTENCE_COUNT
    )

    fun restart() = restartCompletedDocument.execute()

    fun seekTo(position: DocumentPosition) = seekNarration.execute(position)

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

    fun enterReadMode() {
        if (mutableEpubReading.value != null) return
        val id = pageRequest.value ?: return
        if (document.value?.mimeType != EPUB_MIME_TYPE) return
        mutableReaderPageIndex.value = 0
        mutableReaderPageCount.value = 0
        viewModelScope.launch {
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
            if (pageRequest.value != id || mutableEpubReading.value == null) return@launch
            mutableEpubReading.update {
                it?.copy(toc = toc, spineCount = spineCount)
            }
            // Enter where the audio currently is (EPUB-only read mode), then
            // fall back to the saved visual position and finally the start of
            // the book.
            val narrated = narrationController.state.value
                .takeIf { it.documentIdOrNull() == id }
                ?.positionOrNull()
            val narratedSpine = narrated?.let { position ->
                contentRepository.paragraph(id, position.paragraphIndex)
                    ?.sectionIndex?.plus(coverOffset)
            }
            val saved = runCatching { observeReaderPosition.execute(id).first() }.getOrNull()
            val startSpineIndex: Int
            val initialPageIndex: Int
            val narrationJump: DocumentPosition?
            when {
                narratedSpine != null && narratedSpine < spineCount -> {
                    startSpineIndex = narratedSpine
                    initialPageIndex = 0
                    narrationJump = narrated
                }
                saved != null && saved.spineIndex < spineCount -> {
                    startSpineIndex = saved.spineIndex
                    initialPageIndex = saved.pageIndex
                    narrationJump = null
                }
                else -> {
                    startSpineIndex = currentSpineIndexFor(id, coverOffset)
                    initialPageIndex = 0
                    narrationJump = null
                }
            }
            openSpineItem(startSpineIndex, initialPageIndex, narrationJump)
        }
    }

    fun exitReadMode() {
        mutableEpubReading.value = null
        mutableJumpTarget.value = null
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
        if (narrationController.state.value.documentIdOrNull() == id && readingState != null) {
            viewModelScope.launch {
                val section = contentRepository.section(id, entry.spineIndex - readingState.coverOffset)
                    ?: return@launch
                seekTo(
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
     * Turns to the next page of the current spine item, or opens the next
     * chapter when the reader is on the last page. Shared by the swipe and
     * tap gestures and the transport's fast-forward button.
     */
    fun nextReaderPage() {
        when (val advance = nextPageAdvance(mutableReaderPageIndex.value, mutableReaderPageCount.value)) {
            is PageTurnAdvance.ToPage -> {
                mutableReaderPageIndex.value = advance.index
                persistReaderPosition(advance.index)
            }
            PageTurnAdvance.CrossChapter -> nextSpineItem()
            PageTurnAdvance.Stay -> Unit
        }
    }

    /**
     * Turns to the previous page of the current spine item, or opens the
     * previous chapter when the reader is on the first page. Shared by the
     * swipe and tap gestures and the transport's rewind button.
     */
    fun previousReaderPage() {
        when (val advance = previousPageAdvance(mutableReaderPageIndex.value)) {
            is PageTurnAdvance.ToPage -> {
                mutableReaderPageIndex.value = advance.index
                persistReaderPosition(advance.index)
            }
            PageTurnAdvance.CrossChapter -> previousSpineItem()
            PageTurnAdvance.Stay -> Unit
        }
    }

    /** The paged reader reports how many pages the current chapter was packed into. */
    fun setReaderPageCount(count: Int) {
        mutableReaderPageCount.value = count.coerceAtLeast(0)
        val maxIndex = (count - 1).coerceAtLeast(0)
        if (mutableReaderPageIndex.value > maxIndex) {
            mutableReaderPageIndex.value = maxIndex
            persistReaderPosition(maxIndex)
        }
    }

    /** The paged reader reports the first text position of the current page. */
    fun setPageAnchor(anchor: PageTextAnchor?) {
        mutablePageAnchor.value = anchor
    }

    suspend fun imageBytes(resourcePath: String): ByteArray? {
        val id = pageRequest.value ?: return null
        val cacheKey = id.value + "/" + resourcePath
        if (imageCache.containsKey(cacheKey)) return imageCache[cacheKey]
        val bytes = runCatching { loadImageResource.execute(id, resourcePath) }.getOrNull()
        imageCache[cacheKey] = bytes
        return bytes
    }

    /**
     * Opens a spine item, starting at [initialPageIndex] (page zero for
     * ordinary chapter navigation, the saved page when resuming read mode).
     * When [narrationJump] is set, the reader lands on the page that holds
     * that narration position once the chapter's blocks are loaded.
     */
    private fun openSpineItem(
        spineIndex: Int,
        initialPageIndex: Int = 0,
        narrationJump: DocumentPosition? = null
    ) {
        val id = pageRequest.value ?: return
        mutableReaderPageIndex.value = initialPageIndex
        mutableReaderPageCount.value = 0
        mutableJumpTarget.value = null
        mutableEpubReading.update { it?.copy(currentSpineIndex = spineIndex, loadingContent = true) }
        persistReaderPosition(initialPageIndex)
        viewModelScope.launch {
            val content = runCatching { loadSpineContent.execute(id, spineIndex) }.getOrNull()
            if (pageRequest.value != id || mutableEpubReading.value == null) return@launch
            mutableEpubReading.update {
                it?.takeIf { state -> state.currentSpineIndex == spineIndex }
                    ?.copy(content = content, loadingContent = false, unavailable = content == null)
            }
            narrationJump?.let { position ->
                resolveNarrationJump(id, content?.blocks, position)
                    ?.let { target -> mutableJumpTarget.value = target }
            }
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

    /** The paged reader resolved a narration jump: own the page and clear the target. */
    fun resolveReaderJump(pageIndex: Int) {
        mutableReaderPageIndex.value = pageIndex
        mutableJumpTarget.value = null
        persistReaderPosition(pageIndex)
    }

    /** Best-effort persistence of the current read-mode position. */
    private fun persistReaderPosition(pageIndex: Int) {
        val id = pageRequest.value ?: return
        val spineIndex = mutableEpubReading.value?.currentSpineIndex ?: return
        viewModelScope.launch {
            runCatching { saveReaderPosition.execute(id, ReaderPosition(spineIndex, pageIndex)) }
        }
    }

    /**
     * The read-mode spine index matching the current narration position. Real
     * section indices are shifted by the synthesized cover page when the book
     * has one, so a fresh book (no progress) lands on the cover at index 0.
     */
    private suspend fun currentSpineIndexFor(id: DocumentId, coverOffset: Int): Int {
        val narratedIndex = narrationController.state.value.paragraphIndexOrNull()
            ?.takeIf { narrationController.state.value.documentIdOrNull() == id }
        val progressIndex = narratedIndex
            ?: observeReadingProgress.execute(id).first()?.position?.paragraphIndex
            ?: return 0
        return contentRepository.paragraph(id, progressIndex)?.sectionIndex?.plus(coverOffset) ?: 0
    }
}

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
