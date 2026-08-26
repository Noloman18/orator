package com.noloxtreme.tts.reader.ui

import com.noloxtreme.tts.reader.domain.EpubBlock

/** One measured line of a text block. */
data class MeasuredLine(
    /**
     * Offset of the line's first character. For continuation slices this is
     * the split point: a line boundary excludes the wrapped space or newline
     * that ends the previous line, so continued slices re-wrap identically.
     */
    val charStart: Int,
    /** Offset one past the line's last visible character (trailing whitespace trimmed). */
    val charEndExclusive: Int,
    val heightPx: Float
)

/**
 * Supplies measured sizes so [EpubPagination] can pack blocks into pages
 * without knowing how text is laid out. Implementations must measure text
 * with the same styles, indents and widths the reader renders with, so pages
 * pack exactly to the viewport.
 */
interface BlockMeasurer {

    /** Whether the block is text that may be sliced across page boundaries. */
    fun isTextBlock(block: EpubBlock): Boolean

    /** The lines of a text block, in reading order, with pixel heights. */
    fun linesOf(block: EpubBlock): List<MeasuredLine>

    /** The full pixel height of a non-text block (image, divider, page label). */
    fun wholeHeightPx(block: EpubBlock): Float
}

/** A block fragment placed on a page. */
sealed interface PageItem {
    val blockIndex: Int

    /** A non-text block rendered in full: images, dividers, page labels. */
    data class Whole(override val blockIndex: Int) : PageItem

    /**
     * A contiguous character slice of a text block. Slices always start and
     * end on measured line boundaries, so rendering each slice with the same
     * style reproduces the original line breaks.
     */
    data class TextSlice(
        override val blockIndex: Int,
        val charStart: Int,
        val charEndExclusive: Int
    ) : PageItem {
        init {
            require(charStart >= 0)
            require(charEndExclusive >= charStart)
        }
    }
}

/** One Kindle-style page: the ordered block fragments that fit its viewport. */
data class Page(val items: List<PageItem>)

/** The first narratable position on a page: a text block and offset within it. */
data class PageTextAnchor(val blockIndex: Int, val charStart: Int)

/** The first text slice on [page], or null when the page carries no text. */
fun pageTextAnchor(page: Page): PageTextAnchor? =
    (page.items.firstOrNull { it is PageItem.TextSlice } as? PageItem.TextSlice)
        ?.let { PageTextAnchor(it.blockIndex, it.charStart) }

/**
 * The index of the page whose text covers the position (blockIndex, charStart),
 * or -1 when no page contains it. Used to land the reader on the page that
 * holds a given narration position. A position exactly on a slice boundary
 * belongs to the page where that character is visible (the next slice); a
 * position in the whitespace gap between two slices belongs to the previous
 * page.
 */
fun pageContaining(pages: List<Page>, blockIndex: Int, charStart: Int): Int {
    val strict = pages.indexOfFirst { page ->
        page.items.any { item ->
            item is PageItem.TextSlice &&
                item.blockIndex == blockIndex &&
                item.charStart <= charStart &&
                charStart < item.charEndExclusive
        }
    }
    if (strict >= 0) return strict
    return pages.indexOfFirst { page ->
        page.items.any { item ->
            item is PageItem.TextSlice &&
                item.blockIndex == blockIndex &&
                item.charEndExclusive == charStart
        }
    }
}

/**
 * The nearest printed page label (from the EPUB's page-list markup) at or
 * before the start of [page], so the reader can show the book's own page
 * number alongside its generated one. Null when the chapter has no labels.
 */
fun nearestPrintedPageLabel(page: Page, blocks: List<EpubBlock>): String? {
    page.items.forEach { item ->
        (blocks.getOrNull(item.blockIndex) as? EpubBlock.PageNumber)?.let { return it.label }
    }
    val pageStartIndex = page.items.firstOrNull()?.blockIndex ?: return null
    for (index in pageStartIndex - 1 downTo 0) {
        (blocks.getOrNull(index) as? EpubBlock.PageNumber)?.let { return it.label }
    }
    return null
}

/**
 * Packs a spine item's blocks into fixed-height pages, greedily filling each
 * page from top to bottom. Text blocks are sliced at measured line boundaries
 * when they do not fit; whole blocks move as units. [blockGapPx] is reserved
 * only between blocks that share a page — never between the slices of one
 * split block, and never after the last block of a page.
 */
object EpubPagination {

    fun paginate(
        blocks: List<EpubBlock>,
        pageContentHeightPx: Float,
        blockGapPx: Float,
        measurer: BlockMeasurer
    ): List<Page> {
        val gap = blockGapPx.coerceAtLeast(0f)
        if (pageContentHeightPx <= 0f) {
            // Degenerate viewport: keep everything on one clipped page.
            return if (blocks.isEmpty()) {
                emptyList()
            } else {
                listOf(Page(blocks.indices.map { PageItem.Whole(it) }))
            }
        }
        val pages = ArrayList<Page>()
        val pageItems = ArrayList<PageItem>()
        var remaining = pageContentHeightPx
        var pageHasItems = false

        fun flushPage() {
            if (pageItems.isNotEmpty()) {
                pages += Page(pageItems.toList())
                pageItems.clear()
                remaining = pageContentHeightPx
                pageHasItems = false
            }
        }

        for ((blockIndex, block) in blocks.withIndex()) {
            if (!measurer.isTextBlock(block)) {
                var gapBefore = if (pageHasItems) gap else 0f
                if (pageHasItems && blockHeight(block, measurer) + gapBefore > remaining) {
                    flushPage()
                    gapBefore = 0f
                }
                pageItems += PageItem.Whole(blockIndex)
                pageHasItems = true
                remaining -= blockHeight(block, measurer) + gapBefore
                continue
            }
            val lines = measurer.linesOf(block)
            if (lines.isEmpty()) continue
            var consumed = 0
            while (consumed < lines.size) {
                val gapBefore = if (pageHasItems) gap else 0f
                var take = 0
                var used = 0f
                while (consumed + take < lines.size) {
                    val lineHeight = lines[consumed + take].heightPx
                    val addition = lineHeight + if (take == 0) gapBefore else 0f
                    if (used + addition > remaining) break
                    used += lineHeight
                    take++
                }
                if (take == 0 && !pageHasItems) {
                    // A single line taller than the whole page: force it in (clipped).
                    take = 1
                    used = lines[consumed].heightPx
                }
                if (take == 0) {
                    flushPage()
                    continue
                }
                val startChar = lines[consumed].charStart
                val endChar = lines[consumed + take - 1].charEndExclusive
                remaining -= used + gapBefore
                pageHasItems = true
                if (endChar > startChar) {
                    pageItems += PageItem.TextSlice(blockIndex, startChar, endChar)
                }
                consumed += take
                if (consumed < lines.size) flushPage()
            }
        }
        flushPage()
        return pages
    }

    private fun blockHeight(block: EpubBlock, measurer: BlockMeasurer): Float =
        measurer.wholeHeightPx(block).coerceAtLeast(0f)
}
