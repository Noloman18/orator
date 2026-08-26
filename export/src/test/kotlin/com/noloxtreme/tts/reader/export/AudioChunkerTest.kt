package com.noloxtreme.tts.reader.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioChunkerTest {

    @Test
    fun `blank and empty paragraphs are skipped`() {
        val chunks = AudioChunker.chunk(listOf("", "   ", "Only this remains.", ""))
        assertEquals(1, chunks.size)
        assertEquals("Only this remains.", chunks[0].text)
    }

    @Test
    fun `empty input yields no chunks`() {
        assertTrue(AudioChunker.chunk(emptyList()).isEmpty())
    }

    @Test
    fun `short paragraphs stay in one chunk joined by a space`() {
        val chunks = AudioChunker.chunk(listOf("Hello world.", "Second paragraph."))
        assertEquals(1, chunks.size)
        assertEquals("Hello world. Second paragraph.", chunks[0].text)
    }

    @Test
    fun `chunks never exceed the maximum length and preserve all text`() {
        val text = (1..200).joinToString(" ") { "Sentence number $it ends here." }
        val chunks = AudioChunker.chunk(listOf(text))
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertTrue(
                "chunk of ${chunk.text.length} exceeds max",
                chunk.text.length <= AudioChunker.MAX_CHUNK_CHARS
            )
        }
        assertEquals(text, chunks.joinToString(" ") { it.text })
    }

    @Test
    fun `chunk boundaries fall between sentences whenever possible`() {
        val sentence = "The quick brown fox jumps over the lazy dog. "
        val paragraph = sentence.repeat(70)
        val chunks = AudioChunker.chunk(listOf(paragraph))
        assertTrue(chunks.size > 1)
        // Every chunk except the last must end at a sentence boundary.
        chunks.dropLast(1).forEach { chunk ->
            assertTrue(
                "chunk does not end at a sentence boundary: ${chunk.text.takeLast(20)}",
                chunk.text.endsWith("dog.")
            )
        }
        assertEquals(paragraph.trim(), chunks.joinToString(" ") { it.text })
    }

    @Test
    fun `pathological single sentence is hard-split at the cap`() {
        val long = "x".repeat(AudioChunker.MAX_CHUNK_CHARS * 2 + 10)
        val chunks = AudioChunker.chunk(listOf(long))
        assertTrue(chunks.size >= 3)
        chunks.forEach { chunk ->
            assertTrue(chunk.text.length <= AudioChunker.MAX_CHUNK_CHARS)
        }
        assertEquals(long, chunks.joinToString("") { it.text })
    }

    @Test
    fun `chunk indexes are sequential`() {
        val chunks = AudioChunker.chunk(listOf("One. Two. Three. Four. Five."))
        assertEquals(chunks.indices.toList(), chunks.map { it.index })
    }
}
