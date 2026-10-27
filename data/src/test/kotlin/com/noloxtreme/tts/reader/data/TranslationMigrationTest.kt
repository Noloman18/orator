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
class TranslationMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), OratorDatabase::class.java
    )

    @Test
    fun migrationPreservesExistingLibraryAndAddsNullableProvenance() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO documents (
                    id, title, originalFileName, mimeType, sourceExtension,
                    privateSourcePath, sha256, languageTag, totalCharacterCount,
                    sectionCount, importedAt, lastOpenedAt
                ) VALUES ('book', 'A book', 'book.txt', 'text/plain', 'txt',
                    'documents/book/source.txt', 'hash', 'en', 10, 1, 1, NULL)
                """
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).apply {
            query("SELECT title, translationSourceId FROM documents WHERE id = 'book'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("A book", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
            // Provenance remains valid after its original has been deleted.
            execSQL("UPDATE documents SET translationSourceId = 'removed-original' WHERE id = 'book'")
            close()
        }
    }

    private companion object {
        const val TEST_DB = "translation-migration-test"
    }
}
