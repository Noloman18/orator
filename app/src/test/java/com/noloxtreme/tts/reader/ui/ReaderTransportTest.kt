package com.noloxtreme.tts.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTransportTest {

    @Test
    fun narrationTransportForwardsToSentenceActions() {
        val calls = mutableListOf<String>()
        val transport = NarrationTransport(
            rewindAction = { calls += "rewind" },
            previousSentenceAction = { calls += "previous" },
            nextSentenceAction = { calls += "next" },
            fastForwardAction = { calls += "fastForward" }
        )

        transport.rewind()
        transport.previous()
        transport.next()
        transport.fastForward()

        assertEquals(listOf("rewind", "previous", "next", "fastForward"), calls)
    }

    @Test
    fun chapterTransportMapsSkipButtonsToChaptersAndFastButtonsToPages() {
        val calls = mutableListOf<String>()
        val transport = ChapterTransport(
            previousChapter = { calls += "previousChapter" },
            nextChapter = { calls += "nextChapter" },
            previousPage = { calls += "previousPage" },
            nextPage = { calls += "nextPage" }
        )

        transport.rewind()
        transport.previous()
        transport.next()
        transport.fastForward()

        assertEquals(
            listOf("previousPage", "previousChapter", "nextChapter", "nextPage"),
            calls
        )
    }

    @Test
    fun nextPageAdvanceReturnsTheNextPageWithinTheChapter() {
        assertEquals(PageTurnAdvance.ToPage(4), nextPageAdvance(currentPage = 3, pageCount = 10))
        assertEquals(PageTurnAdvance.ToPage(1), nextPageAdvance(currentPage = 0, pageCount = 10))
    }

    @Test
    fun nextPageAdvanceCrossesToTheNextChapterAtTheEnd() {
        assertEquals(PageTurnAdvance.CrossChapter, nextPageAdvance(currentPage = 9, pageCount = 10))
        assertEquals(PageTurnAdvance.CrossChapter, nextPageAdvance(currentPage = 0, pageCount = 1))
    }

    @Test
    fun nextPageAdvanceStaysWhileNoPagesAreMeasuredYet() {
        assertEquals(PageTurnAdvance.Stay, nextPageAdvance(currentPage = 0, pageCount = 0))
        assertEquals(PageTurnAdvance.Stay, nextPageAdvance(currentPage = 0, pageCount = -1))
    }

    @Test
    fun previousPageAdvanceGoesBackWithinChapterAndCrossesAtTheStart() {
        assertEquals(PageTurnAdvance.ToPage(2), previousPageAdvance(currentPage = 3))
        assertEquals(PageTurnAdvance.CrossChapter, previousPageAdvance(currentPage = 0))
    }
}
