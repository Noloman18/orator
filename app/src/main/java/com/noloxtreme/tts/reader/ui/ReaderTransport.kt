package com.noloxtreme.tts.reader.ui

/**
 * Strategy for the reader transport buttons (rewind, previous, next,
 * fast-forward). The active strategy is selected by [ReaderViewModel] from the
 * current mode: [NarrationTransport] while listening to the book, and
 * [ChapterTransport] while the visual read mode is open. Swapping the strategy
 * keeps the transport UI unchanged while its behavior follows the mode, so the
 * buttons always act on what the user is currently doing.
 */
interface ReaderTransport {
    fun rewind()
    fun previous()
    fun next()
    fun fastForward()
}

/** Audio mode: skip buttons navigate sentences and fast-forward increases speech speed. */
class NarrationTransport(
    private val rewindAction: () -> Unit,
    private val previousSentenceAction: () -> Unit,
    private val nextSentenceAction: () -> Unit,
    private val increaseSpeedAction: () -> Unit
) : ReaderTransport {
    override fun rewind() = rewindAction()
    override fun previous() = previousSentenceAction()
    override fun next() = nextSentenceAction()
    override fun fastForward() = increaseSpeedAction()
}

/**
 * Reading mode: the skip buttons move between chapters (spine items) while
 * the fast buttons turn pages inside the current chapter, mirroring the
 * swipe gesture. This keeps the fast buttons distinct from the skip buttons
 * and keeps the visual reader's page-turn behavior separate from narration
 * mode, where fast-forward changes speech speed.
 */
class ChapterTransport(
    private val previousChapter: () -> Unit,
    private val nextChapter: () -> Unit,
    private val previousPage: () -> Unit,
    private val nextPage: () -> Unit
) : ReaderTransport {
    override fun rewind() = previousPage()
    override fun previous() = previousChapter()
    override fun next() = nextChapter()
    override fun fastForward() = nextPage()
}

/** The result of requesting a page turn in read mode. */
sealed interface PageTurnAdvance {

    /** Move to this page index. */
    data class ToPage(val index: Int) : PageTurnAdvance

    /** The reader is at a page boundary: move to the adjacent chapter instead. */
    data object CrossChapter : PageTurnAdvance

    /** Stay put; no pages are measurable yet (chapter still loading or empty). */
    data object Stay : PageTurnAdvance
}

/**
 * Forward page turn: the next page, or the next chapter once the known page
 * count is exhausted. Returns [PageTurnAdvance.Stay] while the chapter has no
 * measurable pages, so a button press during loading does not skip chapters.
 */
fun nextPageAdvance(currentPage: Int, pageCount: Int): PageTurnAdvance = when {
    pageCount <= 0 -> PageTurnAdvance.Stay
    currentPage >= pageCount - 1 -> PageTurnAdvance.CrossChapter
    else -> PageTurnAdvance.ToPage(currentPage + 1)
}

/** Backward page turn: the previous page, or the previous chapter at page one. */
fun previousPageAdvance(currentPage: Int): PageTurnAdvance = if (currentPage > 0) {
    PageTurnAdvance.ToPage(currentPage - 1)
} else {
    PageTurnAdvance.CrossChapter
}
