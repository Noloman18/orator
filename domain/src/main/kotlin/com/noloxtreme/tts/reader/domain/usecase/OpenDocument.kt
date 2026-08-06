package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.TimeProvider

class OpenDocument @javax.inject.Inject constructor(
    private val documentRepository: DocumentRepository,
    private val timeProvider: TimeProvider
) {
    suspend fun execute(id: DocumentId) {
        documentRepository.updateLastOpened(id, timeProvider.nowEpochMillis())
    }
}
