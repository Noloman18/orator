package com.noloxtreme.tts.reader

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.compose.collectAsLazyPagingItems
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.ImportError
import com.noloxtreme.tts.reader.domain.ImportSource
import com.noloxtreme.tts.reader.domain.ImportState
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.SpokenRange
import com.noloxtreme.tts.reader.domain.ThemePreference
import com.noloxtreme.tts.reader.designsystem.BookPlaceholder
import com.noloxtreme.tts.reader.designsystem.R as DesignSystemR
import com.noloxtreme.tts.reader.playback.NarrationService
import com.noloxtreme.tts.reader.ui.AppViewModel
import com.noloxtreme.tts.reader.ui.LibraryViewModel
import com.noloxtreme.tts.reader.ui.ReaderViewModel
import com.noloxtreme.tts.reader.ui.SettingsViewModel
import com.noloxtreme.tts.reader.ui.theme.OratorTheme
import kotlinx.coroutines.launch

private const val LIBRARY_ROUTE = "library"
private const val SETTINGS_ROUTE = "settings"
private const val READER_ROUTE = "reader/{documentId}"
private val SUPPORTED_PICKER_MIME_TYPES = arrayOf("text/plain", "application/epub+zip")

@Composable
fun OratorApp(appViewModel: AppViewModel = hiltViewModel()) {
    val settings by appViewModel.settings.collectAsState()
    val darkTheme = when (settings.theme) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    OratorTheme(darkTheme = darkTheme, dynamicColor = false) {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = LIBRARY_ROUTE,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(LIBRARY_ROUTE) {
                LibraryScreen(
                    onOpenDocument = { id -> navController.navigate("reader/" + id.value) },
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) }
                )
            }
            composable(
                route = READER_ROUTE,
                arguments = listOf(navArgument("documentId") { type = NavType.StringType })
            ) { entry ->
                ReaderScreen(
                    documentId = DocumentId(entry.arguments?.getString("documentId").orEmpty()),
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) }
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    onOpenDocument: (DocumentId) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val documents by viewModel.documents.collectAsState()
    val continueDocument by viewModel.continueDocument.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var documentToDelete by remember { mutableStateOf<Document?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.import(context.contentResolver.toImportSource(context, uri))
    }

    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Success -> scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.book_imported)) }
            is ImportState.ExistingDocument -> scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.duplicate_book)) }
            is ImportState.Failure -> scope.launch { snackbarHostState.showSnackbar(importErrorMessage(resources, state.error)) }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.library_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(SUPPORTED_PICKER_MIME_TYPES)
            }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_book))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (importState) {
                is ImportState.Copying, ImportState.Parsing, ImportState.Saving -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                else -> Unit
            }
            if (documents.isEmpty()) {
                EmptyLibrary(onAddBook = {
                    picker.launch(SUPPORTED_PICKER_MIME_TYPES)
                })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (continueDocument != null) {
                        item {
                            ContinueReadingCard(
                                document = continueDocument!!,
                                onClick = { onOpenDocument(continueDocument!!.id) }
                            )
                        }
                    }
                    item {
                        Text(
                            text = pluralStringResource(R.plurals.books_count, documents.size, documents.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(documents, key = { it.id.value }) { document ->
                        DocumentCard(
                            document = document,
                            onClick = { onOpenDocument(document.id) },
                            onDelete = { documentToDelete = document }
                        )
                    }
                }
            }
        }
    }

    documentToDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text(stringResource(R.string.remove_book_title)) },
            text = { Text(stringResource(R.string.remove_book_body)) },
            confirmButton = {
                TextButton(onClick = {
                    documentToDelete = null
                    scope.launch {
                        // Deletion is intentionally exposed only after confirmation.
                        // The repository is called by a short-lived child scope below.
                        viewModel.delete(document.id)
                    }
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { documentToDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun EmptyLibrary(onAddBook: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(DesignSystemR.drawable.illustration_empty_library),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.empty_library_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.empty_library_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onAddBook) { Text(stringResource(R.string.add_first_book)) }
        }
    }
}

@Composable
private fun DocumentCard(document: Document, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookPlaceholder(document.title, document.sha256, Modifier.size(52.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    document.originalFileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.remove_book_action), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(document: Document, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookPlaceholder(document.title, document.sha256, Modifier.size(60.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.continue_reading), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(3.dp))
                Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.continue_reading), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    documentId: DocumentId,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val document by viewModel.document.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val narration by viewModel.narration.collectAsState()
    val sectionTitle by viewModel.sectionTitle.collectAsState()
    val lazyParagraphs = viewModel.paragraphs.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val currentRange = (narration as? NarrationState.Playing)?.activeRange
    val currentParagraphIndex = when (narration) {
        is NarrationState.Playing -> (narration as NarrationState.Playing).safePosition.paragraphIndex
        is NarrationState.Paused -> (narration as NarrationState.Paused).resumePosition.paragraphIndex
        is NarrationState.Preparing -> (narration as NarrationState.Preparing).requestedPosition.paragraphIndex
        else -> -1
    }
    val playing = narration is NarrationState.Playing || narration is NarrationState.Preparing

    LaunchedEffect(documentId) {
        viewModel.load(documentId)
    }
    LaunchedEffect(currentParagraphIndex, settings.followSpokenText) {
        if (settings.followSpokenText && currentParagraphIndex >= 0 && currentParagraphIndex < lazyParagraphs.itemCount) {
            listState.animateScrollToItem(currentParagraphIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(document?.title ?: stringResource(R.string.reader_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title)) }
                }
            )
        },
        bottomBar = {
            ReaderControls(
                narration = narration,
                playing = playing,
                onPrevious = viewModel::previousSentence,
                onPlay = {
                    val serviceIntent = android.content.Intent(context, NarrationService::class.java)
                        .putExtra(NarrationService.EXTRA_DOCUMENT_ID, documentId.value)
                    ContextCompat.startForegroundService(context, serviceIntent)
                    if (narration is NarrationState.Completed) viewModel.restart() else if (playing) viewModel.pause() else viewModel.play()
                },
                onNext = viewModel::nextSentence
            )
        }
    ) { padding ->
        if (document == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (sectionTitle != null) {
                    item {
                        Text(
                            text = sectionTitle!!,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                items(lazyParagraphs.itemCount, key = { index -> "paragraph-" + index }) { index ->
                    val paragraph = lazyParagraphs[index]
                    if (paragraph == null) {
                        Spacer(Modifier.fillMaxWidth().height(90.dp))
                    } else {
                        ParagraphText(
                            text = paragraph.text,
                            activeRange = currentRange?.takeIf { it.paragraphIndex == paragraph.paragraphIndex },
                            fontSizeSp = settings.readerFontSizeSp,
                            lineHeight = settings.lineHeight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParagraphText(
    text: String,
    activeRange: SpokenRange?,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference
) {
    val lineHeightMultiplier = when (lineHeight) {
        LineHeightPreference.COMPACT -> 1.35f
        LineHeightPreference.COMFORTABLE -> 1.55f
        LineHeightPreference.SPACIOUS -> 1.8f
    }
    Text(
        text = highlightedText(text, activeRange),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSizeSp.coerceIn(14, 32).sp,
            lineHeight = (fontSizeSp.coerceIn(14, 32) * lineHeightMultiplier).sp
        )
    )
}

private fun highlightedText(text: String, range: SpokenRange?): AnnotatedString = buildAnnotatedString {
    if (range == null) {
        append(text)
        return@buildAnnotatedString
    }
    val start = range.startInParagraph.coerceIn(0, text.length)
    val end = range.endExclusiveInParagraph.coerceIn(start, text.length)
    append(text.substring(0, start))
    if (end > start) {
        pushStyle(
            SpanStyle(
                background = Color(0xFFFFD54F),
                color = Color(0xFF211A00),
                fontWeight = FontWeight.SemiBold
            )
        )
        append(text.substring(start, end))
        pop()
    }
    append(text.substring(end))
}

@Composable
private fun ReaderControls(
    narration: NarrationState,
    playing: Boolean,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) { Icon(Icons.Outlined.SkipPrevious, contentDescription = stringResource(R.string.previous_sentence)) }
            IconButton(onClick = onPlay, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = when {
                        narration is NarrationState.Completed -> Icons.Outlined.Replay
                        playing -> Icons.Outlined.Pause
                        else -> Icons.Outlined.PlayArrow
                    },
                    contentDescription = when {
                        narration is NarrationState.Completed -> stringResource(R.string.replay)
                        playing -> stringResource(R.string.pause)
                        else -> stringResource(R.string.play)
                    },
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onNext) { Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(R.string.next_sentence)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back)) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(22.dp, 18.dp, 22.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                SettingSectionTitle(stringResource(R.string.appearance))
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    options = ThemePreference.entries,
                    selected = settings.theme,
                    label = { themeLabel(it) },
                    onSelected = viewModel::setTheme
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.line_spacing), style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    options = LineHeightPreference.entries,
                    selected = settings.lineHeight,
                    label = { lineHeightLabel(it) },
                    onSelected = viewModel::setLineHeight
                )
            }
            item {
                SettingSectionTitle(stringResource(R.string.reading))
                Text(stringResource(R.string.text_size, settings.readerFontSizeSp), style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = settings.readerFontSizeSp.toFloat(),
                    onValueChange = { viewModel.setFontSize(it.toInt()) },
                    valueRange = 14f..32f,
                    steps = 17
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.follow_spoken_text), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.follow_spoken_text_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.followSpokenText, onCheckedChange = viewModel::setFollowSpokenText)
                }
            }
            item {
                SettingSectionTitle(stringResource(R.string.voice))
                val voices = viewModel.voices.collectAsState().value
                if (voices.isEmpty()) {
                    Text(
                        stringResource(R.string.no_offline_voices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val grouped = voices.groupBy { voice ->
                        java.util.Locale.forLanguageTag(voice.localeLanguageTag)
                            .displayLanguage.ifBlank { voice.localeLanguageTag }
                    }
                    grouped.forEach { (language, groupVoices) ->
                        Text(
                            language,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                        )
                        groupVoices.forEach { voice ->
                            val selected = settings.voiceName == voice.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        viewModel.setVoice(if (selected) null else voice.name)
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    voice.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                if (selected) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.speech_rate, settings.speechRate.toString().take(4)), style = MaterialTheme.typography.titleMedium)
                Slider(value = settings.speechRate, onValueChange = viewModel::setRate, valueRange = 0.5f..2f)
                Text(stringResource(R.string.pitch, settings.speechPitch.toString().take(4)), style = MaterialTheme.typography.titleMedium)
                Slider(value = settings.speechPitch, onValueChange = viewModel::setPitch, valueRange = 0.5f..1.5f)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = viewModel::previewVoice) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.preview_voice))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.offline_voice_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@Composable
private fun themeLabel(value: ThemePreference): String = when (value) {
    ThemePreference.LIGHT -> stringResource(R.string.theme_light)
    ThemePreference.DARK -> stringResource(R.string.theme_dark)
    ThemePreference.SYSTEM -> stringResource(R.string.theme_system)
}

@Composable
private fun lineHeightLabel(value: LineHeightPreference): String = when (value) {
    LineHeightPreference.COMPACT -> stringResource(R.string.line_height_compact)
    LineHeightPreference.COMFORTABLE -> stringResource(R.string.line_height_comfortable)
    LineHeightPreference.SPACIOUS -> stringResource(R.string.line_height_spacious)
}

private fun ContentResolver.toImportSource(context: Context, uri: Uri): ImportSource {
    var displayName: String? = null
    var size: Long? = null
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
        }
    }
    return ImportSource(
        opaqueHandle = uri.toString(),
        displayName = displayName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.imported_book),
        mimeType = getType(uri) ?: "application/octet-stream",
        reportedSizeBytes = size
    )
}

private fun importErrorMessage(resources: android.content.res.Resources, error: ImportError): String = when (error) {
    ImportError.UNSUPPORTED_FORMAT -> resources.getString(R.string.error_unsupported_format)
    ImportError.FILE_TOO_LARGE -> resources.getString(R.string.error_file_too_large)
    ImportError.SOURCE_UNREADABLE -> resources.getString(R.string.error_source_unreadable)
    ImportError.UNSUPPORTED_ENCODING -> resources.getString(R.string.error_unsupported_encoding)
    ImportError.MALFORMED_DOCUMENT -> resources.getString(R.string.error_malformed_document)
    ImportError.EPUB_ENCRYPTED -> resources.getString(R.string.error_epub_encrypted)
    ImportError.EPUB_LIMIT_EXCEEDED -> resources.getString(R.string.error_epub_limit)
    ImportError.NO_READABLE_TEXT -> resources.getString(R.string.error_no_readable_text)
    ImportError.STORAGE_FULL -> resources.getString(R.string.error_storage_full)
    ImportError.DATABASE_ERROR -> resources.getString(R.string.error_database)
    ImportError.CANCELLED -> resources.getString(R.string.error_cancelled)
}
