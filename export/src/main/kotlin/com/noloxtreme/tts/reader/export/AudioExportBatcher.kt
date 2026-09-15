package com.noloxtreme.tts.reader.export

/**
 * Keeps long exports within a bounded working-set size. Batches always end on
 * an existing [ExportChunk] boundary, which is sentence-aligned except for the
 * pathological overlong-sentence case already handled by [AudioChunker].
 */
internal object AudioExportBatcher {

    /** Roughly an hour of narration at ordinary rates. */
    const val TARGET_WORDS_PER_BATCH = 10_000

    /**
     * A voice may use a much larger PCM format than another device's voice.
     * This cap keeps both the source WAV and its atomic .part sibling bounded.
     */
    const val MAX_WAV_BYTES_PER_BATCH = 384L * 1024L * 1024L

    fun shouldFinishBatch(wordCount: Int, wavBytes: Long): Boolean =
        wordCount >= TARGET_WORDS_PER_BATCH || wavBytes >= MAX_WAV_BYTES_PER_BATCH

    fun wordCount(text: String): Int {
        var count = 0
        var insideWord = false
        text.forEach { character ->
            if (character.isWhitespace()) {
                insideWord = false
            } else if (!insideWord) {
                count++
                insideWord = true
            }
        }
        return count
    }
}
