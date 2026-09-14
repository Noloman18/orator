package com.noloxtreme.tts.reader.ui

import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.SpokenRange

/** Speeds exposed by the silent Read Mode, from a comfortable base pace to skimming. */
internal val VISUAL_READING_PACE_CYCLE = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f)

/** The approximate pace at 1×. A multiplier is easier to scan in the transport. */
internal const val VISUAL_READING_BASE_WORDS_PER_MINUTE = 200

internal data class TextWordRange(val start: Int, val endExclusive: Int) {
    init {
        require(start >= 0)
        require(endExclusive > start)
    }
}

/** A word currently indicated by the silent reader. */
internal data class VisualWord(
    val position: DocumentPosition,
    val range: SpokenRange
)

/** UI state shared by the generic-text and EPUB Read Mode renderers. */
internal data class VisualReadingState(
    val activeWord: VisualWord? = null,
    val isPlaying: Boolean = false,
    val pace: Float = VISUAL_READING_PACE_CYCLE.first(),
    val completed: Boolean = false
)

internal fun nextVisualReadingPace(currentPace: Float): Float =
    VISUAL_READING_PACE_CYCLE.firstOrNull { it > currentPace } ?: VISUAL_READING_PACE_CYCLE.first()

internal fun visualReadingWordDelayMillis(pace: Float): Long =
    (60_000f / (VISUAL_READING_BASE_WORDS_PER_MINUTE * pace.coerceAtLeast(0.1f)))
        .toLong()
        .coerceAtLeast(20L)

/** Returns the word containing [offset], or the first word following it. */
internal fun wordAtOrAfter(text: String, offset: Int): TextWordRange? {
    var index = offset.coerceIn(0, text.length)
    if (index < text.length && isWordCharacter(text[index])) {
        while (index > 0 && isWordCharacter(text[index - 1])) index -= 1
    } else {
        while (index < text.length && !isWordCharacter(text[index])) index += 1
    }
    if (index == text.length) return null
    val start = index
    while (index < text.length && isWordCharacter(text[index])) index += 1
    return TextWordRange(start, index)
}

/** Returns the final word ending before [offset]. */
internal fun wordBefore(text: String, offset: Int): TextWordRange? {
    var index = offset.coerceIn(0, text.length) - 1
    while (index >= 0 && !isWordCharacter(text[index])) index -= 1
    if (index < 0) return null
    val endExclusive = index + 1
    while (index >= 0 && isWordCharacter(text[index])) index -= 1
    return TextWordRange(index + 1, endExclusive)
}

private fun isWordCharacter(character: Char): Boolean =
    character.isLetterOrDigit() || character == '\'' || character == '’'
