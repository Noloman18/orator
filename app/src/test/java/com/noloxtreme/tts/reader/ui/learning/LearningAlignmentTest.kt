package com.noloxtreme.tts.reader.ui.learning

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.SpokenRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningAlignmentTest {
    private val original = "A quiet morning dawned over the village. Birds sang beside the river."
    private val translation = "Le village se réveillait doucement. Les oiseaux chantaient près de la rivière."
    private val alignment = LearningAlignment(original, translation, "en", "fr")

    @Test
    fun `source follows proportional progress through whole phrases`() {
        val start = requireNotNull(alignment.originalRange(0))
        val middle = requireNotNull(alignment.originalRange(translation.length / 2))
        val end = requireNotNull(alignment.originalRange(translation.length))
        assertEquals("A quiet morning", original.substring(start.start, start.endExclusive))
        assertTrue(middle.start > start.start)
        assertTrue(end.start > middle.start)
        assertTrue(original.substring(end.start, end.endExclusive).contains("river"))
        val positions = (0..translation.length).map { requireNotNull(alignment.originalRange(it)).start }
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun `out of bounds offsets clamp to the first and last phrases`() {
        assertEquals(alignment.originalRange(0), alignment.originalRange(-50))
        assertEquals(alignment.originalRange(translation.length), alignment.originalRange(Int.MAX_VALUE))
    }

    @Test
    fun `engines with ranges preserve precise translation highlighting`() {
        assertEquals(LearningTextRange(3, 10), alignment.translatedRange(0, SpokenRange(0, 3, 10)))
        assertEquals(LearningTextRange(3, translation.length), alignment.translatedRange(0, SpokenRange(0, 3, 500)))
    }

    @Test
    fun `engines without ranges fall back to the current sentence`() {
        val start = requireNotNull(alignment.translatedRange(0, null))
        assertEquals("Le village se réveillait doucement.", translation.substring(start.start, start.endExclusive))
        val second = requireNotNull(alignment.translatedRange(translation.indexOf("Les oiseaux"), null))
        assertTrue(second.start > start.start)
        assertEquals(second, alignment.translatedRange(Int.MAX_VALUE, null))
    }

    @Test
    fun `multilingual boundaries keep accents and surrogate pairs intact`() {
        listOf(
            "Ame\u0301lie découvre le café près de la bibliothèque.",
            "مرحبا بكم في هذا الصباح الجميل في مدينتنا القديمة",
            "今天的天气很好，我们沿着河边散步，欣赏周围的风景。",
            "😀 🌳 🌞 🦜 🌊 🎶 🌈",
            "𐐀".repeat(120)
        ).forEach { source ->
            val mapping = LearningAlignment(source, translation, null, "fr")
            (0..translation.length).forEach { offset ->
                val range = requireNotNull(mapping.originalRange(offset))
                assertTrue(range.start in source.indices)
                assertTrue(range.endExclusive in (range.start + 1)..source.length)
                assertTrue(!source[range.start].isLowSurrogate())
                assertTrue(!source[range.endExclusive - 1].isHighSurrogate())
                assertTrue(Character.getType(source[range.start]) != Character.NON_SPACING_MARK.toInt())
            }
        }
    }

    @Test
    fun `empty content has no invented marker`() {
        assertNull(LearningAlignment("", translation, null, null).originalRange(0))
        assertNull(LearningAlignment("  ", translation, null, null).originalRange(0))
        assertNull(LearningAlignment(original, "", null, null).translatedRange(0, null))
    }

    @Test
    fun `playing paused and preparing use the current book cursor`() {
        val id = DocumentId("translation")
        val position = DocumentPosition(3, 12, 300)
        val range = SpokenRange(3, 12, 20)
        listOf(
            NarrationState.Playing(id, position, range),
            NarrationState.Paused(id, position, range),
            NarrationState.Preparing(id, position)
        ).forEach { assertEquals(position, learningPosition(id, it, null)) }
        assertEquals(range, learningSpokenRange(id, NarrationState.Paused(id, position, range)))
    }

    @Test
    fun `another books media session never moves this books markers`() {
        val id = DocumentId("translation")
        val saved = DocumentPosition(2, 5, 200)
        val otherPosition = DocumentPosition(9, 15, 900)
        listOf(
            NarrationState.Playing(DocumentId("other"), otherPosition, SpokenRange(9, 15, 20)),
            NarrationState.Paused(DocumentId("other"), otherPosition, SpokenRange(9, 15, 20)),
            NarrationState.Preparing(DocumentId("other"), otherPosition)
        ).forEach {
            assertEquals(saved, learningPosition(id, it, saved))
            assertNull(learningSpokenRange(id, it))
        }
        assertEquals(DocumentPosition(0, 0, 0), learningPosition(id, NarrationState.Idle, null))
    }
}
