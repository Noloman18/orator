package com.noloxtreme.tts.reader.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import kotlin.math.roundToInt

/** Page geometry constants shared by the paged reader and its text measurer. */
internal val PageTopPadding = 12.dp
internal val PageBottomPadding = 16.dp
internal val QuoteStartPadding = 14.dp
internal val QuoteEndPadding = 6.dp
internal val ListMarkerWidth = 26.dp
internal val PageLabelVerticalPadding = 8.dp
internal val DividerThickness = 1.dp
internal val DividerVerticalPadding = 10.dp

private val ImagePlaceholderHeight = 150.dp
private const val IMAGE_MAX_PAGE_FRACTION = 0.7f

/** Maps the shared line-spacing preference to a multiplier, reused by both reader panes. */
internal fun lineHeightMultiplier(preference: LineHeightPreference): Float = when (preference) {
    LineHeightPreference.COMPACT -> 1.35f
    LineHeightPreference.COMFORTABLE -> 1.55f
    LineHeightPreference.SPACIOUS -> 1.8f
}

/** The Material text styles shared by the paged reader and its measurer. */
internal data class EpubTypeStyles(
    val body: TextStyle,
    val heading: TextStyle,
    val label: TextStyle
)

/** The rendered style of a block; the pagination measurer applies the same styles. */
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

/** The styled text of a text block, sliced to a page fragment when provided. */
internal fun EpubBlock.slicedText(slice: PageItem.TextSlice?): AnnotatedString {
    val full = runs.toAnnotatedString(defaultItalic = this is EpubBlock.Quote)
    return if (slice == null) {
        full
    } else {
        full.subSequence(slice.charStart, slice.charEndExclusive)
    }
}

/**
 * Kindle-style visual rendering of one EPUB spine item: the chapter's blocks
 * are measured and packed into fixed pages that fill the viewport, and the
 * reader flips between them (tap the left/right edges, drag horizontally, or
 * use the transport's fast buttons). The page index is owned by the caller:
 * this pane reports page-turn requests and the measured page count through
 * [onNextPage], [onPreviousPage] and [onPageCountChange]. Narration controls
 * stay below this pane; only the middle content area is replaced.
 */
@Composable
fun EpubReaderPane(
    spineContent: EpubSpineContent?,
    unavailable: Boolean,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    chromeVisible: Boolean,
    pageIndex: Int,
    jumpTarget: PageTextAnchor?,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onPageCountChange: (Int) -> Unit,
    onPageAnchorChanged: (PageTextAnchor?) -> Unit,
    onJumpTargetResolved: (Int) -> Unit,
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
            else -> PagedChapterView(
                blocks = spineContent.blocks,
                fontSizeSp = fontSizeSp,
                lineHeight = lineHeight,
                chromeVisible = chromeVisible,
                pageIndex = pageIndex,
                jumpTarget = jumpTarget,
                onNextPage = onNextPage,
                onPreviousPage = onPreviousPage,
                onPageCountChange = onPageCountChange,
                onPageAnchorChanged = onPageAnchorChanged,
                onJumpTargetResolved = onJumpTargetResolved,
                onToggleChrome = onToggleChrome,
                loadImageBytes = loadImageBytes
            )
        }
    }
}

