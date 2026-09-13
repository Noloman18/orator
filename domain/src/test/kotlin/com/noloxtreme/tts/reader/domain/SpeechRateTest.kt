package com.noloxtreme.tts.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRateTest {
    @Test
    fun speedButtonCyclesFromNormalToTwoTimesThenBackToNormal() {
        assertEquals(1.25f, nextSpeechRate(1.0f))
        assertEquals(1.5f, nextSpeechRate(1.25f))
        assertEquals(2.0f, nextSpeechRate(1.75f))
        assertEquals(1.0f, nextSpeechRate(2.0f))
    }
}
