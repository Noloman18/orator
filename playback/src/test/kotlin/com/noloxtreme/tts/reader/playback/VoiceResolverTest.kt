package com.noloxtreme.tts.reader.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceResolverTest {

    private fun voice(
        name: String,
        tag: String,
        network: Boolean = false,
        installed: Boolean = true
    ) = VoiceInfo(name, tag, network, installed)

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
    fun offlineVoicesExcludesVoicesWhoseDataIsNotInstalled() {
        val offline = VoiceResolver.offlineVoices(
            voices + voice("arabic-downloading", "ar-SA", installed = false)
        )

        assertTrue(offline.none { it.name == "arabic-downloading" })
    }

    @Test
    fun requestedVoiceIsPreferredWhenOffline() {
        val selected = VoiceResolver.select(voices, "eng-us-2", "fr-FR")

        assertEquals("eng-us-2", selected?.name)
    }

    @Test
    fun translationIgnoresSavedVoiceInAnotherLanguage() {
        val selected = VoiceResolver.select(voices, "eng-us-2", "fr-FR", requireLanguageMatch = true)

        assertEquals("fra", selected?.name)
    }

    @Test
    fun translationKeepsRequestedDialectInTheTargetLanguage() {
        val selected = VoiceResolver.select(voices, "eng-gb", "en-US", requireLanguageMatch = true)

        assertEquals("eng-gb", selected?.name)
    }

    @Test
    fun translationCannotFallBackToAnUnrelatedInstalledVoice() {
        assertNull(VoiceResolver.select(voices, "eng-us", "ja-JP", requireLanguageMatch = true))
        assertNull(VoiceResolver.select(voices, "eng-us", null, requireLanguageMatch = true))
    }

    @Test
    fun translationRejectsTargetVoicesThatNeedNetworkOrUninstalledData() {
        val unavailable = listOf(
            voice("english", "en-US"),
            voice("french-cloud", "fr-FR", network = true),
            voice("french-pending", "fr-CA", installed = false)
        )

        assertNull(VoiceResolver.select(unavailable, "french-cloud", "fr", requireLanguageMatch = true))
        assertNull(VoiceResolver.select(unavailable, "french-pending", "fr", requireLanguageMatch = true))
        assertNull(VoiceResolver.select(unavailable, "english", "fr", requireLanguageMatch = true))
    }

    @Test
    fun translationMatchesAndroidLanguageAliases() {
        listOf(
            "iw-IL" to "he",
            "in-ID" to "id",
            "nb-NO" to "no",
            "fil-PH" to "tl",
            "cmn-CN" to "zh"
        ).forEach { (voiceTag, targetTag) ->
            val selected = VoiceResolver.select(
                listOf(voice("target", voiceTag)), null, targetTag, requireLanguageMatch = true
            )

            assertEquals("Expected $voiceTag to match $targetTag", "target", selected?.name)
        }
        assertTrue(!VoiceResolver.sameLanguage("en-US", "fr"))
        assertTrue(!VoiceResolver.sameLanguage(null, null))
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
