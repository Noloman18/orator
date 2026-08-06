package com.noloxtreme.tts.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.ImportSource
import com.noloxtreme.tts.reader.domain.ImportState
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.usecase.DeleteDocument
import com.noloxtreme.tts.reader.domain.usecase.ImportDocument
import com.noloxtreme.tts.reader.domain.usecase.ObserveLibrary
import com.noloxtreme.tts.reader.domain.usecase.ObserveContinueDocument
import com.noloxtreme.tts.reader.domain.usecase.ObserveReadingProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.update

sealed interface LibraryLoadState {
    data object Loading : LibraryLoadState
    data object Ready : LibraryLoadState
    data object Error : LibraryLoadState
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel @Inject constructor(
    observeLibrary: ObserveLibrary,
    observeContinueDocument: ObserveContinueDocument,
    observeReadingProgress: ObserveReadingProgress,
    private val importDocument: ImportDocument,
    private val deleteDocument: DeleteDocument,
    private val narrationController: NarrationController
) : ViewModel() {
    private val reloadKey = MutableStateFlow(0)
    private val mutableLibraryState = MutableStateFlow<LibraryLoadState>(LibraryLoadState.Loading)
    val libraryState: StateFlow<LibraryLoadState> = mutableLibraryState.asStateFlow()
    val documents: StateFlow<List<Document>> = reloadKey.flatMapLatest {
        observeLibrary.execute()
            .onStart { mutableLibraryState.value = LibraryLoadState.Loading }
            .onEach { mutableLibraryState.value = LibraryLoadState.Ready }
            .catch {
                mutableLibraryState.value = LibraryLoadState.Error
                emit(emptyList())
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val continueDocument: StateFlow<Document?> = observeContinueDocument.execute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val continueProgress = continueDocument
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) emptyFlow() else observeReadingProgress.execute(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val mutableImportState = MutableStateFlow<ImportState>(ImportState.AwaitingPicker)
    val importState: StateFlow<ImportState> = mutableImportState.asStateFlow()
    private var importJob: Job? = null
    private val mutableDeleteFailure = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleteFailure = mutableDeleteFailure.asSharedFlow()

    fun import(source: ImportSource) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            importDocument.execute(source).collect { state ->
                mutableImportState.value = state
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        viewModelScope.launch { importDocument.cancelActiveImport() }
        mutableImportState.value = ImportState.AwaitingPicker
    }

    fun retryLibrary() {
        reloadKey.update { it + 1 }
    }

    fun delete(id: DocumentId) {
        viewModelScope.launch {
            try {
                if (narrationController.state.value.documentIdOrNull() == id) {
                    narrationController.dispatch(NarrationCommand.Stop)
                    withTimeout(DELETE_STOP_TIMEOUT_MS) {
                        narrationController.state.first {
                            it !is NarrationState.Playing && it !is NarrationState.Preparing
                        }
                    }
                }
                deleteDocument.execute(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                mutableDeleteFailure.emit(Unit)
            }
        }
    }

    private companion object {
        const val DELETE_STOP_TIMEOUT_MS = 5_000L
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
