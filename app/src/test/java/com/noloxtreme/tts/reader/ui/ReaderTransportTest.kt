package com.noloxtreme.tts.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTransportTest {

    @Test
    fun narrationTransportForwardsToSentenceAndSpeedActions() {
        val calls = mutableListOf<String>()
        val transport = NarrationTransport(
            previousSentenceAction = { calls += "previous" },
            nextSentenceAction = { calls += "next" },
            increaseSpeedAction = { calls += "increaseSpeed" }
        )

        transport.previous()
        transport.next()
        transport.increaseSpeed()

        assertEquals(listOf("previous", "next", "increaseSpeed"), calls)
    }

    @Test
    fun visualReadingTransportForwardsToWordAndPaceActions() {
        val calls = mutableListOf<String>()
        val transport = VisualReadingTransport(
            previousWordAction = { calls += "previousWord" },
            nextWordAction = { calls += "nextWord" },
            increasePaceAction = { calls += "increasePace" }
        )

        transport.previous()
        transport.next()
        transport.increaseSpeed()

        assertEquals(
            listOf("previousWord", "nextWord", "increasePace"),
            calls
        )
    }

    @Test
    fun pagedReadingTransportForwardsToPageAndPaceActions() {
        val calls = mutableListOf<String>()
        val transport = PagedReadingTransport(
            previousPageAction = { calls += "previousPage" },
            nextPageAction = { calls += "nextPage" },
            increasePaceAction = { calls += "increasePace" }
        )

        transport.previous()
        transport.next()
        transport.increaseSpeed()

        assertEquals(
            listOf("previousPage", "nextPage", "increasePace"),
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