@Composable
private fun PagedChapterView(
    blocks: List<EpubBlock>,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    chromeVisible: Boolean,
    pageIndex: Int,
    jumpTarget: PageTextAnchor?,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onPageCountChange: (Int) -> Unit,
    onPageAnchorChanged: (PageTextAnchor?) -> Unit,
    onJumpTargetResolved: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    loadImageBytes: suspend (String) -> ByteArray?
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pageWidthPx = with(density) {
            (maxWidth - OratorDesignTokens.readerHorizontalPadding * 2).roundToPx()
        }
        val pageHeightPx = with(density) {
            (maxHeight - PageTopPadding - PageBottomPadding).roundToPx()
        }
        val textMeasurer = rememberTextMeasurer(cacheSize = 64)
        val styles = EpubTypeStyles(
            body = MaterialTheme.typography.bodyLarge,
            heading = MaterialTheme.typography.headlineSmall,
            label = MaterialTheme.typography.labelMedium
        )
        val placeholderHeightPx = with(density) {
            minOf(
                ImagePlaceholderHeight.roundToPx(),
                (pageHeightPx * IMAGE_MAX_PAGE_FRACTION).roundToInt()
            )
        }
        val imageHeights by rememberFittedImageHeights(blocks, pageWidthPx, pageHeightPx, loadImageBytes)
        val measurer = remember(
            textMeasurer, density, pageWidthPx, fontSizeSp, lineHeight, imageHeights, styles,
            placeholderHeightPx
        ) {
            ComposeBlockMeasurer(
                textMeasurer = textMeasurer,
                density = density,
                pageWidthPx = pageWidthPx,
                fontSizeSp = fontSizeSp,
                lineHeight = lineHeight,
                styles = styles,
                imageHeightsPx = imageHeights,
                placeholderImageHeightPx = placeholderHeightPx
            )
        }
        val gapPx = with(density) {
            OratorDesignTokens.readerParagraphGap.roundToPx().toFloat()
        }
        val pages = remember(blocks, pageWidthPx, pageHeightPx, fontSizeSp, lineHeight, imageHeights, styles) {
            EpubPagination.paginate(blocks, pageHeightPx.toFloat(), gapPx, measurer)
        }
        LaunchedEffect(pages.size) {
            onPageCountChange(pages.size)
        }
        val lastPageIndex = (pages.size - 1).coerceAtLeast(0)
        val safePageIndex = pageIndex.coerceIn(0, lastPageIndex)
        // A pending narration jump lands directly on its page for the first
        // frame, then reports the resolved index so the caller owns it.
        val jumpPage = jumpTarget?.let { target ->
            pageContaining(pages, target.blockIndex, target.charStart).takeIf { it >= 0 }
        }
        val effectivePageIndex = jumpPage ?: safePageIndex
        LaunchedEffect(jumpPage) {
            if (jumpPage != null) onJumpTargetResolved(jumpPage)
        }
        val anchor = pages.getOrNull(effectivePageIndex)?.let(::pageTextAnchor)
        LaunchedEffect(anchor) {
            onPageAnchorChanged(anchor)
        }
        if (pages.isEmpty()) return@BoxWithConstraints

        // Gesture closures must always see the latest callbacks even though
        // the gesture detectors attach only once.
        val nextPage by rememberUpdatedState(onNextPage)
        val previousPage by rememberUpdatedState(onPreviousPage)
        val toggleChrome by rememberUpdatedState(onToggleChrome)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        when (PageTurnGestures.tapZone(offset.x, size.width.toFloat())) {
                            TapZone.PREVIOUS -> previousPage()
                            TapZone.NEXT -> nextPage()
                            TapZone.TOGGLE_CHROME -> toggleChrome()
                        }
                    }
                }
                .pointerInput(Unit) {
                    var dragDistance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragDistance = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragDistance += dragAmount
                        },
                        onDragEnd = {
                            when (PageTurnGestures.dragDirection(dragDistance, size.width.toFloat())) {
                                PageTurnDirection.FORWARD -> nextPage()
                                PageTurnDirection.BACKWARD -> previousPage()
                                null -> Unit
                            }
                        }
                    )
                }
        ) {
            AnimatedContent(
                targetState = effectivePageIndex,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState > initialState
                    if (forward) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "epubPageTurn"
            ) { index ->
                PagedPageContent(
                    blocks = blocks,
                    page = pages[index],
                    fontSizeSp = fontSizeSp,
                    lineHeight = lineHeight,
                    styles = styles,
                    gap = OratorDesignTokens.readerParagraphGap,
                    placeholderHeightPx = placeholderHeightPx,
                    imageHeights = imageHeights,
                    loadImageBytes = loadImageBytes,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = OratorDesignTokens.readerHorizontalPadding,
                            end = OratorDesignTokens.readerHorizontalPadding,
                            top = PageTopPadding,
                            bottom = PageBottomPadding
                        )
                )
            }
            if (chromeVisible) {
                val label = remember(pages, effectivePageIndex) {
                    nearestPrintedPageLabel(pages[effectivePageIndex], blocks)
                }
                Text(
                    text = label?.let {
                        stringResource(
                            R.string.read_mode_page_position_with_label,
                            it,
                            effectivePageIndex + 1,
                            pages.size
                        )
                    } ?: stringResource(R.string.read_mode_page_position, effectivePageIndex + 1, pages.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )
            }
        }
    }
}

