package com.noloxtreme.tts.reader.data

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DaoContractTest : RoomDatabaseTest() {

    @Before
    fun setUp() {
        openDatabase()
    }

    @After
    fun tearDown() {
        closeDatabase()
    }

    private fun document(id: String = "doc-1", sha256: String = "hash-1") = DocumentEntity(
        id = id,
        title = "Title",
        originalFileName = "title.txt",
        mimeType = "text/plain",
        sourceExtension = "txt",
        privateSourcePath = "documents/$id/source.txt",
        sha256 = sha256,
        languageTag = null,
        totalCharacterCount = 6,
        sectionCount = 1,
        importedAt = 1L,
        lastOpenedAt = null
    )

    private fun paragraph(documentId: String, index: Int, text: String) = ParagraphEntity(
        documentId = documentId,
        paragraphIndex = index,
        sectionIndex = 0,
        text = text,
        absoluteStart = index * 8L,
        absoluteEnd = index * 8L + text.length
    )

    private fun section(documentId: String) = SectionEntity(
        documentId = documentId,
        sectionIndex = 0,
        title = null,
        firstParagraphIndex = 0,
        lastParagraphIndex = 0,
        absoluteStart = 0L,
        absoluteEnd = 6L
    )

    private fun progress(documentId: String) = ReadingProgressEntity(
        documentId = documentId,
        paragraphIndex = 0,
        offsetInParagraph = 0,
        absoluteOffset = 0L,
        updatedAt = 1L,
        isCompleted = false
    )

    @Test
    fun deletingDocumentCascadesThroughAllChildRows() {
        runBlocking {
            database.documentDao().insert(document())
            database.sectionDao().insertAll(listOf(section("doc-1")))
            database.contentDao().insertBatch(
                listOf(paragraph("doc-1", 0, "Hello"), paragraph("doc-1", 1, "World"))
            )
            database.progressDao().upsert(progress("doc-1"))
        }

        runBlocking { database.documentDao().deleteById("doc-1") }

        assertNull(runBlocking { database.documentDao().getById("doc-1") })
        assertEquals(0, runBlocking { database.sectionDao().allForDocument("doc-1") }.size)
        assertEquals(0, runBlocking { database.contentDao().pagingSource("doc-1").loadAll() }.size)
        assertNull(runBlocking { database.progressDao().get("doc-1") })
    }

    @Test
    fun duplicateSha256IsRejected() {
        runBlocking { database.documentDao().insert(document()) }

        assertFailsWith(SQLiteException::class.java) {
            runBlocking { database.documentDao().insert(document(id = "doc-2", sha256 = "hash-1")) }
        }
        assertNull(runBlocking { database.documentDao().getById("doc-2") })
    }

    @Test
    fun childRowRequiresExistingParent() {
        assertFailsWith(SQLiteException::class.java) {
            runBlocking { database.contentDao().insertBatch(listOf(paragraph("missing", 0, "Hello"))) }
        }
    }

    @Test
    fun atomicTransactionRollsBackEveryInsertedRow() {
        runBlocking { database.documentDao().insert(document()) }

        assertFailsWith(IllegalStateException::class.java) {
            runBlocking {
                database.withTransaction {
                    database.contentDao().insertBatch(listOf(paragraph("doc-1", 0, "Hello")))
                    database.progressDao().upsert(progress("doc-1"))
                    throw IllegalStateException("simulated mid-transaction failure")
                }
            }
        }

        assertEquals(0, runBlocking { database.contentDao().pagingSource("doc-1").loadAll() }.size)
        assertNull(runBlocking { database.progressDao().get("doc-1") })
    }

    @Test
    fun completedProgressPositionMatchingFinalParagraphEndIsStored() {
        runBlocking {
            database.documentDao().insert(document())
            database.contentDao().insertBatch(listOf(paragraph("doc-1", 0, "Hello")))
            database.progressDao().upsert(
                progress("doc-1").copy(offsetInParagraph = 5, absoluteOffset = 5, isCompleted = true)
            )
        }

        val saved = runBlocking { database.progressDao().get("doc-1") }
        assertEquals(true, saved?.isCompleted)
        assertEquals(5, saved?.offsetInParagraph)
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

private suspend fun androidx.paging.PagingSource<Int, ParagraphEntity>.loadAll(): List<ParagraphEntity> {
    val result = load(
        androidx.paging.PagingSource.LoadParams.Refresh(
            key = 0,
            loadSize = 10_000,
            placeholdersEnabled = false
        )
    )
    return (result as androidx.paging.PagingSource.LoadResult.Page<Int, ParagraphEntity>).data
}
