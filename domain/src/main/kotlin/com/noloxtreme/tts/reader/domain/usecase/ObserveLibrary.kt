package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentRepository
import kotlinx.coroutines.flow.Flow

class ObserveLibrary @javax.inject.Inject constructor(
    private val documentRepository: DocumentRepository
) {
    fun execute(): Flow<List<Document>> = documentRepository.observeLibrary()
}
