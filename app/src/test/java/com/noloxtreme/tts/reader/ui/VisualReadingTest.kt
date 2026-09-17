package com.noloxtreme.tts.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisualReadingTest {

    @Test
    fun `word lookup includes the word at the current pointer`() {
        assertEquals(TextWordRange(6, 11), wordAtOrAfter("Hello world", 8))
    }

    @Test
    fun `word lookup skips punctuation and whitespace`() {
        assertEquals(TextWordRange(9, 14), wordAtOrAfter("Hello,   world!", 5))
    }

    @Test
    fun `word lookup keeps apostrophes inside a word`() {
        assertEquals(TextWordRange(0, 5), wordAtOrAfter("don't stop", 0))
    }

    @Test
    fun `previous word ends before the pointer`() {
        assertEquals(TextWordRange(0, 5), wordBefore("Hello world", 6))
        assertNull(wordBefore("Hello", 0))
    }

    @Test
    fun `sentence lookup selects one complete sentence at a time`() {
        assertEquals(TextWordRange(0, 4), sentenceAtOrAfter("One. Two?", 0))
        assertEquals(TextWordRange(5, 9), sentenceAtOrAfter("One. Two?", 4))
        assertEquals(TextWordRange(0, 4), sentenceBefore("One. Two?", 5))
    }

    @Test
    fun `reading pace cycles through five times before returning to normal`() {
        assertEquals(5f, nextVisualReadingPace(4f))
        assertEquals(1f, nextVisualReadingPace(5f))
    }

    @Test
    fun `faster reading pace uses a shorter dwell time`() {
        assertEquals(300L, visualReadingWordDelayMillis(1f))
        assertEquals(60L, visualReadingWordDelayMillis(5f))
    }

    @Test
    fun `sentence dwell time keeps the selected words per minute`() {
        val text = "One two three."

        assertEquals(
            900L,
            visualReadingUnitDelayMillis(
                pace = 1f,
                unit = VisualReadingUnit.SENTENCE,
                text = text,
                range = TextWordRange(0, text.length)
            )
        )
    }
}
