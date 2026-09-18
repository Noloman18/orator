package com.noloxtreme.tts.reader.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noloxtreme.tts.reader.R
import com.noloxtreme.tts.reader.designsystem.OratorDesignTokens
import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.EpubRun
import com.noloxtreme.tts.reader.domain.EpubSpineContent
import com.noloxtreme.tts.reader.domain.EpubTocEntry
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged

/** Reader geometry shared by the EPUB renderer and its text styles. */
internal val PageTopPadding = 12.dp
internal val PageBottomPadding = 16.dp
internal val QuoteStartPadding = 14.dp
internal val QuoteEndPadding = 6.dp
internal val ListMarkerWidth = 26.dp
internal val PageLabelVerticalPadding = 8.dp
internal val DividerThickness = 1.dp
internal val DividerVerticalPadding = 10.dp

private val ImagePlaceholderHeight = 150.dp
/** Maps the shared line-spacing preference to a multiplier, reused by both reader panes. */
internal fun lineHeightMultiplier(preference: LineHeightPreference): Float = when (preference) {
    LineHeightPreference.COMPACT -> 1.35f
    LineHeightPreference.COMFORTABLE -> 1.55f
    LineHeightPreference.SPACIOUS -> 1.8f
}

/** The Material text styles shared by EPUB blocks. */
internal data class EpubTypeStyles(
    val body: TextStyle,
    val heading: TextStyle,
    val label: TextStyle
)

/** The rendered style of an EPUB block. */
internal fun epubBlockStyle(
    block: EpubBlock,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    styles: EpubTypeStyles
): TextStyle {
    val normalized = fontSizeSp.coerceIn(14, 32)
    return when (block) {
        is EpubBlock.Heading -> {
            val scale = headingScale(block.level)
            styles.heading.copy(
                fontSize = (normalized * scale).sp,
                lineHeight = (normalized * scale * 1.25f).sp,
                fontWeight = FontWeight.Bold
            )
        }
        is EpubBlock.Paragraph, is EpubBlock.Quote, is EpubBlock.ListItem ->
            styles.body.copy(
                fontSize = normalized.sp,
                lineHeight = (normalized * lineHeightMultiplier(lineHeight)).sp
            )
        else -> styles.body
    }
}

private val EpubBlock.runs: List<EpubRun>
    get() = when (this) {
        is EpubBlock.Heading -> runs
        is EpubBlock.Paragraph -> runs
        is EpubBlock.Quote -> runs
        is EpubBlock.ListItem -> runs
        else -> emptyList()
    }

/** The styled text of a text block, optionally highlighting the active read unit. */
internal fun EpubBlock.slicedText(
    slice: PageItem.TextSlice?,
    activeWord: PageTextRange? = null
): AnnotatedString {
    val full = runs.toAnnotatedString(defaultItalic = this is EpubBlock.Quote)
    val sliceStart = slice?.charStart ?: 0
    val sliceEnd = slice?.charEndExclusive ?: full.length
    val visible = full.subSequence(sliceStart, sliceEnd)
    if (activeWord == null) return visible
    val start = maxOf(activeWord.charStart, sliceStart)
    val end = minOf(activeWord.charEndExclusive, sliceEnd)
    if (end <= start) return visible
    return buildAnnotatedString {
        append(visible)
        addStyle(
            SpanStyle(
                background = OratorDesignTokens.warmHighlight,
                color = OratorDesignTokens.ink,
                fontWeight = FontWeight.SemiBold
            ),
            start - sliceStart,
            end - sliceStart
        )
    }
}

/**
 * Scrollable visual rendering of one EPUB spine item. It uses the same
 * continuous vertical reading interaction as narration mode while preserving
 * EPUB inline styling, lists, page labels, and images. A narration/read-mode
 * position is mapped to a block so the list can bring it into view.
 */
