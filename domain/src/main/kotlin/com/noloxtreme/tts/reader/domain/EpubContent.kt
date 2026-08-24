package com.noloxtreme.tts.reader.domain

const val EPUB_MIME_TYPE = "application/epub+zip"

/** A styled run of inline text inside a visual EPUB block. */
data class EpubRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false
) {
    init {
        require(text.isNotEmpty())
    }
}

/**
 * One renderable unit of a visual EPUB page. Blocks appear in document order;
 * images are referenced by zip-resolved resource paths and their bytes are
 * loaded separately through [EpubContentStore.imageResource].
 */
sealed interface EpubBlock {
    data class Heading(val level: Int, val runs: List<EpubRun>) : EpubBlock {
        init {
            require(level in 1..6)
            require(runs.isNotEmpty())
        }
    }

    data class Paragraph(val runs: List<EpubRun>) : EpubBlock {
        init {
            require(runs.isNotEmpty())
        }
    }

    data class Quote(val runs: List<EpubRun>) : EpubBlock {
        init {
            require(runs.isNotEmpty())
        }
    }

    data class ListItem(
        val ordered: Boolean,
        val marker: String,
        val runs: List<EpubRun>
    ) : EpubBlock {
        init {
            require(marker.isNotBlank())
            require(runs.isNotEmpty())
        }
    }

    data class Image(val resourcePath: String, val contentDescription: String?) : EpubBlock {
        init {
            require(resourcePath.isNotBlank())
        }
    }

    data object Divider : EpubBlock
}

/** The rendered content of one spine item (a chapter) of a visual EPUB. */
data class EpubSpineContent(
    val spineIndex: Int,
    val title: String?,
    val blocks: List<EpubBlock>
) {
    init {
        require(spineIndex >= 0)
    }
}

/** A clickable table-of-contents entry. Depth drives indentation in the UI. */
data class EpubTocEntry(
    val title: String,
    val spineIndex: Int,
    val depth: Int
) {
    init {
        require(title.isNotBlank())
        require(spineIndex >= 0)
        require(depth >= 0)
    }
}

/**
 * Read-only access to the visual content of an imported EPUB document. Spine
 * indices cover the book's declared cover when it has one: a synthesized cover
 * page occupies index 0 and the real spine items start at index 1.
 */
interface EpubContentStore {
    suspend fun tableOfContents(id: DocumentId): List<EpubTocEntry>
    suspend fun spineContent(id: DocumentId, spineIndex: Int): EpubSpineContent?
    suspend fun imageResource(id: DocumentId, resourcePath: String): ByteArray?
    suspend fun spineCount(id: DocumentId): Int

    /** Whether a synthesized cover page is prepended to the spine at index 0. */
    suspend fun hasCoverPage(id: DocumentId): Boolean
}
