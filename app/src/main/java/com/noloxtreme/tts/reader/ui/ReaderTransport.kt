package com.noloxtreme.tts.reader.ui

/**
 * Strategy for the reader transport buttons. The active strategy is selected
 * by [ReaderViewModel] from the current mode: [NarrationTransport] while
 * listening to the book, [VisualReadingTransport] for generic silent reading,
 * and [PagedReadingTransport] for EPUB silent reading.
 */
interface ReaderTransport {
    fun previous()
    fun next()
    fun increaseSpeed()
}

/** Audio mode: skip buttons navigate sentences and the rate button speeds up TTS. */
class NarrationTransport(
    private val previousSentenceAction: () -> Unit,
    private val nextSentenceAction: () -> Unit,
    private val increaseSpeedAction: () -> Unit
) : ReaderTransport {
    override fun previous() = previousSentenceAction()
    override fun next() = nextSentenceAction()
    override fun increaseSpeed() = increaseSpeedAction()
}

/** Read Mode: move the visual word pointer or increase its silent reading pace. */
class VisualReadingTransport(
    private val previousWordAction: () -> Unit,
    private val nextWordAction: () -> Unit,
    private val increasePaceAction: () -> Unit
) : ReaderTransport {
    override fun previous() = previousWordAction()
    override fun next() = nextWordAction()
    override fun increaseSpeed() = increasePaceAction()
}

/** EPUB Read Mode: skip buttons turn pages while the rate button changes pace. */
class PagedReadingTransport(
    private val previousPageAction: () -> Unit,
    private val nextPageAction: () -> Unit,
    private val increasePaceAction: () -> Unit
) : ReaderTransport {
    override fun previous() = previousPageAction()
    override fun next() = nextPageAction()
    override fun increaseSpeed() = increasePaceAction()
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
