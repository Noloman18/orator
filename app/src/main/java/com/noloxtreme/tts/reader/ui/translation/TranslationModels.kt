package com.noloxtreme.tts.reader.ui.translation

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.playback.VoiceInfo
import java.util.Locale

data class TranslationLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String
)

enum class TranslationUnavailableReason {
    ENGINE_UNAVAILABLE,
    NO_SUPPORTED_VOICES,
    NEED_ANOTHER_VOICE,
    TRANSLATION_UNAVAILABLE
}

enum class TranslationError {
    VOICE_UNAVAILABLE,
    LANGUAGE_UNAVAILABLE,
    NETWORK_REQUIRED,
    WIFI_REQUIRED,
    STORAGE_LOW,
    DOWNLOAD_FAILED,
    TRANSLATION_FAILED,
    SAVE_FAILED,
    DOCUMENT_MISSING
}

enum class TranslationPhase { IDLE, DOWNLOADING, TRANSLATING, SAVING, SUCCESS }

data class TranslationUiState(
    val checking: Boolean = true,
    val unavailable: TranslationUnavailableReason? = null,
    val languages: List<TranslationLanguage> = emptyList(),
    val sourceCode: String? = null,
    val targetCode: String? = null,
    val checkingModels: Boolean = false,
    val modelsNeeded: Boolean = true,
    val phase: TranslationPhase = TranslationPhase.IDLE,
    val progress: Float = 0f,
    val error: TranslationError? = null,
    val translatedDocumentId: DocumentId? = null
) {
    val busy: Boolean
        get() = phase == TranslationPhase.DOWNLOADING ||
            phase == TranslationPhase.TRANSLATING || phase == TranslationPhase.SAVING

    val canTranslate: Boolean
        get() = !checking && !checkingModels && unavailable == null && !busy &&
            phase != TranslationPhase.SUCCESS &&
            sourceCode != null && targetCode != null && sourceCode != targetCode &&
            languages.any { it.code == sourceCode } && languages.any { it.code == targetCode }
}

/** Android uses legacy aliases on some devices; use the translation model's canonical code. */
internal fun canonicalTranslationLanguage(tag: String?): String? {
    if (tag.isNullOrBlank()) return null
    val language = Locale.forLanguageTag(tag.replace('_', '-')).language.lowercase(Locale.ROOT)
    return when (language) {
        "iw" -> "he"
        "in" -> "id"
        "fil" -> "tl"
        "cmn" -> "zh"
        "nb" -> "no"
        "", "und" -> null
        else -> language
    }
}

/** Neither an advertised network voice nor one still downloading makes a language speakable. */
internal fun speakableTranslationLanguages(
    voices: List<VoiceInfo>,
    supportedCodes: Set<String>,
    displayLocale: Locale = Locale.getDefault()
): List<TranslationLanguage> = voices.asSequence()
    .filter { it.installed && !it.networkRequired }
    .mapNotNull { canonicalTranslationLanguage(it.localeLanguageTag) }
    .filter { it in supportedCodes }
    .distinct()
    .map { code ->
        val locale = Locale.forLanguageTag(code)
        TranslationLanguage(code, locale.getDisplayLanguage(displayLocale), locale.getDisplayLanguage(locale))
    }
    .sortedBy { it.displayName.lowercase(displayLocale) }
    .toList()

internal fun missingTranslationModels(source: String, target: String, downloaded: Set<String>): Set<String> =
    setOf(source, target) - "en" - downloaded

/** Chunk boundaries should not introduce word spaces into languages written without them. */
internal fun joinTranslatedChunks(chunks: List<String>, targetLanguage: String): String =
    chunks.joinToString(
        separator = if (canonicalTranslationLanguage(targetLanguage) in setOf("zh", "ja", "th")) "" else " "
    )

/**
 * Keep inference requests small. Prefer paragraph sentence/word boundaries and never split a
 * UTF-16 surrogate pair. Joining the source chunks reproduces the input exactly.
 */
internal fun translationChunks(text: String, maxLength: Int = 1_000): List<String> {
    require(maxLength >= 2)
    if (text.isEmpty()) return emptyList()
    val chunks = ArrayList<String>()
    var start = 0
    while (start < text.length) {
        var end = (start + maxLength).coerceAtMost(text.length)
        if (end < text.length) {
            if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
            val lowerBoundary = start + (end - start) / 2
            val sentenceBoundary = (end - 1 downTo lowerBoundary).firstOrNull { index ->
                text[index].isWhitespace() && index > start && text[index - 1] in ".!?。！？"
            }
            val wordBoundary = sentenceBoundary ?: (end - 1 downTo lowerBoundary)
                .firstOrNull { text[it].isWhitespace() }
            if (wordBoundary != null) end = wordBoundary + 1
        }
        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}
