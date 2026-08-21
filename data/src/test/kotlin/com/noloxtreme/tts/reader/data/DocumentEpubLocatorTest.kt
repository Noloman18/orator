package com.noloxtreme.tts.reader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.noloxtreme.tts.reader.domain.DocumentId
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DocumentEpubLocatorTest : RoomDatabaseTest() {

    private lateinit var context: Context

    @Before
    fun setUp() {
        openDatabase()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        closeDatabase()
    }

    private fun insertEpubDocument(id: String = "doc-1") = runBlocking {
        database.documentDao().insert(
            DocumentEntity(
                id = id,
                title = "Book",
                originalFileName = "book.epub",
                mimeType = "application/epub+zip",
                sourceExtension = "epub",
                privateSourcePath = "documents/$id/source.epub",
                sha256 = "hash",
                languageTag = null,
                totalCharacterCount = 1L,
                sectionCount = 1,
                importedAt = 1L,
                lastOpenedAt = null
            )
        )
    }

    private fun writeSourceFile(id: String = "doc-1") {
        val file = File(context.filesDir, "documents/$id/source.epub")
        file.parentFile.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun resolvesSourceFileFromFilesDirRelativePrivatePath() {
        insertEpubDocument()
        writeSourceFile()
        val locator = DocumentEpubLocator(database, context)

        val resolved = runBlocking { locator.sourceFile(DocumentId("doc-1")) }

        assertEquals(File(context.filesDir, "documents/doc-1/source.epub").canonicalFile, resolved)
    }

    @Test
    fun nonEpubDocumentsAreRejected() {
        runBlocking {
            database.documentDao().insert(
                DocumentEntity(
                    id = "doc-txt",
                    title = "Notes",
                    originalFileName = "notes.txt",
                    mimeType = "text/plain",
                    sourceExtension = "txt",
                    privateSourcePath = "documents/doc-txt/source.txt",
                    sha256 = "hash",
                    languageTag = null,
                    totalCharacterCount = 1L,
                    sectionCount = 1,
                    importedAt = 1L,
                    lastOpenedAt = null
                )
            )
        }
        File(context.filesDir, "documents/doc-txt/source.txt").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val locator = DocumentEpubLocator(database, context)

        assertNull(runBlocking { locator.sourceFile(DocumentId("doc-txt")) })
    }

    @Test
    fun unknownDocumentAndMissingFileReturnNull() {
        val locator = DocumentEpubLocator(database, context)

        assertNull(runBlocking { locator.sourceFile(DocumentId("missing")) })
        insertEpubDocument()
        assertNull(runBlocking { locator.sourceFile(DocumentId("doc-1")) })
    }
}
