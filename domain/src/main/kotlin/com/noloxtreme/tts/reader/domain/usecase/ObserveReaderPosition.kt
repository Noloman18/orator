package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.ReaderPosition
import com.noloxtreme.tts.reader.domain.ReaderPositionRepository
import kotlinx.coroutines.flow.Flow

class ObserveReaderPosition @javax.inject.Inject constructor(
    private val repository: ReaderPositionRepository
) {
    fun execute(id: DocumentId): Flow<ReaderPosition?> = repository.observe(id)
}
