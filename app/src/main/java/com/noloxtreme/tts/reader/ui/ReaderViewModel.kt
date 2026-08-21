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
import com.noloxtreme.tts.reader.domain.EpubSpineContent
import com.noloxtreme.tts.reader.domain.EpubTocEntry
import com.noloxtreme.tts.reader.domain.FAST_JUMP_SENTENCE_COUNT
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.ReadingProgress
import com.noloxtreme.tts.reader.domain.usecase.LoadImageResource
import com.noloxtreme.tts.reader.domain.usecase.LoadSpineContent
import com.noloxtreme.tts.reader.domain.usecase.LoadSpineCount
import com.noloxtreme.tts.reader.domain.usecase.LoadTableOfContents
import com.noloxtreme.tts.reader.domain.usecase.ReaderContent
import com.noloxtreme.tts.reader.domain.usecase.ObserveReaderContent
import com.noloxtreme.tts.reader.domain.usecase.ObserveReadingProgress
import com.noloxtreme.tts.reader.domain.usecase.ObserveSettings
import com.noloxtreme.tts.reader.domain.usecase.OpenDocument
import com.noloxtreme.tts.reader.domain.usecase.PauseNarration
import com.noloxtreme.tts.reader.domain.usecase.RestartCompletedDocument
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
    val unavailable: Boolean
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
    private val loadImageResource: LoadImageResource,
    val narrationController: NarrationController
) : ViewModel() {
    private val pageRequest = MutableStateFlow<DocumentId?>(null)
    private val initialParagraph = MutableStateFlow(0)
    private val mutableEpubReading = MutableStateFlow<EpubReadingState?>(null)
    private val imageCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray?>()

    val epubReading: StateFlow<EpubReadingState?> = mutableEpubReading.asStateFlow()

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
        viewModelScope.launch {
            val startSpineIndex = currentSpineIndexFor(id)
            mutableEpubReading.value = EpubReadingState(
                toc = emptyList(),
                spineCount = 0,
                currentSpineIndex = startSpineIndex,
                content = null,
                loadingContent = true,
                unavailable = false
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
            openSpineItem(startSpineIndex)
        }
    }

    fun exitReadMode() {
        mutableEpubReading.value = null
    }

    fun retryCurrentSpine() {
        mutableEpubReading.value?.let { state ->
            openSpineItem(state.currentSpineIndex)
        }
    }

    fun openTocEntry(entry: EpubTocEntry) {
        val id = pageRequest.value ?: return
        openSpineItem(entry.spineIndex)
        if (narrationController.state.value.documentIdOrNull() == id) {
            viewModelScope.launch {
                val section = contentRepository.section(id, entry.spineIndex) ?: return@launch
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

    suspend fun imageBytes(resourcePath: String): ByteArray? {
        val id = pageRequest.value ?: return null
        val cacheKey = id.value + "/" + resourcePath
        if (imageCache.containsKey(cacheKey)) return imageCache[cacheKey]
        val bytes = runCatching { loadImageResource.execute(id, resourcePath) }.getOrNull()
        imageCache[cacheKey] = bytes
        return bytes
    }

    private fun openSpineItem(spineIndex: Int) {
        val id = pageRequest.value ?: return
        mutableEpubReading.update { it?.copy(currentSpineIndex = spineIndex, loadingContent = true) }
        viewModelScope.launch {
            val content = runCatching { loadSpineContent.execute(id, spineIndex) }.getOrNull()
            if (pageRequest.value != id || mutableEpubReading.value == null) return@launch
            mutableEpubReading.update {
                it?.takeIf { state -> state.currentSpineIndex == spineIndex }
                    ?.copy(content = content, loadingContent = false, unavailable = content == null)
            }
        }
    }

    private suspend fun currentSpineIndexFor(id: DocumentId): Int {
        val narratedIndex = narrationController.state.value.paragraphIndexOrNull()
            ?.takeIf { narrationController.state.value.documentIdOrNull() == id }
        val progressIndex = narratedIndex
            ?: observeReadingProgress.execute(id).first()?.position?.paragraphIndex
            ?: return 0
        return contentRepository.paragraph(id, progressIndex)?.sectionIndex ?: 0
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
