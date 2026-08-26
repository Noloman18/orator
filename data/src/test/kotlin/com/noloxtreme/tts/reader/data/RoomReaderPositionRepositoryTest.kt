package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.ReaderPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RoomReaderPositionRepositoryTest : RoomDatabaseTest() {

    private lateinit var repository: RoomReaderPositionRepository

    @Before
    fun setUp() {
        openDatabase()
        repository = RoomReaderPositionRepository(database.readerPositionDao())
    }

    @After
    fun tearDown() {
        closeDatabase()
    }

    private fun seedDocument() {
        runBlocking {
            database.documentDao().insert(
                DocumentEntity(
                    id = "doc-1",
                    title = "Title",
                    originalFileName = "title.epub",
                    mimeType = "application/epub+zip",
                    sourceExtension = "epub",
                    privateSourcePath = "documents/doc-1/source.epub",
                    sha256 = "hash-1",
                    languageTag = null,
                    totalCharacterCount = 13,
                    sectionCount = 1,
                    importedAt = 1L,
                    lastOpenedAt = null
                )
            )
        }
    }

    @Test
    fun observeIsEmptyUntilAPositionIsSaved() = runBlocking {
        seedDocument()

        assertNull(repository.observe(DocumentId("doc-1")).first())
    }

    @Test
    fun savedPositionRoundTripsThroughObservation() = runBlocking {
        seedDocument()

        repository.save(DocumentId("doc-1"), ReaderPosition(3, 7))

        assertEquals(ReaderPosition(3, 7), repository.observe(DocumentId("doc-1")).first())
    }

    @Test
    fun savingAgainReplacesThePreviousPosition() = runBlocking {
        seedDocument()

        repository.save(DocumentId("doc-1"), ReaderPosition(1, 2))
        repository.save(DocumentId("doc-1"), ReaderPosition(4, 9))

        assertEquals(ReaderPosition(4, 9), repository.observe(DocumentId("doc-1")).first())
    }

    @Test
    fun deletingTheDocumentRemovesItsPosition() = runBlocking {
        seedDocument()
        repository.save(DocumentId("doc-1"), ReaderPosition(2, 3))

        database.documentDao().deleteById("doc-1")

        assertNull(repository.observe(DocumentId("doc-1")).first())
    }
}