@Composable
fun EpubReaderPane(
    spineContent: EpubSpineContent?,
    unavailable: Boolean,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    jumpTarget: PageTextAnchor?,
    activeWord: PageTextRange? = null,
    onJumpTargetResolved: () -> Unit,
    onVisibleBlockChanged: (Int) -> Unit,
    onRetry: () -> Unit,
    onToggleChrome: () -> Unit,
    loadImageBytes: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            spineContent == null && unavailable -> MissingChapterContent(onRetry = onRetry)
            spineContent == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            else -> ScrollableChapterView(
                spineIndex = spineContent.spineIndex,
                blocks = spineContent.blocks,
                fontSizeSp = fontSizeSp,
                lineHeight = lineHeight,
                jumpTarget = jumpTarget,
                activeWord = activeWord,
                onJumpTargetResolved = onJumpTargetResolved,
                onVisibleBlockChanged = onVisibleBlockChanged,
                onToggleChrome = onToggleChrome,
                loadImageBytes = loadImageBytes
            )
        }
    }
}

@Composable
private fun ScrollableChapterView(
    spineIndex: Int,
    blocks: List<EpubBlock>,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    jumpTarget: PageTextAnchor?,
    activeWord: PageTextRange?,
    onJumpTargetResolved: () -> Unit,
    onVisibleBlockChanged: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    loadImageBytes: suspend (String) -> ByteArray?
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val contentWidthPx = with(density) {
            (maxWidth - OratorDesignTokens.readerHorizontalPadding * 2).roundToPx()
        }
        val styles = EpubTypeStyles(
            body = MaterialTheme.typography.bodyLarge,
            heading = MaterialTheme.typography.headlineSmall,
            label = MaterialTheme.typography.labelMedium
        )
        val listState = rememberLazyListState()

        LaunchedEffect(spineIndex, jumpTarget) {
            val target = jumpTarget ?: return@LaunchedEffect
            if (blocks.isNotEmpty()) {
                listState.scrollToItem(target.blockIndex.coerceIn(0, blocks.lastIndex))
            }
            onJumpTargetResolved()
        }
        LaunchedEffect(spineIndex, activeWord?.blockIndex) {
            val blockIndex = activeWord?.blockIndex ?: return@LaunchedEffect
            if (blocks.isNotEmpty()) {
                listState.animateScrollToItem(blockIndex.coerceIn(0, blocks.lastIndex))
            }
        }
        LaunchedEffect(listState, spineIndex) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect(onVisibleBlockChanged)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleChrome() })
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = OratorDesignTokens.readerHorizontalPadding,
                    end = OratorDesignTokens.readerHorizontalPadding,
                    top = PageTopPadding,
                    bottom = PageBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(OratorDesignTokens.readerParagraphGap)
            ) {
                itemsIndexed(blocks, key = { index, _ -> "epub-$spineIndex-$index" }) { index, block ->
                    EpubBlockView(
                        block = block,
                        activeWord = activeWord?.takeIf { it.blockIndex == index },
                        fontSizeSp = fontSizeSp,
                        lineHeight = lineHeight,
                        styles = styles,
                        imageWidthPx = contentWidthPx,
                        loadImageBytes = loadImageBytes
                    )
                }
            }
        }
    }
}

