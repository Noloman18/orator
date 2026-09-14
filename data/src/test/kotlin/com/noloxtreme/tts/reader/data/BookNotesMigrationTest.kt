package com.noloxtreme.tts.reader.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BookNotesMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OratorDatabase::class.java
    )

    @Test
    fun migrate2To3AddsBookNotesAndPreservesDocuments() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO documents (
                    id, title, originalFileName, mimeType, sourceExtension,
                    privateSourcePath, sha256, languageTag, totalCharacterCount,
                    sectionCount, importedAt, lastOpenedAt
                ) VALUES (
                    'doc-1', 'Title', 'title.txt', 'text/plain', 'txt',
                    'documents/doc-1/source.txt', 'hash-1', NULL, 100, 1, 1, NULL
                )
                """
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).apply {
            execSQL(
                """
                INSERT INTO book_notes (
                    id, documentId, paragraphIndex, offsetInParagraph, absoluteOffset,
                    text, voiceRelativePath, voiceDurationMillis, createdAt
                ) VALUES ('note-1', 'doc-1', 3, 4, 44, 'A thought', NULL, NULL, 5)
                """
            )
            query("SELECT text, absoluteOffset FROM book_notes WHERE id = 'note-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("A thought", cursor.getString(0))
                assertEquals(44L, cursor.getLong(1))
            }
            query("SELECT title FROM documents WHERE id = 'doc-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Title", cursor.getString(0))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "book-notes-migration-test"
    }
}
