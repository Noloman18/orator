package com.noloxtreme.tts.reader.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.EpubRun
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM tests for the pure logic of the paged reader: gesture mapping, printed
 * page labels, and the styles shared between the measurer and the renderer.
 */
class EpubReaderLogicTest {

    // ------------------------------------------------------------------
    // PageTurnGestures
    // ------------------------------------------------------------------

    @Test
    fun tapZonesFollowKindleThirds() {
        assertEquals(TapZone.PREVIOUS, PageTurnGestures.tapZone(x = 0f, width = 300f))
        assertEquals(TapZone.PREVIOUS, PageTurnGestures.tapZone(x = 99.9f, width = 300f))
        assertEquals(TapZone.TOGGLE_CHROME, PageTurnGestures.tapZone(x = 100f, width = 300f))
        assertEquals(TapZone.TOGGLE_CHROME, PageTurnGestures.tapZone(x = 200f, width = 300f))
        assertEquals(TapZone.NEXT, PageTurnGestures.tapZone(x = 200.1f, width = 300f))
        assertEquals(TapZone.NEXT, PageTurnGestures.tapZone(x = 300f, width = 300f))
    }

    @Test
    fun tapZoneIsSafeForDegenerateWidths() {
        assertEquals(TapZone.TOGGLE_CHROME, PageTurnGestures.tapZone(x = 0f, width = 0f))
        assertEquals(TapZone.TOGGLE_CHROME, PageTurnGestures.tapZone(x = 10f, width = -1f))
    }

    @Test
    fun dragDirectionRequiresThresholdDistance() {
        val width = 300f
        assertEquals(PageTurnDirection.FORWARD, PageTurnGestures.dragDirection(-46f, width))
        assertEquals(PageTurnDirection.FORWARD, PageTurnGestures.dragDirection(-45f, width))
        assertNull(PageTurnGestures.dragDirection(-44f, width))
        assertNull(PageTurnGestures.dragDirection(0f, width))
        assertNull(PageTurnGestures.dragDirection(44f, width))
        assertEquals(PageTurnDirection.BACKWARD, PageTurnGestures.dragDirection(45f, width))
        assertEquals(PageTurnDirection.BACKWARD, PageTurnGestures.dragDirection(300f, width))
        assertNull(PageTurnGestures.dragDirection(10f, 0f))
    }

    // ------------------------------------------------------------------
    // nearestPrintedPageLabel
    // ------------------------------------------------------------------

    private fun pageNumber(label: String) = EpubBlock.PageNumber(label)
    private fun paragraph(text: String) = EpubBlock.Paragraph(listOf(EpubRun(text)))

    @Test
    fun printedLabelOnThePageItselfWins() {
        val blocks = listOf(paragraph("text"), pageNumber("12"))
        val page = Page(listOf(PageItem.TextSlice(0, 0, 5), PageItem.Whole(1)))

        assertEquals("12", nearestPrintedPageLabel(page, blocks))
    }

    @Test
    fun printedLabelBeforeThePageStartIsUsed() {
        val blocks = listOf(paragraph("text"), pageNumber("12"), paragraph("more"))
        val page = Page(listOf(PageItem.TextSlice(2, 0, 4)))

        assertEquals("12", nearestPrintedPageLabel(page, blocks))
    }

    @Test
    fun noPrintedLabelsYieldsNull() {
        val blocks = listOf(paragraph("text"), paragraph("more"))
        val page = Page(listOf(PageItem.TextSlice(0, 0, 5)))

        assertNull(nearestPrintedPageLabel(page, blocks))
    }

    @Test
    fun emptyPageYieldsNull() {
        assertNull(nearestPrintedPageLabel(Page(emptyList()), listOf(pageNumber("1"))))
    }

    // ------------------------------------------------------------------
    // Shared styles: epubBlockStyle
    // ------------------------------------------------------------------

    private val styles = EpubTypeStyles(
        body = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
        heading = androidx.compose.ui.text.TextStyle(fontSize = 24.sp),
        label = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
    )

    @Test
    fun paragraphStyleScalesFontAndLineHeight() {
        val style = epubBlockStyle(paragraph("x"), fontSizeSp = 20, lineHeight = LineHeightPreference.COMFORTABLE, styles)

        assertEquals(20.sp, style.fontSize)
        assertEquals((20f * 1.55f).sp, style.lineHeight)
    }

    @Test
    fun paragraphStyleClampsFontSizeToReaderRange() {
        val style = epubBlockStyle(paragraph("x"), fontSizeSp = 60, lineHeight = LineHeightPreference.COMPACT, styles)

        assertEquals(32.sp, style.fontSize)
    }

    @Test
    fun headingStyleScalesByLevelAndIsBold() {
        val h1 = epubBlockStyle(EpubBlock.Heading(1, listOf(EpubRun("t"))), 20, LineHeightPreference.COMPACT, styles)
        val h4 = epubBlockStyle(EpubBlock.Heading(4, listOf(EpubRun("t"))), 20, LineHeightPreference.COMPACT, styles)

        assertEquals((20f * 1.7f).sp, h1.fontSize)
        assertEquals((20f * 1.2f).sp, h4.fontSize)
        assertEquals(FontWeight.Bold, h1.fontWeight)
    }

    // ------------------------------------------------------------------
    // Shared run rendering: toAnnotatedString
    // ------------------------------------------------------------------

    @Test
    fun runsRenderPlainTextWithoutSpans() {
        val annotated = listOf(EpubRun("plain")).toAnnotatedString(defaultItalic = false)

        assertEquals("plain", annotated.text)
        assertEquals(0, annotated.spanStyles.size)
    }

    @Test
    fun runsApplyBoldAndItalicSpans() {
        val annotated = listOf(EpubRun("b", bold = true), EpubRun("i", italic = true), EpubRun("n"))
            .toAnnotatedString(defaultItalic = false)

        assertEquals("bin", annotated.text)
        assertEquals(2, annotated.spanStyles.size)
        assertEquals(FontWeight.SemiBold, annotated.spanStyles[0].item.fontWeight)
        assertEquals(FontStyle.Italic, annotated.spanStyles[1].item.fontStyle)
    }

    @Test
    fun defaultItalicAppliesToAllRuns() {
        val annotated = listOf(EpubRun("a"), EpubRun("b")).toAnnotatedString(defaultItalic = true)

        assertEquals(2, annotated.spanStyles.size)
        annotated.spanStyles.forEach { span ->
            assertEquals(FontStyle.Italic, span.item.fontStyle)
        }
    }
}
