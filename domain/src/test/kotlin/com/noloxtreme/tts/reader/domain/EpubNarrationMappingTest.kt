package com.noloxtreme.tts.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubNarrationMappingTest {

    @Test
    fun narrationTextConcatenatesRunsWithoutSeparator() {
        val paragraph = EpubBlock.Paragraph(
            listOf(EpubRun("Hello "), EpubRun("bold", bold = true), EpubRun(" world"))
        )

        assertEquals("Hello bold world", paragraph.narrationText)
    }

    @Test
    fun narrationTextIsEmptyForNonTextBlocks() {
        assertEquals("", EpubBlock.Image("img.png", null).narrationText)
        assertEquals("", EpubBlock.PageNumber("12").narrationText)
        assertEquals("", EpubBlock.Divider.narrationText)
    }

    @Test
    fun narratableBlocksBeforeCountsOnlyTextBlocks() {
        val blocks = listOf<EpubBlock>(
            EpubBlock.Heading(1, listOf(EpubRun("Chapter"))),
            EpubBlock.Image("img.png", null),
            EpubBlock.Paragraph(listOf(EpubRun("Text"))),
            EpubBlock.Divider,
            EpubBlock.Quote(listOf(EpubRun("Quote")))
        )

        assertEquals(0, narratableBlocksBefore(blocks, 0))
        assertEquals(1, narratableBlocksBefore(blocks, 2))
        assertEquals(2, narratableBlocksBefore(blocks, 4))
        assertEquals(3, narratableBlocksBefore(blocks, 5))
    }

    @Test
    fun narrationOffsetInParagraphAccountsForTrimmedLeadingWhitespace() {
        assertEquals(0, narrationOffsetInParagraph("plain", 0))
        assertEquals(4, narrationOffsetInParagraph("plain", 4))
        assertEquals(0, narrationOffsetInParagraph(" hello", 0))
        assertEquals(0, narrationOffsetInParagraph(" hello", 1))
        assertEquals(3, narrationOffsetInParagraph(" hello", 4))
    }

    @Test
    fun narrationOffsetInBlockIsTheInverseOfTheParagraphMapping() {
        assertEquals(0, narrationOffsetInBlock("plain", 0))
        assertEquals(4, narrationOffsetInBlock("plain", 4))
        assertEquals(1, narrationOffsetInBlock(" hello", 0))
        assertEquals(5, narrationOffsetInBlock(" hello", 4))
    }

    @Test
    fun blockAtNarratableRankSkipsNonTextBlocks() {
        val blocks = listOf<EpubBlock>(
            EpubBlock.Heading(1, listOf(EpubRun("Chapter"))),
            EpubBlock.Image("img.png", null),
            EpubBlock.Paragraph(listOf(EpubRun("Text"))),
            EpubBlock.Divider,
            EpubBlock.Quote(listOf(EpubRun("Quote")))
        )

        assertEquals(0, blockAtNarratableRank(blocks, 0))
        assertEquals(2, blockAtNarratableRank(blocks, 1))
        assertEquals(4, blockAtNarratableRank(blocks, 2))
    }

    @Test
    fun blockAtNarratableRankReturnsNullPastTheLastBlock() {
        val blocks = listOf<EpubBlock>(EpubBlock.Paragraph(listOf(EpubRun("Text"))))

        assertEquals(null, blockAtNarratableRank(blocks, 1))
        assertEquals(null, blockAtNarratableRank(blocks, -1))
        assertEquals(null, blockAtNarratableRank(emptyList(), 0))
    }
}
