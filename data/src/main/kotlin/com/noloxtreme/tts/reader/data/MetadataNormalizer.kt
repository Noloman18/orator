package com.noloxtreme.tts.reader.data

/** Shared metadata normalization rules from spec Section 8.7. */
internal object MetadataNormalizer {
    private const val MAX_UTF16_UNITS = 200
    private const val FALLBACK_TITLE = "Untitled book"

    fun normalizeTitle(value: String): String {
        val sanitized = sanitize(value)
        return if (sanitized.isBlank()) FALLBACK_TITLE else sanitized
    }

    fun normalizeFileName(value: String): String {
        val sanitized = sanitize(value)
        return if (sanitized.isBlank()) FALLBACK_TITLE else sanitized
    }

    private fun sanitize(value: String): String {
        val cleaned = buildString(value.length) {
            for (character in value) {
                if (character.code < 0x20 || character.code == 0x7F) {
                    append(' ')
                } else {
                    append(character)
                }
            }
        }
        return cleaned.trim().replace(Regex("""\s+"""), " ").limitUtf16(MAX_UTF16_UNITS)
    }

    private fun String.limitUtf16(limit: Int): String {
        if (length <= limit) return this
        val truncated = substring(0, limit)
        return if (Character.isHighSurrogate(truncated.last())) {
            truncated.dropLast(1)
        } else {
            truncated
        }
    }
}
