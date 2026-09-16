package com.noloxtreme.tts.reader.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPolicyTest {

    @Test
    fun `allows every supported extension even when a provider reports generic binary`() {
        listOf("book.txt", "book.md", "book.markdown", "book.epub", "book.pdf").forEach {
            assertTrue(
                ImportPolicy.supports(
                    ImportSource("content://book", it, "application/octet-stream", null)
                )
            )
        }
    }

    @Test
    fun `allows supported MIME types and rejects other files`() {
        assertTrue(
            ImportPolicy.supports(
                ImportSource("content://book", "untitled", "application/pdf", null)
            )
        )
        assertFalse(
            ImportPolicy.supports(
                ImportSource(
                    "content://book",
                    "report.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    null
                )
            )
        )
        assertFalse(
            ImportPolicy.supports(
                ImportSource("content://book", "cover.png", "text/plain", null)
            )
        )
    }

    @Test
    fun `allows a four MiB file but rejects anything larger`() {
        assertTrue(ImportPolicy.isWithinSizeLimit(ImportPolicy.MAX_SOURCE_BYTES))
        assertFalse(ImportPolicy.isWithinSizeLimit(ImportPolicy.MAX_SOURCE_BYTES + 1L))
        assertTrue(ImportPolicy.isWithinSizeLimit(null))
    }
}
