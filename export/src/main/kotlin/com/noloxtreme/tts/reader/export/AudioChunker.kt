package com.noloxtreme.tts.reader.export

import java.text.BreakIterator
import java.util.Locale

data class ExportChunk(val index: Int, val text: String)

/**
 * Splits full-document text into bounded, sentence-aligned utterances for
 * TextToSpeech, mirroring the live-narration chunking rules (spec Section 9.3:
 * sentence boundaries, hard cap of 3000 characters). Pure logic, no Android types.
 */
object AudioChunker {

    const val MAX_CHUNK_CHARS = 3000

    fun chunk(paragraphTexts: List<String>): List<ExportChunk> {
        val sentences = paragraphTexts.asSequence()
            .filter { it.isNotBlank() }
            .flatMap { sentencesOf(it) }
            .toList()
        val chunks = mutableListOf<String>()
        val buffer = StringBuilder()
        for (sentence in sentences) {
            if (sentence.length > MAX_CHUNK_CHARS) {
                // Pathological single sentence: flush, then hard-split it.
                if (buffer.isNotEmpty()) {
                    chunks += buffer.toString()
                    buffer.clear()
                }
                var start = 0
                while (start < sentence.length) {
                    val end = (start + MAX_CHUNK_CHARS).coerceAtMost(sentence.length)
                    chunks += sentence.substring(start, end)
                    start = end
                }
            } else if (buffer.isNotEmpty() && buffer.length + sentence.length + 1 > MAX_CHUNK_CHARS) {
                chunks += buffer.toString()
                buffer.clear()
                buffer.append(sentence)
            } else {
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(sentence)
            }
        }
        if (buffer.isNotEmpty()) chunks += buffer.toString()
        return chunks.mapIndexed { index, text -> ExportChunk(index, text) }
    }

    private fun sentencesOf(text: String): List<String> {
        val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
        iterator.setText(text)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotEmpty()) result += sentence
            start = end
            end = iterator.next()
        }
        val tail = text.substring(start).trim()
        if (tail.isNotEmpty()) result += tail
        return result
    }
}
