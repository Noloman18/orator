package com.noloxtreme.tts.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataNormalizerTest {

    @Test
    fun controlCharactersBecomeSpaces() {
        assertEquals("A B", MetadataNormalizer.normalizeTitle("A\u0001B"))
        assertEquals("A B C", MetadataNormalizer.normalizeTitle("A\u0007\u000BB\u007FC"))
    }

    @Test
    fun whitespaceIsCollapsedAndTrimmed() {
        assertEquals("A book title", MetadataNormalizer.normalizeTitle("  A   book\t\ntitle  "))
    }

    @Test
    fun blankResolvesToFallback() {
        assertEquals("Untitled book", MetadataNormalizer.normalizeTitle("  \t "))
        assertEquals("Untitled book", MetadataNormalizer.normalizeTitle("\u0000\u0001"))
    }

    @Test
    fun shortTextIsUntouched() {
        assertEquals("Title", MetadataNormalizer.normalizeTitle("Title"))
    }

    @Test
    fun truncationNeverSplitsASurrogatePair() {
        val emoji = "\uD83D\uDE00"
        val prefix = "x".repeat(199)
        val title = prefix + emoji

        val normalized = MetadataNormalizer.normalizeTitle(title)

        assertEquals(199, normalized.length)
        assertEquals(prefix, normalized)
        assertNoDanglingSurrogate(normalized)
    }

    @Test
    fun truncationStopsAtBoundaryWhenPairFits() {
        val title = "x".repeat(198) + "\uD83D\uDE00" + "rest"

        val normalized = MetadataNormalizer.normalizeTitle(title)

        assertEquals(200, normalized.length)
        assertEquals("x".repeat(198) + "\uD83D\uDE00", normalized)
        assertNoDanglingSurrogate(normalized)
    }

    private fun assertNoDanglingSurrogate(value: String) {
        for (index in value.indices) {
            if (value[index].isHighSurrogate()) {
                assertEquals(
                    "high surrogate at $index must be followed by a low surrogate",
                    true,
                    index + 1 < value.length && value[index + 1].isLowSurrogate()
                )
            } else {
                assertEquals(
                    "low surrogate at $index must follow a high surrogate",
                    false,
                    value[index].isLowSurrogate() && (index == 0 || !value[index - 1].isHighSurrogate())
                )
            }
        }
    }
}
