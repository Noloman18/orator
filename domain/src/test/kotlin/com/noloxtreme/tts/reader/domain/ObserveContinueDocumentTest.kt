package com.noloxtreme.tts.reader.domain

import com.noloxtreme.tts.reader.domain.usecase.ObserveContinueDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveContinueDocumentTest {
    @Test
    fun selectsMostRecentlyOpenedUnfinishedDocument() = runTest {
        val completed = document("completed", opened = 30)
        val unfinished = document("unfinished", opened = 20)
        val neverOpened = document("never", opened = null)
        val documents = FakeDocumentRepository(listOf(completed, unfinished, neverOpened))
        val progress = FakeProgressRepository(
            mapOf(
                completed.id to progress(completed, completed = true),
                unfinished.id to progress(unfinished, completed = false)
            )
        )

        assertEquals(
            unfinished,
            ObserveContinueDocument(documents, progress).execute().first()
        )
    }

    private fun document(id: String, opened: Long?) = Document(
        id = DocumentId(id),
        title = id,
        originalFileName = "$id.txt",
        mimeType = "text/plain",
        sha256 = id.padEnd(64, '0'),
        languageTag = "en-US",
        totalCharacterCount = 10,
        sectionCount = 1,
        importedAtEpochMillis = 1,
        lastOpenedAtEpochMillis = opened
    )

    private fun progress(document: Document, completed: Boolean) = ReadingProgress(
        documentId = document.id,
        position = DocumentPosition(0, if (completed) 10 else 0, if (completed) 10 else 0),
        updatedAtEpochMillis = 1,
        completed = completed
    )

    private class FakeDocumentRepository(documents: List<Document>) : DocumentRepository {
        private val state = MutableStateFlow(documents)
        override fun observeLibrary(): Flow<List<Document>> = state
        override fun observeDocument(id: DocumentId): Flow<Document?> =
            MutableStateFlow(state.value.firstOrNull { it.id == id })
        override suspend fun getDocument(id: DocumentId): Document? = state.value.firstOrNull { it.id == id }
        override suspend fun updateLastOpened(id: DocumentId, epochMillis: Long) = Unit
        override suspend fun deleteDocument(id: DocumentId) = Unit
    }

    private class FakeProgressRepository(
        private val values: Map<DocumentId, ReadingProgress>
    ) : ProgressRepository {
        override fun observeProgress(id: DocumentId): Flow<ReadingProgress?> =
            MutableStateFlow(values[id])
        override suspend fun getProgress(id: DocumentId): ReadingProgress? = values[id]
        override suspend fun saveProgress(progress: ReadingProgress) = Unit
    }
}