@Composable
private fun EpubBlockView(
    block: EpubBlock,
    activeWord: PageTextRange?,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    styles: EpubTypeStyles,
    imageWidthPx: Int,
    loadImageBytes: suspend (String) -> ByteArray?
) {
    when (block) {
        is EpubBlock.Heading -> Text(
            text = block.slicedText(slice = null, activeWord = activeWord),
            style = epubBlockStyle(block, fontSizeSp, lineHeight, styles),
            color = MaterialTheme.colorScheme.onSurface
        )
        is EpubBlock.Paragraph -> Text(
            text = block.slicedText(slice = null, activeWord = activeWord),
            style = epubBlockStyle(block, fontSizeSp, lineHeight, styles)
        )
        is EpubBlock.Quote -> Text(
            text = block.slicedText(slice = null, activeWord = activeWord),
            style = epubBlockStyle(block, fontSizeSp, lineHeight, styles),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = QuoteStartPadding, end = QuoteEndPadding)
        )
        is EpubBlock.ListItem -> {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = block.marker,
                    style = epubBlockStyle(block, fontSizeSp, lineHeight, styles),
                    modifier = Modifier.width(ListMarkerWidth)
                )
                Text(
                    text = block.slicedText(slice = null, activeWord = activeWord),
                    style = epubBlockStyle(block, fontSizeSp, lineHeight, styles),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        is EpubBlock.Image -> PageImage(
            resourcePath = block.resourcePath,
            contentDescription = block.contentDescription,
            widthPx = imageWidthPx,
            loadImageBytes = loadImageBytes
        )
        is EpubBlock.PageNumber -> Text(
            text = stringResource(R.string.ebook_page_number, block.label),
            style = styles.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            textAlign = TextAlign.Center
        )
        EpubBlock.Divider -> HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(vertical = DividerVerticalPadding),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun PageImage(
    resourcePath: String,
    contentDescription: String?,
    widthPx: Int,
    loadImageBytes: suspend (String) -> ByteArray?
) {
    val bitmap = produceState<ImageBitmap?>(
        initialValue = null,
        resourcePath,
        widthPx
    ) {
        val bytes = loadImageBytes(resourcePath) ?: return@produceState
        value = withContext(Dispatchers.IO) {
            decodeEpubImage(bytes, widthPx, targetHeight = 1)
        }
    }.value
    if (bitmap == null) {
        Box(
            modifier = Modifier.fillMaxWidth().height(ImagePlaceholderHeight),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(Modifier.width(22.dp))
        }
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
        )
    }
}

/**
 * Android bitmap sampling uses powers of two. Decode only enough pixels for
 * the fitted reader box rather than allocating the source cover at full size.
 */
internal fun epubImageSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0) return 1
    val requestedWidth = targetWidth.coerceAtLeast(1)
    val requestedHeight = targetHeight.coerceAtLeast(1)
    var sample = 1
    while (
        sourceWidth / (sample * 2) >= requestedWidth &&
        sourceHeight / (sample * 2) >= requestedHeight
    ) {
        sample *= 2
    }
    return sample
}

private fun decodeEpubImage(bytes: ByteArray, targetWidth: Int, targetHeight: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = epubImageSampleSize(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

@Composable
private fun MissingChapterContent(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(OratorDesignTokens.readerHorizontalPadding)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                stringResource(R.string.epub_chapter_unavailable_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.epub_chapter_unavailable_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

/** Clickable table of contents shown as a modal bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubTocSheet(
    entries: List<EpubTocEntry>,
    currentSpineIndex: Int,
    onSelect: (EpubTocEntry) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.table_of_contents),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(entries, key = { index, _ -> index }) { _, entry ->
                Text(
                    text = entry.title,
                    style = if (entry.spineIndex == currentSpineIndex) {
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                    },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(entry) }
                        .padding(
                            start = 24.dp + 18.dp * entry.depth,
                            end = 24.dp,
                            top = 12.dp,
                            bottom = 12.dp
                        )
                )
            }
        }
    }
}

private fun headingScale(level: Int): Float = when (level) {
    1 -> 1.7f
    2 -> 1.5f
    3 -> 1.34f
    4 -> 1.2f
    5 -> 1.1f
    else -> 1.02f
}

internal fun List<EpubRun>.toAnnotatedString(defaultItalic: Boolean): AnnotatedString =
    buildAnnotatedString {
        for (run in this@toAnnotatedString) {
            if (!run.bold && !(run.italic || defaultItalic)) {
                append(run.text)
                continue
            }
            pushStyle(
                SpanStyle(
                    fontWeight = if (run.bold) FontWeight.SemiBold else null,
                    fontStyle = if (run.italic || defaultItalic) FontStyle.Italic else null
                )
            )
            append(run.text)
            pop()
        }
    }
