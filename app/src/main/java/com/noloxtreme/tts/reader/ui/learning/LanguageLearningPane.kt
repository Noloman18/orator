package com.noloxtreme.tts.reader.ui.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noloxtreme.tts.reader.R
import com.noloxtreme.tts.reader.designsystem.OratorDesignTokens
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import com.noloxtreme.tts.reader.domain.SpokenRange
import com.noloxtreme.tts.reader.ui.lineHeightMultiplier
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.min

@Composable
internal fun compactLearningChrome(): Boolean {
    val window = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { window.width.toDp() >= 600.dp && window.height.toDp() < 480.dp }
}

/** A focused passage pair. Reading ahead never seeks the audio or changes saved progress. */
@Composable
internal fun LanguageLearningPane(
    state: LearningPassageState,
    position: DocumentPosition,
    spokenRange: SpokenRange?,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    followRevision: Int,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    progressControls: (@Composable () -> Unit)? = null
) {
    var showInformation by rememberSaveable { mutableStateOf(false) }
    var following by remember { mutableStateOf(true) }
    var scrollRevision by remember { mutableIntStateOf(0) }
    var heldPassage by remember { mutableStateOf<LearningPassage?>(null) }
    var recentPassage by remember { mutableStateOf<LearningPassage?>(null) }
    val latest = (state as? LearningPassageState.Ready)?.passage
    // During a deliberate scroll, keep that passage on screen even if audio moves on.
    // Otherwise render the new pair immediately, without waiting for a composition effect.
    val displayed = if (following) latest ?: recentPassage else heldPassage ?: latest ?: recentPassage
    LaunchedEffect(latest) { if (latest != null) recentPassage = latest }
    LaunchedEffect(followRevision) {
        following = true
        scrollRevision++
    }
    val onManualScroll = {
        if (following) heldPassage = displayed
        following = false
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.learning_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (progressControls != null) {
                Box(Modifier.weight(1.3f)) { progressControls() }
            }
            IconButton(onClick = { showInformation = true }) {
                Icon(Icons.Outlined.Info, stringResource(R.string.learning_information), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, stringResource(R.string.learning_close), modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val failed = state == LearningPassageState.OriginalMissing ||
                state == LearningPassageState.PassageMissing || state == LearningPassageState.Failed
            if (failed) {
                LearningUnavailable(state, onClose, onRetry)
            } else if (displayed == null) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.learning_loading), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val alignment = remember(displayed) {
                    LearningAlignment(
                        displayed.original.text, displayed.translated.text,
                        displayed.originalLanguage, displayed.translatedLanguage
                    )
                }
                val active = position.paragraphIndex == displayed.translated.paragraphIndex
                val range = spokenRange?.takeIf { it.paragraphIndex == displayed.translated.paragraphIndex }
                val offset = range?.startInParagraph ?: position.offsetInParagraph
                val originalRange = if (active) alignment.originalRange(offset) else null
                val translationRange = if (active) alignment.translatedRange(offset, range) else null
                BoxWithConstraints(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    val horizontal = maxWidth >= 568.dp
                    val original: @Composable (Modifier) -> Unit = { paneModifier ->
                        key(displayed.original.documentId, displayed.original.paragraphIndex) {
                            LearningTextPane(
                                text = displayed.original.text,
                                languageTag = displayed.originalLanguage,
                                original = true,
                                activeRange = originalRange,
                                fontSizeSp = fontSizeSp,
                                lineHeight = lineHeight,
                                following = following,
                                scrollRevision = scrollRevision,
                                onManualScroll = onManualScroll,
                                modifier = paneModifier
                            )
                        }
                    }
                    val translated: @Composable (Modifier) -> Unit = { paneModifier ->
                        key(displayed.translated.documentId, displayed.translated.paragraphIndex) {
                            LearningTextPane(
                                text = displayed.translated.text,
                                languageTag = displayed.translatedLanguage,
                                original = false,
                                activeRange = translationRange,
                                fontSizeSp = fontSizeSp,
                                lineHeight = lineHeight,
                                following = following,
                                scrollRevision = scrollRevision,
                                onManualScroll = onManualScroll,
                                modifier = paneModifier
                            )
                        }
                    }
                    if (horizontal) {
                        Row(Modifier.fillMaxSize().testTag("learning_horizontal"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            original(Modifier.weight(1f).fillMaxSize())
                            translated(Modifier.weight(1f).fillMaxSize())
                        }
                    } else {
                        Column(Modifier.fillMaxSize().testTag("learning_vertical"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            original(Modifier.weight(1f).fillMaxWidth())
                            translated(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
                if (!following) {
                    AssistChip(
                        onClick = {
                            following = true
                            heldPassage = null
                            scrollRevision++
                        },
                        label = { Text(stringResource(R.string.learning_follow)) },
                        leadingIcon = { Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
    if (showInformation) {
        AlertDialog(
            onDismissRequest = { showInformation = false },
            title = { Text(stringResource(R.string.learning_title)) },
            text = { Text(stringResource(R.string.learning_information_body)) },
            confirmButton = {
                TextButton(onClick = { showInformation = false }) { Text(stringResource(android.R.string.ok)) }
            }
        )
    }
}

@Composable
private fun LearningTextPane(
    text: String,
    languageTag: String?,
    original: Boolean,
    activeRange: LearningTextRange?,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference,
    following: Boolean,
    scrollRevision: Int,
    onManualScroll: () -> Unit,
    modifier: Modifier
) {
    val language = languageTag?.let(Locale::forLanguageTag)?.takeIf { it.language.isNotEmpty() }
        ?.getDisplayLanguage(Locale.getDefault())?.takeIf(String::isNotBlank)
    val title = when {
        language != null -> stringResource(if (original) R.string.learning_original_language else R.string.learning_translation_language, language)
        original -> stringResource(R.string.learning_original)
        else -> stringResource(R.string.learning_translation)
    }
    val prefix = if (original) "learning_original" else "learning_translation"
    val scroll = rememberScrollState()
    val dragging by scroll.interactionSource.collectIsDraggedAsState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(dragging) { if (dragging) onManualScroll() }
    val line = layout?.let { result ->
        activeRange?.let { result.getLineForOffset(it.start.coerceIn(0, text.length)) }
    }
    LaunchedEffect(line, viewportHeight, following, scrollRevision, layout?.size) {
        if (!following || dragging || line == null || viewportHeight <= 0) return@LaunchedEffect
        val result = layout ?: return@LaunchedEffect
        val top = result.getLineTop(line)
        val bottom = result.getLineBottom(line)
        // Only move when the marker leaves the comfortable middle of the pane.
        // This keeps narration steady instead of scrolling on every word.
        if (top < scroll.value || bottom > scroll.value + viewportHeight * 0.8f) {
            val margin = min(viewportHeight * 0.3f, (viewportHeight - (bottom - top)).coerceAtLeast(0f))
            scroll.animateScrollTo((top - margin).roundToInt().coerceAtLeast(0))
        }
    }
    val highlightColor = if (original) MaterialTheme.colorScheme.secondaryContainer else OratorDesignTokens.warmHighlight
    val highlightInk = if (original) MaterialTheme.colorScheme.onSecondaryContainer else OratorDesignTokens.ink
    val annotated = remember(text, activeRange, highlightColor, highlightInk) {
        buildAnnotatedString {
            append(text)
            activeRange?.let {
                addStyle(SpanStyle(background = highlightColor, color = highlightInk), it.start, it.endExclusive)
            }
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (original) lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant, 0.4f)
            else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (original) {
                    val description = stringResource(R.string.learning_approximate)
                    Text("≈", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp,
                        modifier = Modifier.semantics { contentDescription = description })
                } else {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.learning_narrated),
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Box(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * lineHeightMultiplier(lineHeight)).sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                        .onSizeChanged { viewportHeight = it.height }
                        .verticalScroll(scroll)
                        .testTag(prefix + "_text"),
                    onTextLayout = { layout = it }
                )
            }
        }
    }
}

@Composable
private fun LearningUnavailable(state: LearningPassageState, onClose: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(stringResource(when (state) {
            LearningPassageState.OriginalMissing -> R.string.learning_original_missing
            LearningPassageState.PassageMissing -> R.string.learning_passage_missing
            else -> R.string.learning_load_failed
        }), style = MaterialTheme.typography.bodyMedium)
        if (state == LearningPassageState.Failed) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        TextButton(onClick = onClose) { Text(stringResource(R.string.learning_back_to_narration)) }
    }
}
