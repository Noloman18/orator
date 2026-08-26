package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.ReaderPosition
import com.noloxtreme.tts.reader.domain.ReaderPositionRepository

class SaveReaderPosition @javax.inject.Inject constructor(
    private val repository: ReaderPositionRepository
) {
    suspend fun execute(id: DocumentId, position: ReaderPosition) {
        repository.save(id, position)
    }
}
