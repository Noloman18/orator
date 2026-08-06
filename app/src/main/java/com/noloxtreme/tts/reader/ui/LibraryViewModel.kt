package com.noloxtreme.tts.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentImporter
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.ImportSource
import com.noloxtreme.tts.reader.domain.ImportState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val documentImporter: DocumentImporter
) : ViewModel() {
    val documents: StateFlow<List<Document>> = documentRepository.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mutableImportState = MutableStateFlow<ImportState>(ImportState.AwaitingPicker)
    val importState: StateFlow<ImportState> = mutableImportState.asStateFlow()
    private var importJob: Job? = null

    fun import(source: ImportSource) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            documentImporter.import(source).collect { state ->
                mutableImportState.value = state
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        viewModelScope.launch { documentImporter.cancelActiveImport() }
        mutableImportState.value = ImportState.AwaitingPicker
    }

    fun delete(id: com.noloxtreme.tts.reader.domain.DocumentId) {
        viewModelScope.launch { documentRepository.deleteDocument(id) }
    }
}
