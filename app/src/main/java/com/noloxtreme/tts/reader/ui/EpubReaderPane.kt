package com.noloxtreme.tts.reader.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
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

/** Maps the shared line-spacing preference to a multiplier, reused by both reader panes. */
internal fun lineHeightMultiplier(preference: LineHeightPreference): Float = when (preference) {
    LineHeightPreference.COMPACT -> 1.35f
    LineHeightPreference.COMFORTABLE -> 1.55f
    LineHeightPreference.SPACIOUS -> 1.8f
}

/**
 * Visual read-only rendering of one EPUB spine item. Narration controls stay
 * available below this pane; only the middle content area is replaced.
 */
@Composable
fun EpubReaderPane(
    spineContent: EpubSpineContent?,
    unavailable: Boolean,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    onRetry: () -> Unit,
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
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = OratorDesignTokens.readerHorizontalPadding,
                    end = OratorDesignTokens.readerHorizontalPadding,
                    top = 12.dp,
                    bottom = 26.dp
                ),
                verticalArrangement = Arrangement.spacedBy(OratorDesignTokens.readerParagraphGap)
            ) {
                itemsIndexed(spineContent.blocks, key = { index, _ -> index }) { _, block ->
                    EpubBlockView(block, fontSizeSp, lineHeight, loadImageBytes)
                }
            }
        }
    }
}

@Composable
private fun EpubBlockView(
    block: EpubBlock,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    loadImageBytes: suspend (String) -> ByteArray?
) {
    val normalizedFontSize = fontSizeSp.coerceIn(14, 32)
    val lineSpacing = (normalizedFontSize * lineHeightMultiplier(lineHeight)).sp
    when (block) {
        is EpubBlock.Heading -> {
            val scale = headingScale(block.level)
            Text(
                text = block.runs.toAnnotatedString(defaultItalic = false),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = (normalizedFontSize * scale).sp,
                    lineHeight = (normalizedFontSize * scale * 1.25f).sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        is EpubBlock.Paragraph -> Text(
            text = block.runs.toAnnotatedString(defaultItalic = false),
            style = bodyStyle(normalizedFontSize, lineSpacing)
        )
        is EpubBlock.Quote -> Text(
            text = block.runs.toAnnotatedString(defaultItalic = true),
            style = bodyStyle(normalizedFontSize, lineSpacing),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp, end = 6.dp)
        )
        is EpubBlock.ListItem -> Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = block.marker,
                style = bodyStyle(normalizedFontSize, lineSpacing),
                modifier = Modifier.width(26.dp)
            )
            Text(
                text = block.runs.toAnnotatedString(defaultItalic = false),
                style = bodyStyle(normalizedFontSize, lineSpacing),
                modifier = Modifier.weight(1f)
            )
        }
        is EpubBlock.Image -> EpubImageView(
            resourcePath = block.resourcePath,
            contentDescription = block.contentDescription,
            loadImageBytes = loadImageBytes
        )
        EpubBlock.Divider -> HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun EpubImageView(
    resourcePath: String,
    contentDescription: String?,
    loadImageBytes: suspend (String) -> ByteArray?
) {
    val bitmap = produceState<ImageBitmap?>(initialValue = null, resourcePath) {
        val bytes = loadImageBytes(resourcePath) ?: return@produceState
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }.value
    if (bitmap == null) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp).heightIn(max = 240.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(Modifier.width(22.dp))
        }
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
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

@Composable
private fun bodyStyle(fontSizeSp: Int, lineHeightSp: TextUnit) =
    MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeightSp
    )

private fun headingScale(level: Int): Float = when (level) {
    1 -> 1.7f
    2 -> 1.5f
    3 -> 1.34f
    4 -> 1.2f
    5 -> 1.1f
    else -> 1.02f
}

private fun List<EpubRun>.toAnnotatedString(defaultItalic: Boolean): AnnotatedString =
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
