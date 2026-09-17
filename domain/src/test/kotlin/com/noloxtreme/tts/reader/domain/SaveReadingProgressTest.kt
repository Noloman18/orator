package com.noloxtreme.tts.reader.domain

import com.noloxtreme.tts.reader.domain.usecase.SaveReadingProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveReadingProgressTest {
    @Test
    fun savesTheReadModePosition() = runTest {
        val repository = RecordingProgressRepository()
        val progress = ReadingProgress(
            documentId = DocumentId("book"),
            position = DocumentPosition(4, 12, 103),
            updatedAtEpochMillis = 99,
            completed = false
        )

        SaveReadingProgress(repository).execute(progress)

        assertEquals(progress, repository.saved)
    }

    private class RecordingProgressRepository : ProgressRepository {
        var saved: ReadingProgress? = null

        override fun observeProgress(id: DocumentId): Flow<ReadingProgress?> = flowOf(null)

        override suspend fun getProgress(id: DocumentId): ReadingProgress? = null

        override suspend fun saveProgress(progress: ReadingProgress) {
            saved = progress
        }
    }
}
