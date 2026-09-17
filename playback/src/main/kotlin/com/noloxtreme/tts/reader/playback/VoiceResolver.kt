package com.noloxtreme.tts.reader.playback

import java.util.Locale

data class VoiceInfo(
    val name: String,
    val localeLanguageTag: String,
    val networkRequired: Boolean,
    /** False when Android reports that the voice data has not finished downloading. */
    val installed: Boolean = true
)

/**
 * Offline voice selection rules from spec Section 9.3. Pure logic, no Android types.
 */
object VoiceResolver {

    fun offlineVoices(voices: List<VoiceInfo>): List<VoiceInfo> =
        voices.filter { !it.networkRequired && it.installed }.sortedBy { it.name }

    /**
     * 1. Requested voice when installed and offline-capable.
     * 2. First installed offline voice whose locale exactly matches the document language tag.
     * 3. First installed offline voice with the same ISO language.
     * 4. Otherwise null (caller falls back to the engine default when offline-capable).
     */
    fun select(
        voices: List<VoiceInfo>,
        requestedName: String?,
        languageTag: String?
    ): VoiceInfo? {
        val offline = offlineVoices(voices)
        if (offline.isEmpty()) return null
        requestedName?.takeIf { it.isNotBlank() }?.let { name ->
            offline.firstOrNull { it.name == name }?.let { return it }
        }
        val language = languageTag
            ?.takeIf { it.isNotBlank() }
            ?.let(Locale::forLanguageTag)
            ?.takeUnless { it.language.isBlank() }
            ?: return null
        offline.firstOrNull { it.localeLanguageTag == language.toLanguageTag() }?.let { return it }
        return offline.firstOrNull {
            it.localeLanguageTag.substringBefore('-') == language.language
        }
    }
}
