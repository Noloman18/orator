package com.noloxtreme.tts.reader.ui

import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.SpokenRange
import java.text.BreakIterator
import java.util.Locale

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

/** How much text is highlighted at once in silent Read Mode. */
internal enum class VisualReadingUnit {
    WORD,
    SENTENCE;

    fun next(): VisualReadingUnit = when (this) {
        WORD -> SENTENCE
        SENTENCE -> WORD
    }
}

/** A contiguous visual selection currently indicated by the silent reader. */
internal data class VisualWord(
    val position: DocumentPosition,
    val range: SpokenRange,
    /** Source paragraph, retained so sentence dwell time needs no second database read. */
    val text: String
)

/** UI state shared by the generic-text and EPUB Read Mode renderers. */
internal data class VisualReadingState(
    val activeWord: VisualWord? = null,
    val isPlaying: Boolean = false,
    val pace: Float = VISUAL_READING_PACE_CYCLE.first(),
    val unit: VisualReadingUnit = VisualReadingUnit.WORD,
    val completed: Boolean = false
)

internal fun nextVisualReadingPace(currentPace: Float): Float =
    VISUAL_READING_PACE_CYCLE.firstOrNull { it > currentPace } ?: VISUAL_READING_PACE_CYCLE.first()

internal fun visualReadingWordDelayMillis(pace: Float): Long =
    (60_000f / (VISUAL_READING_BASE_WORDS_PER_MINUTE * pace.coerceAtLeast(0.1f)))
        .toLong()
        .coerceAtLeast(20L)

/**
 * A sentence stays visible for the same per-word duration as Word mode, so
 * changing the highlighting style never changes the selected words-per-minute.
 */
internal fun visualReadingUnitDelayMillis(
    pace: Float,
    unit: VisualReadingUnit,
    text: String,
    range: TextWordRange
): Long = when (unit) {
    VisualReadingUnit.WORD -> visualReadingWordDelayMillis(pace)
    VisualReadingUnit.SENTENCE ->
        visualReadingWordDelayMillis(pace) * wordCount(text, range).coerceAtLeast(1)
}

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

/** Returns the sentence containing [offset], or the first one after it. */
internal fun sentenceAtOrAfter(text: String, offset: Int): TextWordRange? =
    sentenceRanges(text).firstOrNull { range -> range.endExclusive > offset.coerceIn(0, text.length) }

/** Returns the sentence that ends at or before [offset]. */
internal fun sentenceBefore(text: String, offset: Int): TextWordRange? =
    sentenceRanges(text).lastOrNull { range -> range.endExclusive <= offset.coerceIn(0, text.length) }

private fun sentenceRanges(text: String): List<TextWordRange> {
    if (text.isBlank()) return emptyList()
    val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
    iterator.setText(text)
    val ranges = mutableListOf<TextWordRange>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val contentStart = (start until end).firstOrNull { index -> !text[index].isWhitespace() }
        val contentEnd = (start until end).lastOrNull { index -> !text[index].isWhitespace() }?.plus(1)
        if (contentStart != null && contentEnd != null && contentEnd > contentStart) {
            ranges += TextWordRange(contentStart, contentEnd)
        }
        start = end
        end = iterator.next()
    }
    return ranges
}

private fun wordCount(text: String, range: TextWordRange): Int {
    var count = 0
    var offset = range.start
    while (offset < range.endExclusive) {
        val word = wordAtOrAfter(text, offset) ?: break
        if (word.start >= range.endExclusive) break
        count += 1
        offset = word.endExclusive
    }
    return count
}

private fun isWordCharacter(character: Char): Boolean =
    character.isLetterOrDigit() || character == '\'' || character == '’'
