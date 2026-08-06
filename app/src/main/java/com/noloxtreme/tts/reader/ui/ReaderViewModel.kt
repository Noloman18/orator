package com.noloxtreme.tts.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.ProgressRepository
import com.noloxtreme.tts.reader.domain.ReadingProgress
import com.noloxtreme.tts.reader.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    settingsRepository: SettingsRepository,
    val narrationController: NarrationController
) : ViewModel() {
    private val loadedDocument = MutableStateFlow<Document?>(null)
    private val initialParagraph = MutableStateFlow(0)
    private val pageRequest = MutableStateFlow<DocumentId?>(null)
    private val mutableProgress = MutableStateFlow<ReadingProgress?>(null)

    val document: StateFlow<Document?> = loadedDocument.asStateFlow()
    val progress: StateFlow<ReadingProgress?> = mutableProgress.asStateFlow()
    val settings: StateFlow<OratorSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OratorSettings())
    val narration: StateFlow<NarrationState> = narrationController.state

    val paragraphs: Flow<PagingData<Paragraph>> = pageRequest.flatMapLatest { id ->
        if (id == null) emptyFlow()
        else contentRepository.pagedParagraphs(id, initialParagraph.value)
    }.cachedIn(viewModelScope)

    fun load(id: DocumentId) {
        if (loadedDocument.value?.id == id) return
        viewModelScope.launch {
            val currentDocument = documentRepository.getDocument(id)
            loadedDocument.value = currentDocument
            val currentProgress = progressRepository.getProgress(id)
            mutableProgress.value = currentProgress
            initialParagraph.value = currentProgress?.position?.paragraphIndex ?: 0
            pageRequest.value = id
            narrationController.dispatch(NarrationCommand.Load(id))
        }
    }

    fun play() = narrationController.dispatch(NarrationCommand.Play)

    fun pause() = narrationController.dispatch(NarrationCommand.Pause())

    fun previousSentence() = narrationController.dispatch(NarrationCommand.PreviousSentence)

    fun nextSentence() = narrationController.dispatch(NarrationCommand.NextSentence)

    fun restart() = narrationController.dispatch(NarrationCommand.RestartCompleted)

    fun seekTo(position: DocumentPosition) = narrationController.dispatch(NarrationCommand.SeekTo(position))
}
