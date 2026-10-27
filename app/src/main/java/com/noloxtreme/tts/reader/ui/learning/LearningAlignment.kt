package com.noloxtreme.tts.reader.ui.learning

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.SpokenRange
import java.text.BreakIterator
import java.util.Locale

internal data class LearningTextRange(val start: Int, val endExclusive: Int)

/**
 * Translation preserves paragraph order, but does not provide word alignment.
 * Move through small source phrases in proportion to the spoken paragraph's
 * character position. Build boundaries once per passage, not on every TTS callback.
 */
internal class LearningAlignment(
    original: String,
    private val translated: String,
    originalLanguage: String?,
    translatedLanguage: String?
) {
    private val originalLength = original.length
    private val originalPhrases = phraseRanges(original, locale(originalLanguage))
    private val translatedSentences = boundaries(
        translated, BreakIterator.getSentenceInstance(locale(translatedLanguage))
    )

    fun originalRange(translatedOffset: Int): LearningTextRange? {
        if (translated.isEmpty() || originalPhrases.isEmpty()) return null
        val fraction = translatedOffset.coerceIn(0, translated.length).toDouble() / translated.length
        val offset = (fraction * originalLength).toInt()
        return originalPhrases.firstOrNull { it.endExclusive > offset } ?: originalPhrases.last()
    }

    /** Engines without range callbacks still advance a sentence at a time. */
    fun translatedRange(offset: Int, spokenRange: SpokenRange?): LearningTextRange? {
        if (translated.isEmpty()) return null
        spokenRange?.let {
            val start = it.startInParagraph.coerceIn(0, translated.length)
            val end = it.endExclusiveInParagraph.coerceIn(start, translated.length)
            if (end > start) return LearningTextRange(start, end)
        }
        return translatedSentences.firstOrNull { it.endExclusive > offset.coerceAtLeast(0) }
            ?: translatedSentences.lastOrNull()
    }
}

/** Never use another book's media-session cursor in the paired reader. */
internal fun learningPosition(
    documentId: DocumentId,
    narration: NarrationState,
    savedPosition: DocumentPosition?
): DocumentPosition = when (narration) {
    is NarrationState.Playing -> narration.safePosition.takeIf { narration.documentId == documentId }
    is NarrationState.Paused -> narration.resumePosition.takeIf { narration.documentId == documentId }
    is NarrationState.Preparing -> narration.requestedPosition.takeIf { narration.documentId == documentId }
    else -> null
} ?: savedPosition ?: DocumentPosition(0, 0, 0L)

internal fun learningSpokenRange(documentId: DocumentId, narration: NarrationState): SpokenRange? =
    when (narration) {
        is NarrationState.Playing -> narration.activeRange.takeIf { narration.documentId == documentId }
        is NarrationState.Paused -> narration.activeRange.takeIf { narration.documentId == documentId }
        else -> null
    }

private fun locale(tag: String?): Locale = tag?.let(Locale::forLanguageTag)
    ?.takeIf { it.language.isNotEmpty() } ?: Locale.ROOT

private fun phraseRanges(text: String, locale: Locale): List<LearningTextRange> {
    val words = boundaries(text, BreakIterator.getWordInstance(locale)).filter { range ->
        var offset = range.start
        var hasWord = false
        while (offset < range.endExclusive && !hasWord) {
            val point = text.codePointAt(offset)
            hasWord = Character.isLetterOrDigit(point)
            offset += Character.charCount(point)
        }
        hasWord
    }
    // Character boundaries also cover symbol-only passages and unusually long tokens.
    val units = if (words.isEmpty()) boundaries(text, BreakIterator.getCharacterInstance(locale)) else words.flatMap { word ->
        if (word.endExclusive - word.start <= 48) listOf(word)
        else boundaries(text.substring(word.start, word.endExclusive), BreakIterator.getCharacterInstance(locale))
            .chunked(12).map { LearningTextRange(word.start + it.first().start, word.start + it.last().endExclusive) }
    }
    return units.chunked(3).map { LearningTextRange(it.first().start, it.last().endExclusive) }
}

private fun boundaries(text: String, iterator: BreakIterator): List<LearningTextRange> {
    iterator.setText(text)
    val ranges = mutableListOf<LearningTextRange>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val trimmedStart = (start until end).firstOrNull { !text[it].isWhitespace() }
        val trimmedEnd = (start until end).lastOrNull { !text[it].isWhitespace() }?.plus(1)
        if (trimmedStart != null && trimmedEnd != null) ranges += LearningTextRange(trimmedStart, trimmedEnd)
        start = end
        end = iterator.next()
    }
    return ranges
}
