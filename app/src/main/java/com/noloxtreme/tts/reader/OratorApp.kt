package com.noloxtreme.tts.reader

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
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
    val documents by viewModel.documents.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var documentToDelete by remember { mutableStateOf<Document?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.import(context.contentResolver.toImportSource(uri))
    }

    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Success -> scope.launch { snackbarHostState.showSnackbar("Book imported") }
            is ImportState.ExistingDocument -> scope.launch { snackbarHostState.showSnackbar("That book is already in your library") }
            is ImportState.Failure -> scope.launch { snackbarHostState.showSnackbar(importErrorMessage(state.error)) }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Library", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(arrayOf("text/plain", "application/epub+zip", "application/epub", "application/octet-stream"))
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add book")
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
                    picker.launch(arrayOf("text/plain", "application/epub+zip", "application/epub", "application/octet-stream"))
                })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = documents.size.toString() + if (documents.size == 1) " book" else " books",
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
            title = { Text("Remove book?") },
            text = { Text("This removes the imported copy and its saved position from Orator." ) },
            confirmButton = {
                TextButton(onClick = {
                    documentToDelete = null
                    scope.launch {
                        // Deletion is intentionally exposed only after confirmation.
                        // The repository is called by a short-lived child scope below.
                        viewModel.delete(document.id)
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { documentToDelete = null }) { Text("Cancel") } }
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
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(18.dp))
            Text("Your library is empty", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Choose a TXT or non-DRM EPUB file stored on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onAddBook) { Text("Add your first book") }
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
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
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
                Icon(Icons.Outlined.Delete, contentDescription = "Remove book", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                    Text(document?.title ?: "Reader", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                }
            )
        },
        bottomBar = {
            ReaderControls(
                narration = narration,
                playing = playing,
                onPrevious = viewModel::previousSentence,
                onPlay = {
                    ContextCompat.startForegroundService(context, android.content.Intent(context, NarrationService::class.java))
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
            IconButton(onClick = onPrevious) { Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous sentence") }
            IconButton(onClick = onPlay, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = when {
                        narration is NarrationState.Completed -> Icons.Outlined.Replay
                        playing -> Icons.Outlined.Pause
                        else -> Icons.Outlined.PlayArrow
                    },
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onNext) { Icon(Icons.Outlined.SkipNext, contentDescription = "Next sentence") }
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
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(22.dp, 18.dp, 22.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                SettingSectionTitle("Appearance")
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    options = ThemePreference.entries,
                    selected = settings.theme,
                    label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    onSelected = viewModel::setTheme
                )
                Spacer(Modifier.height(14.dp))
                Text("Line spacing", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    options = LineHeightPreference.entries,
                    selected = settings.lineHeight,
                    label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    onSelected = viewModel::setLineHeight
                )
            }
            item {
                SettingSectionTitle("Reading")
                Text("Text size: " + settings.readerFontSizeSp + " sp", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = settings.readerFontSizeSp.toFloat(),
                    onValueChange = { viewModel.setFontSize(it.toInt()) },
                    valueRange = 14f..32f,
                    steps = 17
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Follow spoken text", style = MaterialTheme.typography.titleMedium)
                        Text("Keep the current sentence in view while playing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.followSpokenText, onCheckedChange = viewModel::setFollowSpokenText)
                }
            }
            item {
                SettingSectionTitle("Voice")
                Text("Speech rate: " + settings.speechRate.toString().take(4) + "×", style = MaterialTheme.typography.titleMedium)
                Slider(value = settings.speechRate, onValueChange = viewModel::setRate, valueRange = 0.5f..2f)
                Text("Pitch: " + settings.speechPitch.toString().take(4) + "×", style = MaterialTheme.typography.titleMedium)
                Slider(value = settings.speechPitch, onValueChange = viewModel::setPitch, valueRange = 0.5f..2f)
                Text("Orator uses an installed offline Android voice. Network voices are not selected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    label: (T) -> String,
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

private fun ContentResolver.toImportSource(uri: Uri): ImportSource {
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
        displayName = displayName?.takeIf { it.isNotBlank() } ?: "Imported book",
        mimeType = getType(uri) ?: "application/octet-stream",
        reportedSizeBytes = size
    )
}

private fun importErrorMessage(error: ImportError): String = when (error) {
    ImportError.UNSUPPORTED_FORMAT -> "Choose a TXT or EPUB file"
    ImportError.FILE_TOO_LARGE -> "The file is larger than 100 MB"
    ImportError.SOURCE_UNREADABLE -> "Orator could not read that file"
    ImportError.UNSUPPORTED_ENCODING -> "The text encoding is not supported"
    ImportError.MALFORMED_DOCUMENT -> "The document is malformed"
    ImportError.EPUB_ENCRYPTED -> "Encrypted EPUB files are not supported"
    ImportError.EPUB_LIMIT_EXCEEDED -> "The EPUB exceeds Orator's safety limits"
    ImportError.NO_READABLE_TEXT -> "No readable text was found"
    ImportError.STORAGE_FULL -> "Not enough storage to import the book"
    ImportError.DATABASE_ERROR -> "Could not save the imported book"
    ImportError.CANCELLED -> "Import cancelled"
}
