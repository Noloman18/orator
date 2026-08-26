package com.noloxtreme.tts.reader.ui

import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.EpubRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPaginationTest {

    // ------------------------------------------------------------------
    // Fakes and helpers
    // ------------------------------------------------------------------

    /** Scripted sizes keyed by block value; unscripted blocks measure zero. */
    private class ScriptedMeasurer(
        private val textLines: Map<EpubBlock, List<MeasuredLine>> = emptyMap(),
        private val wholeHeights: Map<EpubBlock, Float> = emptyMap()
    ) : BlockMeasurer {
        override fun isTextBlock(block: EpubBlock): Boolean = textLines.containsKey(block)
        override fun linesOf(block: EpubBlock): List<MeasuredLine> = textLines[block] ?: emptyList()
        override fun wholeHeightPx(block: EpubBlock): Float = wholeHeights[block] ?: 0f
    }

    private fun paragraph(text: String) = EpubBlock.Paragraph(listOf(EpubRun(text)))

    private fun image(path: String) = EpubBlock.Image(resourcePath = path, contentDescription = null)

    /** Scripts one line of 10 characters per height given. */
    private fun lines(vararg heights: Float): List<MeasuredLine> {
        var start = 0
        return heights.map { height ->
            MeasuredLine(charStart = start, charEndExclusive = start + 10, heightPx = height)
                .also { start += 10 }
        }
    }

    private val gap = 10f

    // ------------------------------------------------------------------
    // Page text anchor
    // ------------------------------------------------------------------

    @Test
    fun pageTextAnchorFindsTheFirstTextSlice() {
        val page = Page(
            listOf(
                PageItem.Whole(1),               // a leading image
                PageItem.TextSlice(2, 40, 60),
                PageItem.TextSlice(3, 0, 10)
            )
        )

        assertEquals(PageTextAnchor(2, 40), pageTextAnchor(page))
    }

    @Test
    fun pageTextAnchorIsNullForAWholeBlockOnlyPage() {
        val page = Page(listOf(PageItem.Whole(0)))

        assertEquals(null, pageTextAnchor(page))
    }

    @Test
    fun pageContainingFindsThePageHoldingACharacterPosition() {
        val pages = listOf(
            Page(listOf(PageItem.TextSlice(0, 0, 20))),
            Page(listOf(PageItem.TextSlice(0, 20, 40), PageItem.TextSlice(1, 0, 10))),
            Page(listOf(PageItem.TextSlice(1, 10, 30)))
        )

        assertEquals(0, pageContaining(pages, blockIndex = 0, charStart = 10))
        assertEquals(1, pageContaining(pages, blockIndex = 0, charStart = 20))
        assertEquals(1, pageContaining(pages, blockIndex = 1, charStart = 0))
        assertEquals(2, pageContaining(pages, blockIndex = 1, charStart = 10))
        assertEquals(2, pageContaining(pages, blockIndex = 1, charStart = 30))
    }

    @Test
    fun pageContainingReturnsMinusOneForAPositionNoPageHolds() {
        val pages = listOf(Page(listOf(PageItem.TextSlice(0, 0, 20))))

        assertEquals(-1, pageContaining(pages, blockIndex = 1, charStart = 0))
        assertEquals(-1, pageContaining(pages, blockIndex = 0, charStart = 25))
    }

    // ------------------------------------------------------------------
    // Packing and page breaks
    // ------------------------------------------------------------------

    @Test
    fun shortParagraphFillsSinglePage() {
        val block = paragraph("hello")
        val measurer = ScriptedMeasurer(textLines = mapOf(block to lines(40f, 40f)))

        val pages = EpubPagination.paginate(listOf(block), 200f, gap, measurer)

        assertEquals(1, pages.size)
        assertEquals(listOf(PageItem.TextSlice(0, 0, 20)), pages.single().items)
    }

    @Test
    fun paragraphsPackGreedilyUntilThePageIsFull() {
        val a = paragraph("a")
        val b = paragraph("b")
        val c = paragraph("c")
        val measurer = ScriptedMeasurer(
            mapOf(a to lines(100f), b to lines(100f), c to lines(100f))
        )

        val pages = EpubPagination.paginate(listOf(a, b, c), 250f, gap, measurer)

        assertEquals(2, pages.size)
        assertEquals(
            listOf(PageItem.TextSlice(0, 0, 10), PageItem.TextSlice(1, 0, 10)),
            pages[0].items
        )
        assertEquals(listOf(PageItem.TextSlice(2, 0, 10)), pages[1].items)
    }

    @Test
    fun longParagraphSplitsAtLineBoundariesAcrossPages() {
        val block = paragraph("x")
        val measurer = ScriptedMeasurer(textLines = mapOf(block to lines(40f, 40f, 40f, 40f, 40f)))

        val pages = EpubPagination.paginate(listOf(block), 100f, gap, measurer)

        assertEquals(3, pages.size)
        assertEquals(listOf(PageItem.TextSlice(0, 0, 20)), pages[0].items)
        assertEquals(listOf(PageItem.TextSlice(0, 20, 40)), pages[1].items)
        assertEquals(listOf(PageItem.TextSlice(0, 40, 50)), pages[2].items)
    }

    @Test
    fun remainingHeightTooSmallForNextLineBreaksThePage() {
        val block = paragraph("p")
        val measurer = ScriptedMeasurer(textLines = mapOf(block to lines(90f, 90f, 90f)))

        val pages = EpubPagination.paginate(listOf(block), 100f, 0f, measurer)

        assertEquals(3, pages.size)
    }

    @Test
    fun tinyPageForcesOneLinePerPageWithoutInfiniteLoop() {
        val block = paragraph("p")
        val measurer = ScriptedMeasurer(textLines = mapOf(block to lines(40f, 40f)))

        val pages = EpubPagination.paginate(listOf(block), 10f, 0f, measurer)

        assertEquals(2, pages.size)
        assertEquals(listOf(PageItem.TextSlice(0, 0, 10)), pages[0].items)
        assertEquals(listOf(PageItem.TextSlice(0, 10, 20)), pages[1].items)
    }

    @Test
    fun continuationStartsAtNextLineStartNotAtPreviousVisibleEnd() {
        val block = paragraph("x")
        // Line 0's visible end is 7, but the wrapped space at char 7 belongs
        // to neither slice: line 1 starts at char 8. Continued slices must
        // start at 8 so re-rendering reproduces the original line breaks.
        val measurer = ScriptedMeasurer(
            textLines = mapOf(
                block to listOf(
                    MeasuredLine(charStart = 0, charEndExclusive = 7, heightPx = 40f),
                    MeasuredLine(charStart = 8, charEndExclusive = 18, heightPx = 40f),
                    MeasuredLine(charStart = 18, charEndExclusive = 28, heightPx = 40f)
                )
            )
        )

        val pages = EpubPagination.paginate(listOf(block), 60f, 0f, measurer)

        assertEquals(3, pages.size)
        assertEquals(listOf(PageItem.TextSlice(0, 0, 7)), pages[0].items)
        assertEquals(listOf(PageItem.TextSlice(0, 8, 18)), pages[1].items)
        assertEquals(listOf(PageItem.TextSlice(0, 18, 28)), pages[2].items)
    }

    // ------------------------------------------------------------------
    // Gap accounting
    // ------------------------------------------------------------------

    @Test
    fun gapIsReservedBetweenBlocksSharingAPage() {
        val a = paragraph("a")
        val b = paragraph("b")
        val c = paragraph("c")
        val fit = ScriptedMeasurer(mapOf(a to lines(40f), b to lines(50f)))

        // 40 + gap 10 + 50 = 100: both blocks share the page.
        assertEquals(1, EpubPagination.paginate(listOf(a, b), 100f, gap, fit).size)

        // 40 + gap 10 + 51 > 100: the second block moves to its own page.
        val overflow = ScriptedMeasurer(mapOf(a to lines(40f), c to lines(51f)))
        val pages = EpubPagination.paginate(listOf(a, c), 100f, gap, overflow)

        assertEquals(2, pages.size)
        assertEquals(listOf(PageItem.TextSlice(1, 0, 10)), pages[1].items)
    }

    @Test
    fun gapIsNeverChargedBetweenSlicesOfOneSplitBlock() {
        val block = paragraph("x")
        val measurer = ScriptedMeasurer(textLines = mapOf(block to lines(60f, 40f)))

        // 60 + 40 = 100 fills the page exactly; a spurious 10 px gap would
        // push the second line onto a second page.
        val pages = EpubPagination.paginate(listOf(block), 100f, gap, measurer)

        assertEquals(1, pages.size)
        assertEquals(listOf(PageItem.TextSlice(0, 0, 20)), pages.single().items)
    }

    // ------------------------------------------------------------------
    // Whole blocks (images, dividers, page labels)
    // ------------------------------------------------------------------

    @Test
    fun wholeBlocksSharePagesWithTextAndBreakWhenNeeded() {
        val paragraphBlock = paragraph("p")
        val imageBlock = image("cover.png")
        val packed = ScriptedMeasurer(mapOf(paragraphBlock to lines(40f)), mapOf(imageBlock to 50f))

        // 40 + gap 10 + 50 = 100: the image fits beside the paragraph.
        val shared = EpubPagination.paginate(listOf(paragraphBlock, imageBlock), 100f, gap, packed)
        assertEquals(1, shared.size)
        assertEquals(
            listOf(PageItem.TextSlice(0, 0, 10), PageItem.Whole(1)),
            shared.single().items
        )

        // 40 + gap 10 + 60 > 100: the image moves to its own page.
        val tall = ScriptedMeasurer(mapOf(paragraphBlock to lines(40f)), mapOf(imageBlock to 60f))
        val split = EpubPagination.paginate(listOf(paragraphBlock, imageBlock), 100f, gap, tall)
        assertEquals(2, split.size)
        assertEquals(listOf(PageItem.Whole(1)), split[1].items)
    }

    @Test
    fun oversizedWholeBlockStillGetsPlacedOnItsOwnPage() {
        val imageBlock = image("huge.png")
        val measurer = ScriptedMeasurer(wholeHeights = mapOf(imageBlock to 500f))

        val pages = EpubPagination.paginate(listOf(imageBlock), 200f, gap, measurer)

        assertEquals(1, pages.size)
        assertEquals(listOf(PageItem.Whole(0)), pages.single().items)
    }

    @Test
    fun wholeBlocksPreserveOrderAndAreNeverSliced() {
        val first = image("1.png")
        val second = image("2.png")
        val measurer = ScriptedMeasurer(wholeHeights = mapOf(first to 300f, second to 300f))

        val pages = EpubPagination.paginate(listOf(first, second), 200f, gap, measurer)

        assertEquals(2, pages.size)
        assertEquals(listOf(PageItem.Whole(0)), pages[0].items)
        assertEquals(listOf(PageItem.Whole(1)), pages[1].items)
    }

    // ------------------------------------------------------------------
    // Edge cases
    // ------------------------------------------------------------------

    @Test
    fun exactFillLeavesNoTrailingEmptyPage() {
        val block = paragraph("p")
        val measurer = ScriptedMeasurer(textLines = mapOf(block to lines(100f)))

        val pages = EpubPagination.paginate(listOf(block), 100f, 0f, measurer)

        assertEquals(1, pages.size)
    }

    @Test
    fun emptyChapterProducesNoPages() {
        val pages = EpubPagination.paginate(emptyList(), 100f, gap, ScriptedMeasurer())

        assertTrue(pages.isEmpty())
    }

    @Test
    fun textBlockWithNoMeasurableLinesIsSkipped() {
        val block = paragraph("p")
        val measurer = ScriptedMeasurer(textLines = mapOf(block to emptyList()))

        val pages = EpubPagination.paginate(listOf(block), 100f, gap, measurer)

        assertTrue(pages.isEmpty())
    }

    @Test
    fun degenerateZeroHeightViewportKeepsSingleClippedPage() {
        val paragraphBlock = paragraph("p")
        val imageBlock = image("img.png")
        val measurer = ScriptedMeasurer(
            mapOf(paragraphBlock to lines(40f)),
            mapOf(imageBlock to 50f)
        )

        val pages = EpubPagination.paginate(listOf(paragraphBlock, imageBlock), 0f, gap, measurer)

        assertEquals(1, pages.size)
        assertEquals(listOf(PageItem.Whole(0), PageItem.Whole(1)), pages.single().items)
    }

    // ------------------------------------------------------------------
    // Coverage invariants
    // ------------------------------------------------------------------

    @Test
    fun slicesCoverEveryTextBlockExactlyOnceInOrder() {
        val a = paragraph("a")
        val b = paragraph("b")
        val c = paragraph("c")
        val measurer = ScriptedMeasurer(
            mapOf(
                a to lines(40f, 40f, 40f),
                b to lines(60f, 60f),
                c to lines(20f)
            )
        )
        val blocks = listOf(a, b, c)

        val pages = EpubPagination.paginate(blocks, 100f, gap, measurer)

        val slicesByBlock = pages.flatMap { it.items }
            .mapNotNull { it as? PageItem.TextSlice }
            .groupBy { it.blockIndex }
        assertEquals(setOf(0, 1, 2), slicesByBlock.keys)
        blocks.forEachIndexed { blockIndex, block ->
            val slices = slicesByBlock.getValue(blockIndex)
            var cursor = 0
            for (slice in slices) {
                assertEquals(cursor, slice.charStart)
                assertTrue(slice.charEndExclusive > slice.charStart)
                cursor = slice.charEndExclusive
            }
            assertEquals(measurer.linesOf(block).size * 10, cursor)
        }
    }
}
