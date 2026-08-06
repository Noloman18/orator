package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Selects the most recently opened unfinished document for the Library's resume card. */
class ObserveContinueDocument @javax.inject.Inject constructor(
    private val documentRepository: DocumentRepository,
    private val progressRepository: ProgressRepository
) {
    fun execute(): Flow<Document?> = documentRepository.observeLibrary().map { documents ->
        documents.firstOrNull { document ->
            document.lastOpenedAtEpochMillis != null &&
                progressRepository.getProgress(document.id)?.completed != true
        }
    }
}
