package com.noloxtreme.tts.reader.domain

/** Translated text retains chapter membership while its character offsets are rebuilt. */
data class TranslatedParagraph(val sectionIndex: Int, val text: String)

interface TranslatedDocumentRepository {
    /** Saves a complete copy atomically, or returns the identical previously saved copy. */
    suspend fun save(
        source: Document,
        targetLanguageTag: String,
        paragraphs: List<TranslatedParagraph>,
        sectionTitles: Map<Int, String?>
    ): DocumentId
}
