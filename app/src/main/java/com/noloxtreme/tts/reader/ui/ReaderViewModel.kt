package com.noloxtreme.tts.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.ReadingProgress
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReaderLoadState {
    data object Loading : ReaderLoadState
    data object Ready : ReaderLoadState
    data object MissingDocument : ReaderLoadState
}

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
    val narrationController: NarrationController
) : ViewModel() {
    private val pageRequest = MutableStateFlow<DocumentId?>(null)
    private val initialParagraph = MutableStateFlow(0)

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

    fun restart() = restartCompletedDocument.execute()

    fun seekTo(position: DocumentPosition) = seekNarration.execute(position)
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
