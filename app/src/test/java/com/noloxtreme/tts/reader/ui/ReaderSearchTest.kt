package com.noloxtreme.tts.reader.ui

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSearchTest {

    @Test
    fun `find returns exact positions for every matching paragraph`() {
        val documentId = DocumentId("search-book")
        val matches = findTextMatches(
            paragraphs = listOf(
                Paragraph(documentId, 0, 0, "A lighthouse and another Lighthouse.", 0, 36),
                Paragraph(documentId, 1, 0, "No match here.", 37, 51),
                Paragraph(documentId, 2, 0, "The lighthouse returns.", 52, 75)
            ),
            query = "lighthouse",
            limit = 10
        )

        assertEquals(3, matches.size)
        assertEquals(listOf(2, 25, 4), matches.map { it.position.offsetInParagraph })
        assertEquals(listOf(2L, 25L, 56L), matches.map { it.position.absoluteOffset })
    }

    @Test
    fun `find obeys its result limit`() {
        val documentId = DocumentId("search-book")
        val paragraph = Paragraph(documentId, 0, 0, "echo echo echo", 0, 14)

        val matches = findTextMatches(listOf(paragraph), "echo", limit = 2)

        assertEquals(listOf(0, 5), matches.map { it.position.offsetInParagraph })
    }
}
