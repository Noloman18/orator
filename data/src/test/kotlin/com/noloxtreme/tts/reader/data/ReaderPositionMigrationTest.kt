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
class ReaderPositionMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OratorDatabase::class.java
    )

    @Test
    fun migrate1To2AddsReaderPositionsTableAndPreservesData() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO documents (
                    id, title, originalFileName, mimeType, sourceExtension,
                    privateSourcePath, sha256, languageTag, totalCharacterCount,
                    sectionCount, importedAt, lastOpenedAt
                ) VALUES (
                    'doc-1', 'Title', 'title.epub', 'application/epub+zip', 'epub',
                    'documents/doc-1/source.epub', 'hash-1', NULL, 13, 1, 1, NULL
                )
                """
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).apply {
            // The v1 data survives the migration untouched.
            query("SELECT id, title FROM documents").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("doc-1", cursor.getString(0))
                assertEquals("Title", cursor.getString(1))
            }
            // The new table accepts rows (including the document foreign key).
            execSQL(
                "INSERT INTO reader_positions (documentId, spineIndex, pageIndex) " +
                    "VALUES ('doc-1', 3, 7)"
            )
            query("SELECT spineIndex, pageIndex FROM reader_positions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
                assertEquals(7, cursor.getInt(1))
            }
            close()
        }
    }

    companion object {
        private const val TEST_DB = "reader-position-migration-test"
    }
}
