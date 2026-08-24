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

/** Audio mode: the transport buttons skip through the narrated sentences. */
class NarrationTransport(
    private val rewindAction: () -> Unit,
    private val previousSentenceAction: () -> Unit,
    private val nextSentenceAction: () -> Unit,
    private val fastForwardAction: () -> Unit
) : ReaderTransport {
    override fun rewind() = rewindAction()
    override fun previous() = previousSentenceAction()
    override fun next() = nextSentenceAction()
    override fun fastForward() = fastForwardAction()
}

/** Reading mode: the transport buttons move between chapters (spine items). */
class ChapterTransport(
    private val previousChapter: () -> Unit,
    private val nextChapter: () -> Unit
) : ReaderTransport {
    override fun rewind() = previousChapter()
    override fun previous() = previousChapter()
    override fun next() = nextChapter()
    override fun fastForward() = nextChapter()
}
