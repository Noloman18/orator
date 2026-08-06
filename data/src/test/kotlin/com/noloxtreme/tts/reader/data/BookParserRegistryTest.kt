package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.ImportError
import com.noloxtreme.tts.reader.domain.ImportSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BookParserRegistryTest {
    private val registry = BookParserRegistry(setOf(TxtParser(), MarkdownParser(), EpubParser()))

    @Test
    fun resolvesTxtByExtension() {
        val parser = registry.resolve(
            ImportSource("content://book", "novel.txt", "application/octet-stream", null),
            File.createTempFile("orator", ".txt")
        )

        assertEquals("txt", parser.extension)
    }

    @Test
    fun rejectsConflictingFormatClaims() {
        val error = assertThrows(ImportException::class.java) {
            registry.resolve(
                ImportSource("content://book", "novel.txt", "application/epub+zip", null),
                File.createTempFile("orator", ".bin")
            )
        }

        assertEquals(ImportError.UNSUPPORTED_FORMAT, error.error)
    }

    @Test
    fun resolvesMarkdownBeforeGenericTextFallback() {
        val parser = registry.resolve(
            ImportSource("content://book", "novel.md", "application/octet-stream", null),
            File.createTempFile("orator", ".md")
        )

        assertEquals("md", parser.extension)
    }

    @Test
    fun resolvesMarkdownWhenProviderReportsPlainText() {
        val parser = registry.resolve(
            ImportSource("content://book", "novel.md", "text/plain", null),
            File.createTempFile("orator", ".md")
        )

        assertEquals("md", parser.extension)
    }
}
