package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.ProgressRepository
import com.noloxtreme.tts.reader.domain.ReadingProgress

/** Saves a reading position produced outside the narration engine, such as Read Mode. */
class SaveReadingProgress @javax.inject.Inject constructor(
    private val progressRepository: ProgressRepository
) {
    suspend fun execute(progress: ReadingProgress) {
        progressRepository.saveProgress(progress)
    }
}
