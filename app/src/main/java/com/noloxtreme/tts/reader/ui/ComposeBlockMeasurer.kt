package com.noloxtreme.tts.reader.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.LineHeightPreference

/**
 * The [BlockMeasurer] used by the paged EPUB reader. It measures text with
 * the exact styles, indents and widths that [EpubReaderPane] renders with, so
 * the pagination lines up pixel-for-pixel with what is drawn. Images use the
 * caller-supplied fitted heights, or a placeholder while their bytes load.
 */
class ComposeBlockMeasurer internal constructor(
    private val textMeasurer: TextMeasurer,
    private val density: Density,
    private val pageWidthPx: Int,
    private val fontSizeSp: Int,
    private val lineHeight: LineHeightPreference,
    private val styles: EpubTypeStyles,
    private val imageHeightsPx: Map<String, Float>,
    private val placeholderImageHeightPx: Int
) : BlockMeasurer {

    private val quoteIndentPx = with(density) { (QuoteStartPadding + QuoteEndPadding).roundToPx() }
    private val listMarkerWidthPx = with(density) { ListMarkerWidth.roundToPx() }
    private val pageLabelVerticalPaddingPx = with(density) { PageLabelVerticalPadding.roundToPx() }
    private val dividerHeightPx =
        with(density) { (DividerVerticalPadding * 2 + DividerThickness).roundToPx() }

    override fun isTextBlock(block: EpubBlock): Boolean = when (block) {
        is EpubBlock.Heading,
        is EpubBlock.Paragraph,
        is EpubBlock.Quote,
        is EpubBlock.ListItem -> true
        is EpubBlock.Image,
        is EpubBlock.PageNumber,
        EpubBlock.Divider -> false
    }

    override fun linesOf(block: EpubBlock): List<MeasuredLine> {
        val textWidthPx = when (block) {
            is EpubBlock.Quote -> pageWidthPx - quoteIndentPx
            is EpubBlock.ListItem -> pageWidthPx - listMarkerWidthPx
            else -> pageWidthPx
        }.coerceAtLeast(1)
        val layout = textMeasurer.measure(
            text = block.slicedText(slice = null),
            style = epubBlockStyle(block, fontSizeSp, lineHeight, styles),
            overflow = TextOverflow.Clip,
            softWrap = true,
            constraints = Constraints(maxWidth = textWidthPx)
        )
        return (0 until layout.lineCount).map { index ->
            MeasuredLine(
                charStart = layout.getLineStart(index),
                charEndExclusive = layout.getLineEnd(index, visibleEnd = true),
                heightPx = lineHeightPx(layout, index)
            )
        }
    }

    override fun wholeHeightPx(block: EpubBlock): Float = when (block) {
        is EpubBlock.Image ->
            imageHeightsPx[block.resourcePath] ?: placeholderImageHeightPx.toFloat()
        is EpubBlock.PageNumber -> {
            val layout = textMeasurer.measure(
                text = AnnotatedString(block.label),
                style = styles.label,
                constraints = Constraints(maxWidth = pageWidthPx)
            )
            layout.size.height.toFloat() + pageLabelVerticalPaddingPx
        }
        EpubBlock.Divider -> dividerHeightPx.toFloat()
        else -> 0f
    }

    private fun lineHeightPx(layout: TextLayoutResult, index: Int): Float =
        layout.getLineBottom(index) - if (index == 0) 0f else layout.getLineBottom(index - 1)
}
