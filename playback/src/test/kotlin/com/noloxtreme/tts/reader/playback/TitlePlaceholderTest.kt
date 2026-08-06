package com.noloxtreme.tts.reader.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitlePlaceholderTest {

    @Test
    fun twoWordTitleUsesFirstLetterOfEachWord() {
        assertEquals("AL", TitlePlaceholder.initials("A Long Title Here"))
        assertEquals("TW", TitlePlaceholder.initials("The Way of Kings"))
    }

    @Test
    fun oneWordTitleUsesFirstTwoLetters() {
        assertEquals("PR", TitlePlaceholder.initials("Pride"))
    }

    @Test
    fun nonLetterWordsAreSkipped() {
        assertEquals("O", TitlePlaceholder.initials("!!! Outbound"))
        assertEquals("1B", TitlePlaceholder.initials("1234 book"))
    }

    @Test
    fun noLettersFallsBackToO() {
        assertEquals("O", TitlePlaceholder.initials("!!!"))
        assertEquals("O", TitlePlaceholder.initials(""))
    }

    @Test
    fun unicodeLettersAreSupported() {
        assertEquals("\u00C9L", TitlePlaceholder.initials("\u00C9cole La Fontaine"))
        assertEquals("\u0410\u0417", TitlePlaceholder.initials("\u0410\u0437\u0431\u0443\u043A\u0430"))
    }

    @Test
    fun emojiAreSkippedWithoutSplittingSurrogates() {
        val initials = TitlePlaceholder.initials("\uD83D\uDE00 Novel")
        assertEquals("N", initials)
    }

    @Test
    fun paletteIsDeterministicFromShaPrefix() {
        val first = TitlePlaceholder.backgroundColorArgb("ff00000000000000000000000000000000000000000000000000000000000000")
        val again = TitlePlaceholder.backgroundColorArgb("ff00000000000000000000000000000000000000000000000000000000000000")
        assertEquals(first, again)
        assertTrue(first in TitlePlaceholder.paletteArgb)
    }

    @Test
    fun differentShasMapIntoPaletteRange() {
        for (index in 0..31) {
            val hash = "0f".repeat(index) + "ff" + "00".repeat(30)
            val color = TitlePlaceholder.backgroundColorArgb(hash)
            assertTrue(
                "color $color out of range for $hash",
                color in TitlePlaceholder.paletteArgb
            )
        }
    }

    @Test
    fun emptyShaUsesFirstPaletteColor() {
        assertEquals(TitlePlaceholder.paletteArgb[0], TitlePlaceholder.backgroundColorArgb(""))
    }
}
