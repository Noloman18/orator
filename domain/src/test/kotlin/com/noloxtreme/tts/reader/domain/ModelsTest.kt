package com.noloxtreme.tts.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelsTest {
    @Test
    fun paragraphUsesUtf16Offsets() {
        val paragraph = Paragraph(
            documentId = DocumentId("book"),
            paragraphIndex = 0,
            sectionIndex = 0,
            text = "A 😀 book",
            absoluteStart = 12,
            absoluteEnd = 12L + "A 😀 book".length
        )

        assertEquals(9, paragraph.text.length)
        assertEquals(21L, paragraph.absoluteEnd)
    }

    @Test
    fun paragraphRejectsMismatchedAbsoluteEnd() {
        assertThrows(IllegalArgumentException::class.java) {
            Paragraph(
                documentId = DocumentId("book"),
                paragraphIndex = 0,
                sectionIndex = 0,
                text = "text",
                absoluteStart = 0,
                absoluteEnd = 3
            )
        }
    }

    @Test
    fun defaultsAreOfflineSafe() {
        val settings = OratorSettings()
        assertEquals(null, settings.voiceName)
        assertEquals(true, settings.followSpokenText)
        assertEquals(ThemePreference.SYSTEM, settings.theme)
    }

    @Test
    fun readerPositionAcceptsChapterStartAtPageZero() {
        assertEquals(ReaderPosition(0, 0), ReaderPosition(0, 0))
        assertEquals(ReaderPosition(4, 12), ReaderPosition(4, 12))
    }

    @Test
    fun readerPositionRejectsNegativeIndices() {
        assertThrows(IllegalArgumentException::class.java) { ReaderPosition(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { ReaderPosition(0, -1) }
    }
}
