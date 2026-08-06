package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.ImportSource
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    private val parser = MarkdownParser()

    @Test
    fun extractsReadableTextAndHeadingSections() = runTest {
        val file = tempMarkdown(
            """
            # My *Novel*

            An **important** [opening](https://example.com) paragraph.

            - First item
            - Second `item`

            ```kotlin
            println("not narrated")
            ```

            ## Chapter Two

            The snake_case ending is ~~not crossed out~~.
            """.trimIndent()
        )
        val blocks = mutableListOf<ParsedBlock>()

        val metadata = parser.readMetadata(file, "fallback.md")
        parser.forEachBlock(file, blocks::add)

        assertEquals("My Novel", metadata.title)
        assertEquals("text/markdown", metadata.mimeType)
        assertEquals(
            listOf(
                "My Novel",
                "An important opening paragraph.",
                "First item",
                "Second item",
                "Chapter Two",
                "The snake_case ending is not crossed out."
            ),
            blocks.map { it.text }
        )
        assertEquals(listOf(0, 0, 0, 0, 1, 1), blocks.map { it.sectionIndex })
        assertTrue(blocks.none { it.text.contains("println") })
    }

    @Test
    fun supportsUtf16MarkdownAndExtensionClaims() = runTest {
        val file = File.createTempFile("orator-markdown", ".md").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "# Unicode\n\nHello Ω".toByteArray(Charsets.UTF_16LE))
        }
        val blocks = mutableListOf<ParsedBlock>()

        parser.forEachBlock(file, blocks::add)

        assertTrue(parser.accepts(ImportSource("content://book", "book.md", "application/octet-stream", null)))
        assertEquals(listOf("Unicode", "Hello Ω"), blocks.map { it.text })
    }

    private fun tempMarkdown(content: String): File =
        File.createTempFile("orator-markdown", ".md").apply {
            deleteOnExit()
            writeText(content)
        }
}
