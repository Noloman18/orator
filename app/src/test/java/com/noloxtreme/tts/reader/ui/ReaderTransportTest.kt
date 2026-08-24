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
    fun chapterTransportMapsButtonsToChapterNavigation() {
        val calls = mutableListOf<String>()
        val transport = ChapterTransport(
            previousChapter = { calls += "previousChapter" },
            nextChapter = { calls += "nextChapter" }
        )

        transport.rewind()
        transport.previous()
        transport.next()
        transport.fastForward()

        assertEquals(
            listOf("previousChapter", "previousChapter", "nextChapter", "nextChapter"),
            calls
        )
    }
}
