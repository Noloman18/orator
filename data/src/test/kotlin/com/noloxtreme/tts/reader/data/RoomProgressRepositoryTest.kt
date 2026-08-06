package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.ReadingProgress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RoomProgressRepositoryTest : RoomDatabaseTest() {

    private lateinit var repository: RoomProgressRepository

    @Before
    fun setUp() {
        openDatabase()
        repository = RoomProgressRepository(database.progressDao(), database.contentDao())
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
                    originalFileName = "title.txt",
                    mimeType = "text/plain",
                    sourceExtension = "txt",
                    privateSourcePath = "documents/doc-1/source.txt",
                    sha256 = "hash-1",
                    languageTag = null,
                    totalCharacterCount = 13,
                    sectionCount = 1,
                    importedAt = 1L,
                    lastOpenedAt = null
                )
            )
            database.contentDao().insertBatch(
                listOf(
                    ParagraphEntity("doc-1", 0, 0, "Hello world", 0L, 11L),
                    ParagraphEntity("doc-1", 1, 0, "Second", 13L, 19L)
                )
            )
        }
    }

    private fun progress(
        paragraphIndex: Int,
        offset: Int,
        absoluteOffset: Long,
        completed: Boolean = false
    ) = ReadingProgress(
        documentId = DocumentId("doc-1"),
        position = DocumentPosition(paragraphIndex, offset, absoluteOffset),
        updatedAtEpochMillis = 2L,
        completed = completed
    )

    @Test
    fun validProgressIsPersistedAndReadBack() {
        seedDocument()

        runBlocking { repository.saveProgress(progress(0, 5, 5L)) }

        val saved = runBlocking { repository.getProgress(DocumentId("doc-1")) }
        assertEquals(5, saved?.position?.offsetInParagraph)
        assertEquals(5L, saved?.position?.absoluteOffset)
        assertEquals(false, saved?.completed)
    }

    @Test
    fun progressForMissingParagraphIsRejected() {
        seedDocument()

        assertFailsWith(IllegalArgumentException::class.java) {
            runBlocking { repository.saveProgress(progress(9, 0, 100L)) }
        }
        assertNull(runBlocking { repository.getProgress(DocumentId("doc-1")) })
    }

    @Test
    fun offsetBeyondParagraphLengthIsRejected() {
        seedDocument()

        assertFailsWith(IllegalArgumentException::class.java) {
            runBlocking { repository.saveProgress(progress(0, 12, 12L)) }
        }
        assertNull(runBlocking { repository.getProgress(DocumentId("doc-1")) })
    }

    @Test
    fun inconsistentAbsoluteOffsetIsRejected() {
        seedDocument()

        assertFailsWith(IllegalArgumentException::class.java) {
            runBlocking { repository.saveProgress(progress(1, 2, 999L)) }
        }
    }

    @Test
    fun completedProgressMustPointAtFinalParagraphEnd() {
        seedDocument()

        assertFailsWith(IllegalArgumentException::class.java) {
            runBlocking { repository.saveProgress(progress(0, 11, 11L, completed = true)) }
        }

        runBlocking { repository.saveProgress(progress(1, 6, 19L, completed = true)) }
        assertEquals(true, runBlocking { repository.getProgress(DocumentId("doc-1")) }?.completed)
    }

    private fun <T : Throwable> assertFailsWith(
        expected: Class<T>,
        block: () -> Unit
    ): T {
        try {
            block()
        } catch (error: Throwable) {
            if (expected.isInstance(error)) {
                @Suppress("UNCHECKED_CAST")
                return error as T
            }
            throw error
        }
        fail("Expected ${expected.simpleName} but nothing was thrown")
        throw AssertionError("unreachable")
    }
}
