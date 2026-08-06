package com.noloxtreme.tts.reader.playback

/** Deterministic title placeholder rules from spec Section 13.1. */
object TitlePlaceholder {
    val paletteArgb = intArrayOf(
        0xFF285E61.toInt(),
        0xFF6B4F7A.toInt(),
        0xFF8A5A44.toInt(),
        0xFF526D3F.toInt(),
        0xFF365B7D.toInt(),
        0xFF7A4A58.toInt()
    )

    fun backgroundColorArgb(sha256: String): Int {
        if (sha256.isEmpty()) return paletteArgb[0]
        val high = Character.digit(sha256[0], 16).coerceAtLeast(0)
        val low = if (sha256.length > 1) {
            Character.digit(sha256[1], 16).coerceAtLeast(0)
        } else {
            0
        }
        return paletteArgb[((high shl 4) or low) % paletteArgb.size]
    }

    fun initials(title: String): String {
        val words = title.trim().split(Regex("""\s+"""))
        return when {
            words.size >= 2 -> firstLetters(words[0]) + firstLetters(words[1])
            words.size == 1 -> firstTwoLetters(words[0])
            else -> "O"
        }.take(2).ifBlank { "O" }.uppercase()
    }

    private fun firstLetters(word: String): String =
        firstTwoLetters(word).take(1)

    private fun firstTwoLetters(word: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < word.length && result.length < 2) {
            val codePoint = word.codePointAt(index)
            if (Character.isLetterOrDigit(codePoint)) {
                result.appendCodePoint(Character.toUpperCase(codePoint))
            }
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }
}
