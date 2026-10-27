package com.noloxtreme.tts.reader.ui.translation

import com.noloxtreme.tts.reader.playback.VoiceInfo
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRulesTest {
    @Test
    fun `languages require installed offline voices and a translation model`() {
        val languages = speakableTranslationLanguages(
            listOf(
                VoiceInfo("english-us", "en-US", false),
                VoiceInfo("english-gb", "en-GB", false),
                VoiceInfo("french-network", "fr-FR", true),
                VoiceInfo("german-pending", "de-DE", false, installed = false),
                VoiceInfo("zulu", "zu-ZA", false),
                VoiceInfo("afrikaans", "af-ZA", false)
            ),
            setOf("en", "fr", "de", "af"),
            Locale.ENGLISH
        )

        assertEquals(listOf("af", "en"), languages.map { it.code })
        assertEquals(listOf("Afrikaans", "English"), languages.map { it.displayName })
    }

    @Test
    fun `language aliases match translation models`() {
        assertEquals("he", canonicalTranslationLanguage("iw-IL"))
        assertEquals("id", canonicalTranslationLanguage("in-ID"))
        assertEquals("tl", canonicalTranslationLanguage("fil-PH"))
        assertEquals("no", canonicalTranslationLanguage("nb-NO"))
        assertEquals("nn", canonicalTranslationLanguage("nn-NO"))
        assertEquals("zh", canonicalTranslationLanguage("cmn-Hans-CN"))
        assertEquals("pt", canonicalTranslationLanguage("pt_BR"))
        assertNull(canonicalTranslationLanguage("und"))
        assertNull(canonicalTranslationLanguage(null))
    }

    @Test
    fun `english does not require a separate translation pack`() {
        assertEquals(setOf("fr"), missingTranslationModels("en", "fr", emptySet()))
        assertTrue(missingTranslationModels("fr", "en", setOf("fr")).isEmpty())
        assertEquals(setOf("de"), missingTranslationModels("fr", "de", setOf("fr")))
    }

    @Test
    fun `only a checked supported distinct pair enables translation`() {
        val languages = listOf(TranslationLanguage("en", "English", "English"), TranslationLanguage("fr", "French", "français"))
        val ready = TranslationUiState(checking = false, languages = languages, sourceCode = "en", targetCode = "fr")
        assertTrue(ready.canTranslate)
        assertFalse(ready.copy(checking = true).canTranslate)
        assertFalse(ready.copy(checkingModels = true).canTranslate)
        assertFalse(ready.copy(targetCode = "zh").canTranslate)
        assertFalse(ready.copy(sourceCode = "fr").canTranslate)
        assertFalse(ready.copy(unavailable = TranslationUnavailableReason.TRANSLATION_UNAVAILABLE).canTranslate)
        assertFalse(ready.copy(phase = TranslationPhase.DOWNLOADING).canTranslate)
        assertFalse(ready.copy(phase = TranslationPhase.SUCCESS).canTranslate)
    }

    @Test
    fun `chunking preserves all source text and prefers sentence endings`() {
        val text = "The first sentence. A much longer sentence follows here. Final words."
        val chunks = translationChunks(text, maxLength = 32)
        assertEquals(text, chunks.joinToString(""))
        assertEquals("The first sentence. ", chunks.first())
        assertTrue(chunks.all { it.length <= 32 })
    }

    @Test
    fun `chunking handles unbroken text without damaging emoji`() {
        val text = "a".repeat(999) + "\uD83C\uDF0D" + "文".repeat(2_000)
        val chunks = translationChunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 1_000 })
        assertTrue(chunks.none { Character.isHighSurrogate(it.last()) })
        assertTrue(chunks.none { Character.isLowSurrogate(it.first()) })
        assertTrue(translationChunks("").isEmpty())
    }

    @Test
    fun `joining translated chunks preserves continuous Chinese Japanese and Thai writing`() {
        assertEquals("这是一段连续文字。", joinTranslatedChunks(listOf("这是一段", "连续文字。"), "zh-CN"))
        assertEquals("日本語の文章です。", joinTranslatedChunks(listOf("日本語の", "文章です。"), "ja"))
        assertEquals("ข้อความภาษาไทย", joinTranslatedChunks(listOf("ข้อความ", "ภาษาไทย"), "th"))
        assertEquals("中文", joinTranslatedChunks(listOf("中", "文"), "cmn-Hans-CN"))
    }

    @Test
    fun `joining word separated translations keeps words apart including Korean`() {
        assertEquals("Hello world", joinTranslatedChunks(listOf("Hello", "world"), "en"))
        assertEquals("Bonjour le monde", joinTranslatedChunks(listOf("Bonjour", "le monde"), "fr"))
        assertEquals("한국어 문장입니다", joinTranslatedChunks(listOf("한국어", "문장입니다"), "ko"))
    }
}