/** Renders one page: the block fragments it was packed with, in order. */
@Composable
private fun PagedPageContent(
    blocks: List<EpubBlock>,
    page: Page,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    styles: EpubTypeStyles,
    gap: Dp,
    placeholderHeightPx: Int,
    imageHeights: Map<String, Float>,
    loadImageBytes: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        page.items.forEach { item ->
            PageItemView(
                block = blocks[item.blockIndex],
                slice = item as? PageItem.TextSlice,
                fontSizeSp = fontSizeSp,
                lineHeight = lineHeight,
                styles = styles,
                placeholderHeightPx = placeholderHeightPx,
                imageHeights = imageHeights,
                loadImageBytes = loadImageBytes
            )
        }
    }
}

@Composable
private fun PageItemView(
    block: EpubBlock,
    slice: PageItem.TextSlice?,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    styles: EpubTypeStyles,
    placeholderHeightPx: Int,
    imageHeights: Map<String, Float>,
    loadImageBytes: suspend (String) -> ByteArray?
) {
    when (block) {
        is EpubBlock.Heading -> Text(
            text = block.slicedText(slice),
            style = epubBlockStyle(block, fontSizeSp, lineHeight, styles),
            color = MaterialTheme.colorScheme.onSurface
        )
        is EpubBlock.Paragraph -> Text(
            text = block.slicedText(slice),
            style = epubBlockStyle(block, fontSizeSp, lineHeight, styles)
        )
        is EpubBlock.Quote -> Text(
            text = block.slicedText(slice),
            style = epubBlockStyle(block, fontSizeSp, lineHeight, styles),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = QuoteStartPadding, end = QuoteEndPadding)
        )
        is EpubBlock.ListItem -> {
            val startsAtBlockStart = slice == null || slice.charStart == 0
            if (startsAtBlockStart) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = block.marker,
                        style = epubBlockStyle(block, fontSizeSp, lineHeight, styles),
                        modifier = Modifier.width(ListMarkerWidth)
                    )
                    Text(
                        text = block.slicedText(slice),
                        style = epubBlockStyle(block, fontSizeSp, lineHeight, styles),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = block.slicedText(slice),
                    style = epubBlockStyle(block, fontSizeSp, lineHeight, styles),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ListMarkerWidth)
                )
            }
        }
        is EpubBlock.Image -> PageImage(
            resourcePath = block.resourcePath,
            contentDescription = block.contentDescription,
            heightPx = imageHeights[block.resourcePath] ?: placeholderHeightPx.toFloat(),
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

/**
 * Decodes the dimensions of every chapter image once and returns its fitted
 * display height (aspect-scaled to the page width, capped to the page height)
 * so the paginator can reserve exact space. Missing entries fall back to the
 * placeholder height, keeping pagination stable while bytes load.
 */
@Composable
private fun rememberFittedImageHeights(
    blocks: List<EpubBlock>,
    pageWidthPx: Int,
    pageHeightPx: Int,
    loadImageBytes: suspend (String) -> ByteArray?
): State<Map<String, Float>> {
    val paths = remember(blocks) {
        blocks.mapNotNull { (it as? EpubBlock.Image)?.resourcePath }.distinct()
    }
    val heights = remember(blocks) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    LaunchedEffect(paths, pageWidthPx, pageHeightPx) {
        val fitted = mutableMapOf<String, Float>()
        for (path in paths) {
            val bytes = loadImageBytes(path) ?: continue
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) continue
            val scaledHeight = options.outHeight.toFloat() * pageWidthPx / options.outWidth.toFloat()
            fitted[path] = scaledHeight.coerceAtMost(pageHeightPx * IMAGE_MAX_PAGE_FRACTION)
        }
        heights.value = fitted
    }
    return heights
}

@Composable
private fun PageImage(
    resourcePath: String,
    contentDescription: String?,
    heightPx: Float,
    loadImageBytes: suspend (String) -> ByteArray?
) {
    val bitmap = produceState<ImageBitmap?>(initialValue = null, resourcePath) {
        val bytes = loadImageBytes(resourcePath) ?: return@produceState
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }.value
    val height = with(LocalDensity.current) { heightPx.toDp() }
    Box(
        modifier = Modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            CircularProgressIndicator(Modifier.width(22.dp))
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
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
