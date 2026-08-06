package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.ProgressRepository
import com.noloxtreme.tts.reader.domain.ReadingProgress
import kotlinx.coroutines.flow.Flow

class ObserveReadingProgress @javax.inject.Inject constructor(
    private val progressRepository: ProgressRepository
) {
    fun execute(id: DocumentId): Flow<ReadingProgress?> = progressRepository.observeProgress(id)
}
