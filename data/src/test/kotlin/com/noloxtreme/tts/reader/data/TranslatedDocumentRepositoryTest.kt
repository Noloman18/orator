package com.noloxtreme.tts.reader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.TimeProvider
import com.noloxtreme.tts.reader.domain.TranslatedParagraph
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslatedDocumentRepositoryTest : RoomDatabaseTest() {
    private lateinit var context: Context
    private lateinit var repository: RoomTranslatedDocumentRepository
    private lateinit var documents: RoomDocumentRepository
    private val source = Document(
        DocumentId("original"), "A book", "book.epub", "application/epub+zip", "source-hash",
        "en", 20L, 2, 1L, null
    )
    private val translated = listOf(
        TranslatedParagraph(0, "Bonjour."),
        TranslatedParagraph(0, "Le monde."),
        TranslatedParagraph(1, "Au revoir.")
    )
    private val titles = mapOf(0 to "Début", 1 to "Fin")

    @Before
    fun setUp() = runBlocking {
        openDatabase()
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "documents").deleteRecursively()
        File(context.cacheDir, "translations").deleteRecursively()
        repository = RoomTranslatedDocumentRepository(context, database, object : TimeProvider {
            override fun nowEpochMillis() = 100L
        })
        documents = RoomDocumentRepository(database, context)
        database.documentDao().insert(
            DocumentEntity(
                "original", "A book", "book.epub", "application/epub+zip", "epub",
                "documents/original/source.epub", "source-hash", "en", 20L, 2, 1L, null
            )
        )
    }

    @After
    fun tearDown() {
        closeDatabase()
        File(context.filesDir, "documents").deleteRecursively()
        File(context.cacheDir, "translations").deleteRecursively()
    }

    @Test
    fun copyPreservesOriginalAndBuildsPlayableParagraphsAndChapters() = runBlocking {
        val id = repository.save(source, "fr", translated, titles)
        val copy = documents.getDocument(id)!!
        val rows = database.contentDao().allForDocument(id.value)
        val sections = database.sectionDao().allForDocument(id.value)

        assertEquals(source.id, copy.translationSourceId)
        assertEquals("fr", copy.languageTag)
        assertEquals("text/plain", copy.mimeType)
        assertEquals(2, copy.sectionCount)
        assertEquals(listOf(0L, 10L, 21L), rows.map { it.absoluteStart })
        assertEquals(rows.last().absoluteEnd, copy.totalCharacterCount)
        assertEquals(listOf(0, 1, 2), rows.map { it.paragraphIndex })
        assertEquals(listOf(0, 0, 1), rows.map { it.sectionIndex })
        assertEquals(listOf("Début", "Fin"), sections.map { it.title })
        assertEquals(1, sections.first().lastParagraphIndex)
        assertEquals(2, sections.last().firstParagraphIndex)
        assertEquals(0L, database.progressDao().get(id.value)!!.absoluteOffset)
        assertEquals(source, documents.getDocument(source.id))
        assertEquals(
            "Début\n\nBonjour.\n\nLe monde.\n\nFin\n\nAu revoir.\n\n",
            File(context.filesDir, "documents/${id.value}/source.txt").readText()
        )
    }

    @Test
    fun identicalCopiesAreReusedButTargetLanguagesStayDistinct() = runBlocking {
        val first = repository.save(source, "fr", translated, titles)
        val repeated = repository.save(source, "fr", translated, titles)
        val anotherTarget = repository.save(source, "es", translated, titles)

        assertEquals(first, repeated)
        assertNotEquals(first, anotherTarget)
        assertEquals(2, File(context.filesDir, "documents").listFiles()!!.size)
        assertTrue(File(context.cacheDir, "translations").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun deletingOriginalLeavesItsTranslatedCopyReadable() = runBlocking {
        val id = repository.save(source, "fr", translated, titles)

        documents.deleteDocument(source.id)

        assertNotNull(documents.getDocument(id))
        assertEquals(3, database.contentDao().allForDocument(id.value).size)
        assertTrue(File(context.filesDir, "documents/${id.value}/source.txt").isFile)
    }

    @Test
    fun failedDatabaseInsertionRollsBackRowsAndDeletesPrivateSource() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_translation BEFORE INSERT ON paragraphs
            BEGIN SELECT RAISE(ABORT, 'injected storage failure'); END
            """
        )

        val result = runCatching { repository.save(source, "fr", translated, titles) }

        assertTrue(result.isFailure)
        assertTrue(File(context.filesDir, "documents").listFiles().orEmpty().isEmpty())
        assertTrue(File(context.cacheDir, "translations").listFiles().orEmpty().isEmpty())
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM documents").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun emptyOrOutOfOrderInputCannotPublishPartialCopies() = runBlocking {
        assertTrue(runCatching { repository.save(source, "fr", emptyList(), titles) }.isFailure)
        assertTrue(runCatching { repository.save(source, "fr", translated.reversed(), titles) }.isFailure)
        assertFalse(File(context.filesDir, "documents").exists())
    }
}
