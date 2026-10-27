package com.noloxtreme.tts.reader.ui.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.Paragraph
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal sealed interface LearningSource {
    data object None : LearningSource
    data object Loading : LearningSource
    data object Missing : LearningSource
    data object Failed : LearningSource
    data class Available(val document: Document) : LearningSource
}

internal data class LearningPassage(
    val original: Paragraph,
    val translated: Paragraph,
    val originalLanguage: String?,
    val translatedLanguage: String?
)

internal sealed interface LearningPassageState {
    data object Idle : LearningPassageState
    data object Loading : LearningPassageState
    data object OriginalMissing : LearningPassageState
    data object PassageMissing : LearningPassageState
    data object Failed : LearningPassageState
    data class Ready(val passage: LearningPassage) : LearningPassageState
}

private data class LearningBook(val id: DocumentId, val sourceId: DocumentId, val language: String?)
private data class PassageRequest(val book: LearningBook?, val source: LearningSource, val index: Int?)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageLearningViewModel @Inject constructor(
    private val documents: DocumentRepository,
    private val content: ContentRepository
) : ViewModel() {
    private val book = MutableStateFlow<LearningBook?>(null)
    private val paragraphIndex = MutableStateFlow<Int?>(null)
    private val retryRevision = MutableStateFlow(0)

    internal val source: StateFlow<LearningSource> = combine(book, retryRevision) { book, _ -> book?.sourceId }
        .flatMapLatest { sourceId ->
            if (sourceId == null) flowOf(LearningSource.None)
            else documents.observeDocument(sourceId)
                .map<Document?, LearningSource> { it?.let(LearningSource::Available) ?: LearningSource.Missing }
                .onStart { emit(LearningSource.Loading) }
                .catch { emit(LearningSource.Failed) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearningSource.None)

    internal val passage: StateFlow<LearningPassageState> = combine(book, source, paragraphIndex, retryRevision) { book, source, index, _ ->
        PassageRequest(book, source, index)
    }.flatMapLatest { request ->
        flow<LearningPassageState> {
            val current = request.book
            val index = request.index
            when {
                current == null || index == null -> emit(LearningPassageState.Idle)
                request.source == LearningSource.Missing -> emit(LearningPassageState.OriginalMissing)
                request.source == LearningSource.Failed -> emit(LearningPassageState.Failed)
                request.source !is LearningSource.Available || request.source.document.id != current.sourceId ->
                    emit(LearningPassageState.Loading)
                else -> {
                    emit(LearningPassageState.Loading)
                    // Translation saves exactly one output paragraph per input paragraph.
                    // Fetch only the current pair; books never need to be loaded into memory.
                    val translated = content.paragraph(current.id, index)
                    val original = content.paragraph(current.sourceId, index)
                    if (translated == null || original == null ||
                        original.sectionIndex != translated.sectionIndex
                    ) emit(LearningPassageState.PassageMissing)
                    else emit(LearningPassageState.Ready(LearningPassage(
                        original, translated, request.source.document.languageTag, current.language
                    )))
                }
            }
        }.catch { emit(LearningPassageState.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearningPassageState.Idle)

    fun prepare(document: Document?) {
        val next = document?.translationSourceId?.let { LearningBook(document.id, it, document.languageTag) }
        if (book.value == next) return
        paragraphIndex.value = null
        book.value = next
    }

    /** Null suspends passage loading when the feature is closed or silent Read Mode is entered. */
    fun showParagraph(index: Int?) {
        paragraphIndex.value = index?.coerceAtLeast(0)
    }

    fun retry() { retryRevision.update { it + 1 } }
}
