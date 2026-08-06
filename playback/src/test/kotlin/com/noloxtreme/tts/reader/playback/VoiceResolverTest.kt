package com.noloxtreme.tts.reader.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceResolverTest {

    private fun voice(name: String, tag: String, network: Boolean = false) =
        VoiceInfo(name, tag, network)

    private val voices = listOf(
        voice("network-en", "en-US", network = true),
        voice("eng-gb", "en-GB"),
        voice("eng-us", "en-US"),
        voice("fra", "fr-FR"),
        voice("zulu", "zu-ZA"),
        voice("eng-us-2", "en-US")
    )

    @Test
    fun offlineVoicesExcludesNetworkAndSortsByName() {
        val offline = VoiceResolver.offlineVoices(voices)

        assertEquals(listOf("eng-gb", "eng-us", "eng-us-2", "fra", "zulu"), offline.map { it.name })
        assertTrue(offline.none { it.name == "network-en" })
    }

    @Test
    fun requestedVoiceIsPreferredWhenOffline() {
        val selected = VoiceResolver.select(voices, "eng-us-2", "fr-FR")

        assertEquals("eng-us-2", selected?.name)
    }

    @Test
    fun requestedNetworkVoiceIsIgnored() {
        val selected = VoiceResolver.select(voices, "network-en", "en-US")

        assertEquals("eng-us", selected?.name)
    }

    @Test
    fun exactLocaleMatchWinsOverSameLanguage() {
        val selected = VoiceResolver.select(voices, null, "en-US")

        assertEquals("eng-us", selected?.name)
    }

    @Test
    fun sameLanguageFallbackWorks() {
        val selected = VoiceResolver.select(voices, null, "en-CA")

        assertEquals("eng-gb", selected?.name)
    }

    @Test
    fun noCandidateReturnsNull() {
        assertNull(VoiceResolver.select(voices, null, "xx-XX"))
        assertNull(VoiceResolver.select(voices, null, "!!!not-a-locale"))
        assertNull(VoiceResolver.select(voices, null, null))
        assertNull(VoiceResolver.select(voices, null, ""))
    }

    @Test
    fun emptyVoiceListReturnsNull() {
        assertNull(VoiceResolver.select(emptyList(), "eng-us", "en-US"))
    }
}
