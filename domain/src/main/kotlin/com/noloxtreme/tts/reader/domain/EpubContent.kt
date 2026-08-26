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

    /** A page label declared by the EPUB's page list or page-break markup. */
    data class PageNumber(val label: String) : EpubBlock {
        init {
            require(label.isNotBlank())
        }
    }

    data object Divider : EpubBlock
}

/**
 * The narration text of a block: its inline runs concatenated without a
 * separator. Images, dividers and page labels contribute nothing. This is the
 * single source of truth shared by the visual reader, the paginator's
 * character offsets, and the narration paragraph stream, so the two stay
 * aligned.
 */
val EpubBlock.narrationText: String
    get() = when (this) {
        is EpubBlock.Heading -> runs.joinToString(separator = "") { it.text }
        is EpubBlock.Paragraph -> runs.joinToString(separator = "") { it.text }
        is EpubBlock.Quote -> runs.joinToString(separator = "") { it.text }
        is EpubBlock.ListItem -> runs.joinToString(separator = "") { it.text }
        is EpubBlock.Image, is EpubBlock.PageNumber, EpubBlock.Divider -> ""
    }

/**
 * How many narratable blocks (those with non-empty [EpubBlock.narrationText])
 * appear strictly before [blockIndex]. Because narration stores one paragraph
 * per narratable block, this maps a block to its paragraph index.
 */
fun narratableBlocksBefore(blocks: List<EpubBlock>, blockIndex: Int): Int =
    blocks.take(blockIndex.coerceIn(0, blocks.size)).count { it.narrationText.isNotEmpty() }

/**
 * Maps a character offset inside a block's raw inline text ([charStart]) to an
 * offset in its stored narration paragraph, whose text is the block text with
 * leading whitespace trimmed.
 */
fun narrationOffsetInParagraph(blockText: String, charStart: Int): Int =
    (charStart - blockText.takeWhile { it.isWhitespace() }.length).coerceAtLeast(0)

/**
 * Inverse of [narrationOffsetInParagraph]: maps an offset in the stored
 * narration paragraph back to a character offset in the raw block text.
 */
fun narrationOffsetInBlock(blockText: String, offsetInParagraph: Int): Int =
    offsetInParagraph.coerceAtLeast(0) + blockText.takeWhile { it.isWhitespace() }.length

/**
 * The index of the block at the given narratable rank (the rank-th block with
 * non-empty [EpubBlock.narrationText]), or null when the rank exceeds the
 * block list. Inverse of [narratableBlocksBefore].
 */
fun blockAtNarratableRank(blocks: List<EpubBlock>, rank: Int): Int? {
    if (rank < 0) return null
    var remaining = rank
    blocks.forEachIndexed { index, block ->
        if (block.narrationText.isNotEmpty()) {
            if (remaining == 0) return index
            remaining -= 1
        }
    }
    return null
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
