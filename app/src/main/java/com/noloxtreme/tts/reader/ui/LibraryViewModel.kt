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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeLibrary: ObserveLibrary,
    private val importDocument: ImportDocument,
    private val deleteDocument: DeleteDocument,
    private val narrationController: NarrationController
) : ViewModel() {
    val documents: StateFlow<List<Document>> = observeLibrary.execute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mutableImportState = MutableStateFlow<ImportState>(ImportState.AwaitingPicker)
    val importState: StateFlow<ImportState> = mutableImportState.asStateFlow()
    private var importJob: Job? = null

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

    fun delete(id: DocumentId) {
        viewModelScope.launch {
            narrationController.dispatch(NarrationCommand.Stop)
            withTimeout(DELETE_STOP_TIMEOUT_MS) {
                narrationController.state.first { it !is NarrationState.Playing && it !is NarrationState.Preparing }
            }
            deleteDocument.execute(id)
        }
    }

    private companion object {
        const val DELETE_STOP_TIMEOUT_MS = 5_000L
    }
}
