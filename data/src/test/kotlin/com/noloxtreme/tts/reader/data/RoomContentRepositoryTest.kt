package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RoomContentRepositoryTest : RoomDatabaseTest() {

    private val documentId = DocumentId("sentence-navigation")
    private lateinit var repository: RoomContentRepository
    private val text = "First sentence. Second sentence remains long enough for navigation."
    private val secondSentenceStart = text.indexOf("Second")

    @Before
    fun setUp() {
        openDatabase()
        repository = RoomContentRepository(database.contentDao(), database.sectionDao())
        runBlocking {
            database.documentDao().insert(
                DocumentEntity(
                    id = documentId.value,
                    title = "Sentence navigation",
                    originalFileName = "sentences.txt",
                    mimeType = "text/plain",
                    sourceExtension = "txt",
                    privateSourcePath = "documents/${documentId.value}/source.txt",
                    sha256 = "sentence-navigation-hash",
                    languageTag = "en",
                    totalCharacterCount = text.length.toLong(),
                    sectionCount = 1,
                    importedAt = 0L,
                    lastOpenedAt = null
                )
            )
            database.contentDao().insertBatch(
                listOf(
                    ParagraphEntity(
                        documentId = documentId.value,
                        paragraphIndex = 0,
                        sectionIndex = 0,
                        text = text,
                        absoluteStart = 0L,
                        absoluteEnd = text.length.toLong()
                    )
                )
            )
        }
    }

    @After
    fun tearDown() {
        closeDatabase()
    }

    @Test
    fun previousSentenceFromOpeningWordMovesToThePriorSentence() = runBlocking {
        val position = positionAt(secondSentenceStart + "Second ".length)

        val previous = repository.sentenceBefore(documentId, position)

        assertEquals(0, previous.offsetInParagraph)
    }

    @Test
    fun previousSentenceFurtherIntoSentenceRestartsThatSentence() = runBlocking {
        val position = positionAt(secondSentenceStart + 30)

        val previous = repository.sentenceBefore(documentId, position)

        assertEquals(secondSentenceStart, previous.offsetInParagraph)
    }

    private fun positionAt(offset: Int) = DocumentPosition(
        paragraphIndex = 0,
        offsetInParagraph = offset,
        absoluteOffset = offset.toLong()
    )
}
