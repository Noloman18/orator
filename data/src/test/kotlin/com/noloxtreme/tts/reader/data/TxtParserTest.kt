package com.noloxtreme.tts.reader.data

import java.io.File
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TxtParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val parser = TxtParser()

    private fun parse(file: File): List<ParsedBlock> = runBlocking {
        val blocks = mutableListOf<ParsedBlock>()
        parser.forEachBlock(file) { blocks += it }
        blocks
    }

    private fun writeTxt(content: ByteArray, name: String = "book.txt"): File =
        temporaryFolder.newFile(name).apply { writeBytes(content) }

    @Test
    fun utf8WithoutBomIsParsed() {
        val file = writeTxt("Hello world.\n\nSecond paragraph.\n".toByteArray(StandardCharsets.UTF_8))

        val blocks = parse(file)

        assertEquals(listOf("Hello world.", "Second paragraph."), blocks.map { it.text })
        assertEquals(listOf(0, 0), blocks.map { it.sectionIndex })
        assertEquals(listOf(null, null), blocks.map { it.sectionTitle })
    }

    @Test
    fun utf8WithBomIsParsed() {
        val content = "\uFEFFBom file starts here.\n".toByteArray(StandardCharsets.UTF_8)
        val file = writeTxt(content)

        assertEquals(listOf("Bom file starts here."), parse(file).map { it.text })
    }

    @Test
    fun utf16LeWithBomIsParsed() {
        val content = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "Hello UTF-16.\n".toByteArray(StandardCharsets.UTF_16LE)
        val file = writeTxt(content)

        assertEquals(listOf("Hello UTF-16."), parse(file).map { it.text })
    }

    @Test
    fun utf16BeWithBomIsParsed() {
        val content = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "Hello UTF-16.\n".toByteArray(StandardCharsets.UTF_16BE)
        val file = writeTxt(content)

        assertEquals(listOf("Hello UTF-16."), parse(file).map { it.text })
    }

    @Test
    fun crlfAndBlankLinesSplitParagraphs() {
        val file = writeTxt("First line\r\ncontinued here.\r\n\r\n\r\nThird paragraph.\r\n".toByteArray())

        val blocks = parse(file)

        assertEquals(
            listOf("First line continued here.", "Third paragraph."),
            blocks.map { it.text }
        )
    }

    @Test
    fun intraParagraphWhitespaceIsCollapsed() {
        val file = writeTxt("Spaces  and\ttabs   and newlines\nwithin one paragraph.\n".toByteArray())

        assertEquals(
            listOf("Spaces and tabs and newlines within one paragraph."),
            parse(file).map { it.text }
        )
    }

    @Test
    fun emojiContentIsPreserved() {
        val file = writeTxt("An emoji \uD83D\uDE00 paragraph.\n".toByteArray(StandardCharsets.UTF_8))

        val text = parse(file).single().text

        assertEquals("An emoji \uD83D\uDE00 paragraph.", text)
    }

    @Test
    fun malformedUtf8IsRejected() {
        val file = writeTxt(byteArrayOf(0x61.toByte(), 0xE2.toByte(), 0x28.toByte(), 0xA1.toByte(), 0x0A))

        assertThrows(CharacterCodingException::class.java) { parse(file) }
    }

    @Test
    fun metadataTitleComesFromFilenameWithoutExtension() {
        val file = writeTxt("Content\n".toByteArray(), name = "My Great Novel.txt")

        val metadata = parser.readMetadata(file, "My Great Novel.txt")

        assertEquals("My Great Novel", metadata.title)
        assertEquals("text/plain", metadata.mimeType)
        assertEquals(null, metadata.languageTag)
    }

    @Test
    fun metadataTitleNormalizesControlCharacters() {
        val file = writeTxt("Content\n".toByteArray(), name = "safe.txt")

        val metadata = parser.readMetadata(file, "Nov\u0001el\u0000\u0007.txt")

        assertEquals("Nov el", metadata.title)
    }

    @Test
    fun parserIsDeterministicAcrossEncodings() {
        val text = "Caf\u00E9 \u00FCniversal.\n\nZwei.\n"
        val utf8 = writeTxt(text.toByteArray(StandardCharsets.UTF_8), "a.txt")
        val utf16 = writeTxt(
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(StandardCharsets.UTF_16LE),
            "b.txt"
        )

        assertEquals(parse(utf8).map { it.text }, parse(utf16).map { it.text })
        assertTrue(parse(utf8).map { it.text }.contains("Caf\u00E9 \u00FCniversal."))
    }
}
