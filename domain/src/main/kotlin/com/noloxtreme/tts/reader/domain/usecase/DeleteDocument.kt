package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentRepository

class DeleteDocument @javax.inject.Inject constructor(
    private val documentRepository: DocumentRepository
) {
    suspend fun execute(id: DocumentId) {
        documentRepository.deleteDocument(id)
    }
}
