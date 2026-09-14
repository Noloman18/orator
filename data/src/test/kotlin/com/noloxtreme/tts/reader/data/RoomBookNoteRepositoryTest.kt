package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.BookNote
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomBookNoteRepositoryTest : RoomDatabaseTest() {
    private lateinit var repository: RoomBookNoteRepository

    @Before
    fun setUp() {
        openDatabase()
        repository = RoomBookNoteRepository(database.bookNoteDao())
        runBlocking { database.documentDao().insert(document()) }
    }

    @After
    fun tearDown() = closeDatabase()

    @Test
    fun savesAndObservesNotesInNewestFirstOrder() = runBlocking {
        repository.save(note(id = "older", createdAt = 10L, text = "First thought"))
        repository.save(
            note(
                id = "newer",
                createdAt = 20L,
                text = null,
                voicePath = "voice-notes/doc-1/newer.m4a",
                durationMillis = 4_000L
            )
        )

        val notes = repository.observeNotes(DocumentId("doc-1")).first()

        assertEquals(listOf("newer", "older"), notes.map { it.id })
        assertEquals(3, notes.first().position.paragraphIndex)
        assertEquals("voice-notes/doc-1/newer.m4a", notes.first().voiceRelativePath)
    }

    @Test
    fun notesAreRemovedWhenTheirBookIsRemoved() = runBlocking {
        repository.save(note())

        database.documentDao().deleteById("doc-1")

        assertTrue(repository.observeNotes(DocumentId("doc-1")).first().isEmpty())
    }

    private fun document() = DocumentEntity(
        id = "doc-1",
        title = "Title",
        originalFileName = "title.txt",
        mimeType = "text/plain",
        sourceExtension = "txt",
        privateSourcePath = "documents/doc-1/source.txt",
        sha256 = "hash-1",
        languageTag = null,
        totalCharacterCount = 100L,
        sectionCount = 1,
        importedAt = 1L,
        lastOpenedAt = null
    )

    private fun note(
        id: String = "note-1",
        createdAt: Long = 1L,
        text: String? = "A thought",
        voicePath: String? = null,
        durationMillis: Long? = null
    ) = BookNote(
        id = id,
        documentId = DocumentId("doc-1"),
        position = DocumentPosition(paragraphIndex = 3, offsetInParagraph = 4, absoluteOffset = 44L),
        text = text,
        voiceRelativePath = voicePath,
        voiceDurationMillis = durationMillis,
        createdAtEpochMillis = createdAt
    )
}
