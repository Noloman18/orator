package com.noloxtreme.tts.reader

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.core.content.ContextCompat
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.BookNote
import com.noloxtreme.tts.reader.domain.EPUB_MIME_TYPE
import com.noloxtreme.tts.reader.domain.ExportError
import com.noloxtreme.tts.reader.domain.ExportState
import com.noloxtreme.tts.reader.domain.ImportError
import com.noloxtreme.tts.reader.domain.ImportPolicy
import com.noloxtreme.tts.reader.domain.ImportSource
import com.noloxtreme.tts.reader.domain.ImportState
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.PlaybackError
import com.noloxtreme.tts.reader.domain.SpokenRange
import com.noloxtreme.tts.reader.domain.ThemePreference
import com.noloxtreme.tts.reader.designsystem.BookPlaceholder
import com.noloxtreme.tts.reader.designsystem.OratorDesignTokens
import com.noloxtreme.tts.reader.designsystem.R as DesignSystemR
import com.noloxtreme.tts.reader.export.R as ExportR
import com.noloxtreme.tts.reader.playback.NarrationService
import com.noloxtreme.tts.reader.ui.AppViewModel
import com.noloxtreme.tts.reader.ui.ExportMessage
import com.noloxtreme.tts.reader.ui.ExportsScreen
import com.noloxtreme.tts.reader.ui.LibraryViewModel
import com.noloxtreme.tts.reader.ui.LibraryLoadState
import com.noloxtreme.tts.reader.ui.ReaderViewModel
import com.noloxtreme.tts.reader.ui.ReaderLoadState
import com.noloxtreme.tts.reader.ui.ReaderTransport
import com.noloxtreme.tts.reader.ui.VoiceNotePlayer
import com.noloxtreme.tts.reader.ui.VoiceNoteRecorder
import com.noloxtreme.tts.reader.ui.VoiceNoteRecording
import com.noloxtreme.tts.reader.ui.EpubReaderPane
import com.noloxtreme.tts.reader.ui.EpubTocSheet
import com.noloxtreme.tts.reader.ui.lineHeightMultiplier
import com.noloxtreme.tts.reader.ui.SettingsViewModel
import com.noloxtreme.tts.reader.ui.theme.OratorTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val LIBRARY_ROUTE = "library"
private const val SETTINGS_ROUTE = "settings"
private const val EXPORTS_ROUTE = "exports"
private const val READER_ROUTE = "reader/{documentId}"
private const val SPLASH_DURATION_MILLIS = 5_000L
/** Google's anchored adaptive banner test unit. Never replace this with an app-ads.txt entry. */
private const val TEST_NOTES_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
@Composable
fun OratorApp(appViewModel: AppViewModel = hiltViewModel()) {
    val settings by appViewModel.settings.collectAsState()
    val showReviewPrompt by appViewModel.showReviewPrompt.collectAsState()
    val context = LocalContext.current
    val darkTheme = when (settings.theme) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MILLIS)
        showSplash = false
    }

    OratorTheme(darkTheme = darkTheme, dynamicColor = false) {
        if (showSplash) {
            OratorSplashScreen()
        } else {
            val navController = rememberNavController()
            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow
                    .map { it.destination.route }
                    .distinctUntilChanged()
                    .collect { route ->
                        if (route == LIBRARY_ROUTE) appViewModel.recordLibraryLanding()
                    }
            }
            NavHost(
                navController = navController,
                startDestination = LIBRARY_ROUTE,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(LIBRARY_ROUTE) {
                    LibraryScreen(
                        onOpenDocument = { id -> navController.navigate("reader/" + id.value) },
                        onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                        onOpenExports = { navController.navigate(EXPORTS_ROUTE) },
                        showReviewPrompt = showReviewPrompt,
                        onReview = {
                            appViewModel.completeReviewPrompt()
                            openPlayStoreReview(context)
                        },
                        onReviewLater = appViewModel::remindForReviewLater,
                        onReviewDeclined = appViewModel::completeReviewPrompt
                    )
                }
                composable(
                    route = READER_ROUTE,
                    arguments = listOf(navArgument("documentId") { type = NavType.StringType })
                ) { entry ->
                    ReaderScreen(
                        documentId = DocumentId(entry.arguments?.getString("documentId").orEmpty()),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                        onOpenExports = { navController.navigate(EXPORTS_ROUTE) }
                    )
                }
                composable(SETTINGS_ROUTE) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(EXPORTS_ROUTE) {
                    ExportsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun OratorSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OratorDesignTokens.ink)
    ) {
        Image(
            painter = painterResource(R.drawable.orator_splash_art),
            contentDescription = stringResource(R.string.splash_screen_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xCC1E1411)
                        )
                    )
                )
        )
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = Color(0xFFFFEBDD),
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    onOpenDocument: (DocumentId) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExports: () -> Unit,
    showReviewPrompt: Boolean,
    onReview: () -> Unit,
    onReviewLater: () -> Unit,
    onReviewDeclined: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val documents by viewModel.documents.collectAsState()
    val continueDocument by viewModel.continueDocument.collectAsState()
    val continueProgress by viewModel.continueProgress.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var documentToDelete by remember { mutableStateOf<Document?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val source = context.contentResolver.toImportSource(context, uri)
            when {
                !ImportPolicy.supports(source) -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(R.string.error_unsupported_format))
                }
                !ImportPolicy.isWithinSizeLimit(source.reportedSizeBytes) -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(R.string.error_file_too_large))
                }
                else -> viewModel.import(source)
            }
        }
    }

    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Success -> scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.book_imported)) }
            is ImportState.ExistingDocument -> {
                onOpenDocument(state.documentId)
                scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.duplicate_book)) }
            }
            is ImportState.Failure -> scope.launch { snackbarHostState.showSnackbar(importErrorMessage(resources, state.error)) }
            else -> Unit
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deleteFailure.collect {
            snackbarHostState.showSnackbar(resources.getString(R.string.error_remove_book))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.library_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    LibraryMoreMenu(onOpenSettings, onOpenExports)
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(ImportPolicy.pickerMimeTypes.toTypedArray())
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
            when (libraryState) {
                LibraryLoadState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                LibraryLoadState.Error -> {
                    ErrorState(
                        title = stringResource(R.string.library_error_title),
                        body = stringResource(R.string.library_error_body),
                        action = stringResource(R.string.retry),
                        onAction = viewModel::retryLibrary
                    )
                }
                LibraryLoadState.Ready -> if (documents.isEmpty()) {
                    EmptyLibrary(onAddBook = {
                        picker.launch(ImportPolicy.pickerMimeTypes.toTypedArray())
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
                                    progress = continueProgress,
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
    }

    documentToDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text(stringResource(R.string.remove_book_title)) },
            text = { Text(stringResource(R.string.remove_book_body, document.title)) },
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

    if (showReviewPrompt && libraryState is LibraryLoadState.Ready) {
        ReviewPromptDialog(
            onReview = onReview,
            onLater = onReviewLater,
            onDecline = onReviewDeclined
        )
    }
}

@Composable
private fun ReviewPromptDialog(
    onReview: () -> Unit,
    onLater: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.review_prompt_title)) },
        text = { Text(stringResource(R.string.review_prompt_body)) },
        confirmButton = {
            Button(onClick = onReview) { Text(stringResource(R.string.review_prompt_review)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onLater) { Text(stringResource(R.string.review_prompt_later)) }
                TextButton(onClick = onDecline) { Text(stringResource(R.string.review_prompt_decline)) }
            }
        }
    )
}

private fun openPlayStoreReview(context: Context) {
    val packageName = context.packageName
    val storeIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$packageName")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(storeIntent) }
        .recoverCatching { context.startActivity(webIntent) }
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
private fun ErrorState(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(action) }
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
private fun ContinueReadingCard(
    document: Document,
    progress: com.noloxtreme.tts.reader.domain.ReadingProgress?,
    onClick: () -> Unit
) {
    val percentage = document.characterPercentage(progress)
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
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.book_percentage, percentage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
    onOpenExports: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val document by viewModel.document.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val narration by viewModel.narration.collectAsState()
    val sectionTitle by viewModel.sectionTitle.collectAsState()
    val inReadMode by viewModel.readMode.collectAsState()
    val visualReading by viewModel.visualReading.collectAsState()
    val epubReading by viewModel.epubReading.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val transport by viewModel.transport.collectAsState()
    val readerPageIndex by viewModel.readerPageIndex.collectAsState()
    val readerJumpTarget by viewModel.readerJumpTarget.collectAsState()
    val readerActiveWord by viewModel.readerActiveWord.collectAsState()
    val isEpubReadMode = inReadMode && document?.mimeType == EPUB_MIME_TYPE
    var tocSheetVisible by remember(documentId) { mutableStateOf(false) }
    val lazyParagraphs = viewModel.paragraphs.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val narrationPosition = narration.positionFor(documentId)
    val currentRange = (narration as? NarrationState.Playing)
        ?.takeIf { it.documentId == documentId }
        ?.activeRange
    val currentParagraphIndex = narrationPosition?.paragraphIndex ?: -1
    val currentAbsoluteOffset = if (inReadMode) {
        visualReading.activeWord?.position?.absoluteOffset
            ?: progress?.position?.absoluteOffset
            ?: 0L
    } else {
        narrationPosition?.absoluteOffset
            ?: progress?.position?.absoluteOffset
            ?: 0L
    }
    val totalCharacterCount = document?.totalCharacterCount ?: 0L
    val narrationPlaying = narration is NarrationState.Playing || narration is NarrationState.Preparing
    val playing = if (inReadMode) visualReading.isPlaying else narrationPlaying
    val playbackError = narration as? NarrationState.Error
    var followEnabled by remember { mutableStateOf(settings.followSpokenText) }
    var programmaticScroll by remember { mutableStateOf(false) }
    var seekFraction by remember(documentId) { mutableStateOf(0f) }
    var isSeeking by remember(documentId) { mutableStateOf(false) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var showNoteComposer by remember(documentId) { mutableStateOf(false) }
    var showBookNotes by remember(documentId) { mutableStateOf(false) }
    var notePosition by remember(documentId) { mutableStateOf(DocumentPosition(0, 0, 0L)) }

    val exportState by viewModel.exportState.collectAsState()
    val exportSnackbar = remember { SnackbarHostState() }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.exportAudio() }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.exportAudio() }
    val requestExport: () -> Unit = {
        when {
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED ->
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

            Build.VERSION.SDK_INT < 29 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED ->
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)

            else -> viewModel.exportAudio()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.exportMessages.collect { message ->
            when (message) {
                ExportMessage.Started -> exportSnackbar.showSnackbar(
                    message = context.getString(R.string.export_started_notice),
                    duration = SnackbarDuration.Long
                )
                is ExportMessage.Succeeded -> {
                    val result = exportSnackbar.showSnackbar(
                        message = context.getString(R.string.export_succeeded, message.displayName),
                        actionLabel = context.getString(R.string.view),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(Uri.parse(message.contentUri), "audio/mp4")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        }
                    }
                }
                is ExportMessage.Failed -> exportSnackbar.showSnackbar(
                    context.getString(
                        R.string.export_failed_reason,
                        exportErrorMessage(context, message.error) +
                            message.detail
                                ?.takeIf { it.isNotBlank() }
                                ?.let { " — $it" }
                                .orEmpty()
                    )
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.noteMessages.collect { message ->
            exportSnackbar.showSnackbar(
                context.getString(
                    if (message is com.noloxtreme.tts.reader.ui.NoteMessage.Saved) {
                        R.string.note_saved
                    } else {
                        R.string.note_save_failed
                    }
                )
            )
        }
    }

    LaunchedEffect(documentId) {
        viewModel.load(documentId)
    }
    LaunchedEffect(settings.followSpokenText) {
        followEnabled = settings.followSpokenText
    }
    LaunchedEffect(inReadMode) {
        if (inReadMode) chromeVisible = true
    }
    LaunchedEffect(currentAbsoluteOffset, totalCharacterCount) {
        if (!isSeeking) {
            seekFraction = if (totalCharacterCount > 0L) {
                (currentAbsoluteOffset.toFloat() / totalCharacterCount.toFloat())
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }
    LaunchedEffect(currentParagraphIndex, settings.followSpokenText, followEnabled, lazyParagraphs.itemCount, sectionTitle, inReadMode) {
        if (!inReadMode && settings.followSpokenText && followEnabled && currentParagraphIndex >= 0 && currentParagraphIndex < lazyParagraphs.itemCount) {
            val itemIndex = currentParagraphIndex + if (sectionTitle != null) 1 else 0
            if (itemIndex >= 0) {
                programmaticScroll = true
                try {
                    listState.animateScrollToItem(itemIndex)
                } finally {
                    programmaticScroll = false
                }
            }
        }
    }
    LaunchedEffect(listState, currentParagraphIndex, inReadMode) {
        snapshotFlow {
            listState.isScrollInProgress to listState.layoutInfo.visibleItemsInfo.map { it.key }
        }.collect { (scrolling, visibleKeys) ->
            if (!inReadMode && scrolling && !programmaticScroll && currentParagraphIndex >= 0 &&
                !visibleKeys.contains("paragraph-$currentParagraphIndex")
            ) {
                followEnabled = false
            }
        }
    }
    LaunchedEffect(
        visualReading.activeWord,
        inReadMode,
        isEpubReadMode,
        lazyParagraphs.itemCount,
        sectionTitle
    ) {
        val activeWord = visualReading.activeWord ?: return@LaunchedEffect
        if (inReadMode && !isEpubReadMode && activeWord.range.paragraphIndex < lazyParagraphs.itemCount) {
            val itemIndex = activeWord.range.paragraphIndex + if (sectionTitle != null) 1 else 0
            programmaticScroll = true
            try {
                listState.animateScrollToItem(itemIndex)
            } finally {
                programmaticScroll = false
            }
        }
    }

    Scaffold(
        topBar = {
            if (!inReadMode || chromeVisible) {
                TopAppBar(
                    title = {
                        Text(document?.title ?: stringResource(R.string.reader_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back)) }
                    },
                    actions = {
                        if (inReadMode) {
                            if (document?.mimeType == EPUB_MIME_TYPE) {
                                IconButton(onClick = { tocSheetVisible = true }) {
                                    Icon(Icons.Outlined.Toc, contentDescription = stringResource(R.string.table_of_contents))
                                }
                            }
                            IconButton(onClick = viewModel::exitReadMode) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close_read_mode))
                            }
                        } else {
                            IconButton(onClick = viewModel::enterReadMode) {
                                Icon(Icons.Outlined.MenuBook, contentDescription = stringResource(R.string.open_read_mode))
                            }
                        }
                        ExportAndSettingsMenu(
                            exportState = exportState,
                            onExport = requestExport,
                            onCancelExport = viewModel::cancelExport,
                            onMakeNote = {
                                notePosition = viewModel.captureNotePositionAndPause()
                                showNoteComposer = true
                            },
                            onOpenNotes = { showBookNotes = true },
                            onOpenExports = onOpenExports,
                            onOpenSettings = onOpenSettings
                        )
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(exportSnackbar) },
        bottomBar = {
            if ((!inReadMode || chromeVisible) && loadState == ReaderLoadState.Ready) {
                ReaderControls(
                    narration = narration,
                    playing = playing,
                    transport = transport,
                    reading = inReadMode,
                    speechRate = settings.speechRate,
                    visualReadingPace = visualReading.pace,
                    visualReadingCompleted = visualReading.completed,
                    onPlay = {
                        if (inReadMode) {
                            viewModel.toggleVisualReading()
                        } else {
                            if (!playing) {
                            val serviceIntent = android.content.Intent(context, NarrationService::class.java)
                                .putExtra(NarrationService.EXTRA_DOCUMENT_ID, documentId.value)
                            // Start while the reader is visible. Media3 promotes the service to
                            // foreground in sync with playback, avoiding the platform's five-second
                            // foreground-service deadline before narration has entered Playing.
                            context.startService(serviceIntent)
                            }
                            if (narration is NarrationState.Completed) {
                                viewModel.restart()
                            } else if (playing) {
                                viewModel.pause()
                            } else {
                                viewModel.play()
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        when (loadState) {
            ReaderLoadState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            ReaderLoadState.MissingDocument -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.missing_document_title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.missing_document_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = onBack) { Text(stringResource(R.string.back_to_library)) }
                        TextButton(onClick = viewModel::retryLoad) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            ReaderLoadState.Ready -> {
                val currentDocument = document
                if (currentDocument == null) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val displayFraction = if (isSeeking) {
                        seekFraction
                    } else {
                        if (totalCharacterCount > 0L) {
                            (currentAbsoluteOffset.toFloat() / totalCharacterCount.toFloat())
                                .coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    }
                    val percentage = (displayFraction * 100f)
                        .roundToInt()
                        .coerceIn(0, 100)
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        Column(Modifier.fillMaxSize()) {
                            if (exportState is ExportState.Running || exportState is ExportState.Enqueued) {
                                LinearProgressIndicator(
                                    progress = {
                                        ((exportState as? ExportState.Running)?.percent ?: 0) / 100f
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (!inReadMode || chromeVisible) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(stringResource(R.string.book_progress), style = MaterialTheme.typography.labelLarge)
                                        Text(stringResource(R.string.book_percentage, percentage), style = MaterialTheme.typography.labelLarge)
                                    }
                                    Slider(
                                        value = seekFraction,
                                        onValueChange = {
                                            isSeeking = true
                                            seekFraction = it
                                        },
                                        onValueChangeFinished = {
                                            val targetOffset = (
                                                seekFraction * currentDocument.totalCharacterCount.toFloat()
                                            ).roundToLong()
                                            isSeeking = false
                                            if (settings.followSpokenText) followEnabled = true
                                            if (inReadMode) {
                                                viewModel.seekVisualToAbsoluteOffset(targetOffset)
                                            } else {
                                                viewModel.seekToAbsoluteOffset(targetOffset)
                                            }
                                        },
                                        enabled = currentDocument.totalCharacterCount > 0L,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (!inReadMode) {
                                playbackError?.let { error ->
                                    PlaybackErrorCard(
                                        error = error.code,
                                        onRetry = viewModel::play,
                                        onOpenTtsSettings = {
                                            context.startActivity(android.content.Intent("android.settings.TTS_SETTINGS"))
                                        }
                                    )
                                }
                            }
                            if (isEpubReadMode) {
                                val readingState = epubReading
                                if (readingState != null) {
                                    EpubReaderPane(
                                        spineContent = readingState.content,
                                        unavailable = readingState.unavailable,
                                        fontSizeSp = settings.readerFontSizeSp,
                                        lineHeight = settings.lineHeight,
                                        chromeVisible = chromeVisible,
                                        pageIndex = readerPageIndex,
                                        jumpTarget = readerJumpTarget,
                                        activeWord = readerActiveWord,
                                        onNextPage = viewModel::nextReaderPage,
                                        onPreviousPage = viewModel::previousReaderPage,
                                        onPageCountChange = viewModel::setReaderPageCount,
                                        onJumpTargetResolved = viewModel::resolveReaderJump,
                                        onRetry = viewModel::retryCurrentSpine,
                                        onToggleChrome = { chromeVisible = !chromeVisible },
                                        loadImageBytes = viewModel::imageBytes,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 26.dp),
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
                                            val activeRange = if (inReadMode) {
                                                visualReading.activeWord?.range
                                            } else {
                                                currentRange
                                            }
                                            ParagraphText(
                                                text = paragraph.text,
                                                activeRange = activeRange?.takeIf {
                                                    it.paragraphIndex == paragraph.paragraphIndex
                                                },
                                                fontSizeSp = settings.readerFontSizeSp,
                                                lineHeight = settings.lineHeight
                                            )
                                        }
                                    }
                                }
                             }
                        }
                        if (!followEnabled && currentParagraphIndex >= 0 && !inReadMode) {
                            AssistChip(
                                onClick = {
                                    followEnabled = true
                                    val itemIndex = currentParagraphIndex + if (sectionTitle != null) 1 else 0
                                    if (itemIndex >= 0 && itemIndex < lazyParagraphs.itemCount + if (sectionTitle != null) 1 else 0) {
                                        scope.launch {
                                            programmaticScroll = true
                                            try {
                                                listState.animateScrollToItem(itemIndex)
                                            } finally {
                                                programmaticScroll = false
                                            }
                                        }
                                    }
                                },
                                label = { Text(stringResource(R.string.return_to_narration)) },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    val tocReadingState = epubReading
    if (tocSheetVisible && tocReadingState != null) {
        EpubTocSheet(
            entries = tocReadingState.toc,
            currentSpineIndex = tocReadingState.currentSpineIndex,
            onSelect = { entry ->
                tocSheetVisible = false
                viewModel.openTocEntry(entry)
            },
            onDismiss = { tocSheetVisible = false }
        )
    }
    if (showNoteComposer) {
        NoteComposerDialog(
            onSave = { text, voiceRecording ->
                viewModel.saveNote(text, voiceRecording, notePosition)
                showNoteComposer = false
            },
            onDismiss = { showNoteComposer = false }
        )
    }
    if (showBookNotes) {
        BookNotesDialog(
            notes = notes,
            totalCharacterCount = totalCharacterCount,
            onOpenNote = { note ->
                viewModel.goToNote(note.position)
                showBookNotes = false
            },
            onDismiss = { showBookNotes = false }
        )
    }
}

@Composable
private fun NoteComposerDialog(
    onSave: (String, VoiceNoteRecording?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recorder = remember(context) { VoiceNoteRecorder(context) }
    val handedOff = remember { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var voiceRecording by remember { mutableStateOf<VoiceNoteRecording?>(null) }
    var recordingError by remember { mutableStateOf<String?>(null) }
    var isRecording by remember(context) { mutableStateOf(false) }

    fun startRecording() {
        voiceRecording?.file?.delete()
        voiceRecording = null
        isRecording = recorder.start()
        recordingError = if (isRecording) null else context.getString(R.string.note_recording_failed)
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else recordingError = context.getString(R.string.note_microphone_permission_needed)
    }

    DisposableEffect(recorder) {
        onDispose {
            if (!handedOff.value) {
                recorder.discard()
                voiceRecording?.file?.delete()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.make_note)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.note_anchor_hint))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.note_text_label)) },
                    placeholder = { Text(stringResource(R.string.note_text_placeholder)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isRecording) {
                    Text(
                        stringResource(R.string.note_recording),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = {
                        voiceRecording = recorder.stop()
                        isRecording = false
                        if (voiceRecording == null) {
                            recordingError = context.getString(R.string.note_recording_failed)
                        }
                    }) {
                        Text(stringResource(R.string.note_stop_recording))
                    }
                } else {
                    OutlinedButton(onClick = {
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            startRecording()
                        } else {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Text(
                            stringResource(
                                if (voiceRecording == null) {
                                    R.string.note_record_voice
                                } else {
                                    R.string.note_replace_voice
                                }
                            )
                        )
                    }
                }
                voiceRecording?.let { recording ->
                    Text(
                        stringResource(
                            R.string.note_voice_ready,
                            voiceNoteDurationLabel(recording.durationMillis)
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                recordingError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    handedOff.value = true
                    onSave(text, voiceRecording)
                },
                enabled = !isRecording && (text.isNotBlank() || voiceRecording != null)
            ) { Text(stringResource(R.string.save_note)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun BookNotesDialog(
    notes: List<BookNote>,
    totalCharacterCount: Long,
    onOpenNote: (BookNote) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var playingNoteId by remember { mutableStateOf<String?>(null) }
    val voicePlayer = remember {
        VoiceNotePlayer { playingNoteId = null }
    }
    DisposableEffect(voicePlayer) {
        onDispose { voicePlayer.stop() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.book_notes)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (notes.isEmpty()) {
                    Text(stringResource(R.string.no_book_notes))
                } else {
                    notes.forEach { note ->
                        val progress = if (totalCharacterCount > 0L) {
                            ((note.position.absoluteOffset * 100L) / totalCharacterCount)
                                .toInt()
                                .coerceIn(0, 100)
                        } else {
                            0
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    voicePlayer.stop()
                                    onOpenNote(note)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.note_position, progress),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                note.text?.let { noteText ->
                                    Text(noteText, style = MaterialTheme.typography.bodyMedium)
                                }
                                note.voiceRelativePath?.let { relativePath ->
                                    val voiceFile = File(context.filesDir, relativePath)
                                    TextButton(
                                        onClick = {
                                            if (voiceFile.isFile) {
                                                playingNoteId = if (voicePlayer.toggle(note.id, voiceFile)) {
                                                    note.id
                                                } else {
                                                    null
                                                }
                                            }
                                        },
                                        enabled = voiceFile.isFile
                                    ) {
                                        Text(
                                            stringResource(
                                                if (playingNoteId == note.id) {
                                                    R.string.stop_voice_note
                                                } else {
                                                    R.string.play_voice_note
                                                },
                                                voiceNoteDurationLabel(note.voiceDurationMillis ?: 0L)
                                            )
                                        )
                                    }
                                    if (!voiceFile.isFile) {
                                        Text(
                                            stringResource(R.string.voice_note_unavailable),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                TestNotesBannerAd()
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
        }
    )
}

@Composable
private fun TestNotesBannerAd() {
    val context = LocalContext.current
    val adView = remember(context) {
        val adWidth = context.resources.displayMetrics.run {
            (widthPixels / density).toInt().coerceAtLeast(1)
        }
        AdView(context).apply {
            adUnitId = TEST_NOTES_BANNER_AD_UNIT_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth))
        }
    }
    DisposableEffect(adView) {
        adView.loadAd(AdRequest.Builder().build())
        onDispose { adView.destroy() }
    }
    AndroidView(
        factory = { adView },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun voiceNoteDurationLabel(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun ParagraphText(
    text: String,
    activeRange: SpokenRange?,
    fontSizeSp: Int,
    lineHeight: LineHeightPreference
) {
    val multiplier = lineHeightMultiplier(lineHeight)
    Text(
        text = highlightedText(text, activeRange),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSizeSp.coerceIn(14, 32).sp,
            lineHeight = (fontSizeSp.coerceIn(14, 32) * multiplier).sp
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
                background = OratorDesignTokens.warmHighlight,
                color = OratorDesignTokens.ink,
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
    transport: ReaderTransport,
    reading: Boolean,
    speechRate: Float,
    visualReadingPace: Float,
    visualReadingCompleted: Boolean,
    onPlay: () -> Unit
) {
    val speedLabel = narrationSpeedLabel(if (reading) visualReadingPace else speechRate)
    val speedButtonDescription = stringResource(
        if (reading) R.string.increase_reading_pace_at_rate else R.string.increase_speech_speed_at_rate,
        speedLabel
    )
    val completed = if (reading) visualReadingCompleted else narration is NarrationState.Completed
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = transport::previous) {
                Icon(
                    Icons.Outlined.SkipPrevious,
                    contentDescription = stringResource(
                        when {
                            !reading -> R.string.previous_sentence
                            else -> R.string.previous_word
                        }
                    )
                )
            }
            IconButton(onClick = onPlay, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = when {
                        completed -> Icons.Outlined.Replay
                        playing -> Icons.Outlined.Pause
                        else -> Icons.Outlined.PlayArrow
                    },
                    contentDescription = when {
                        completed -> stringResource(R.string.replay)
                        playing -> stringResource(R.string.pause)
                        else -> stringResource(R.string.play)
                    },
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = transport::next) {
                Icon(
                    Icons.Outlined.SkipNext,
                    contentDescription = stringResource(
                        when {
                            !reading -> R.string.next_sentence
                            else -> R.string.next_word
                        }
                    )
                )
            }
            IconButton(
                onClick = transport::increaseSpeed,
                modifier = Modifier.semantics {
                    contentDescription = speedButtonDescription
                }
            ) {
                Text(
                    text = speedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun narrationSpeedLabel(rate: Float): String {
    val rounded = (rate * 100).roundToInt() / 100f
    return if (rounded == rounded.toInt().toFloat()) {
        "${rounded.toInt()}×"
    } else {
        "${rounded}×"
    }
}

@Composable
private fun PlaybackErrorCard(
    error: PlaybackError,
    onRetry: () -> Unit,
    onOpenTtsSettings: () -> Unit
) {
    val needsTtsConfiguration = error == PlaybackError.TTS_UNAVAILABLE ||
        error == PlaybackError.TTS_LANGUAGE_MISSING
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = playbackErrorMessage(error),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                if (needsTtsConfiguration) {
                    TextButton(onClick = onOpenTtsSettings) { Text(stringResource(R.string.configure_tts)) }
                }
            }
        }
    }
}

@Composable
private fun playbackErrorMessage(error: PlaybackError): String = when (error) {
    PlaybackError.TTS_UNAVAILABLE -> stringResource(R.string.playback_error_tts_unavailable)
    PlaybackError.TTS_LANGUAGE_MISSING -> stringResource(R.string.playback_error_language_missing)
    PlaybackError.TTS_SPEAK_FAILED -> stringResource(R.string.playback_error_speak_failed)
    PlaybackError.AUDIO_FOCUS_DENIED -> stringResource(R.string.playback_error_audio_focus)
    PlaybackError.DOCUMENT_MISSING -> stringResource(R.string.playback_error_document_missing)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back)) } }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 22.dp, top = 18.dp, end = 22.dp, bottom = 96.dp),
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
                    Text(stringResource(R.string.offline_voice_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val voices = viewModel.voices.collectAsState().value
                    if (voices.isEmpty()) {
                        Text(
                            stringResource(R.string.no_offline_voices),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            context.startActivity(android.content.Intent("android.settings.TTS_SETTINGS"))
                        }) {
                            Text(stringResource(R.string.install_voice_data))
                        }
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
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                OutlinedButton(
                    onClick = viewModel::previewVoice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.preview_voice))
                }
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
    ImportError.PDF_ENCRYPTED -> resources.getString(R.string.error_pdf_encrypted)
    ImportError.PDF_LIMIT_EXCEEDED -> resources.getString(R.string.error_pdf_limit)
    ImportError.NO_READABLE_TEXT -> resources.getString(R.string.error_no_readable_text)
    ImportError.STORAGE_FULL -> resources.getString(R.string.error_storage_full)
    ImportError.DATABASE_ERROR -> resources.getString(R.string.error_database)
    ImportError.CANCELLED -> resources.getString(R.string.error_cancelled)
}

private fun Document.characterPercentage(
    progress: com.noloxtreme.tts.reader.domain.ReadingProgress?
): Int {
    val total = totalCharacterCount.coerceAtLeast(1L)
    val offset = (progress?.position?.absoluteOffset ?: 0L).coerceIn(0L, total)
    return ((offset * 100L) / total).toInt().coerceIn(0, 100)
}

private fun NarrationState.positionFor(documentId: DocumentId): DocumentPosition? = when (this) {
    is NarrationState.Preparing -> requestedPosition.takeIf { this.documentId == documentId }
    is NarrationState.Playing -> safePosition.takeIf { this.documentId == documentId }
    is NarrationState.Paused -> resumePosition.takeIf { this.documentId == documentId }
    else -> null
}

@Composable
private fun LibraryMoreMenu(onOpenSettings: () -> Unit, onOpenExports: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_actions))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exports_title)) },
                onClick = {
                    menuOpen = false
                    onOpenExports()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                onClick = {
                    menuOpen = false
                    onOpenSettings()
                }
            )
        }
    }
}

@Composable
private fun ExportAndSettingsMenu(
    exportState: ExportState,
    onExport: () -> Unit,
    onCancelExport: () -> Unit,
    onMakeNote: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenExports: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_actions))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.make_note)) },
                onClick = {
                    menuOpen = false
                    onMakeNote()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.book_notes)) },
                onClick = {
                    menuOpen = false
                    onOpenNotes()
                }
            )
            HorizontalDivider()
            val exporting = exportState is ExportState.Enqueued ||
                exportState is ExportState.Running
            if (exporting) {
                val percent = (exportState as? ExportState.Running)?.percent ?: 0
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_progress_menu, percent)) },
                    onClick = {},
                    enabled = false
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_cancel_menu)) },
                    onClick = {
                        menuOpen = false
                        onCancelExport()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_audio)) },
                    onClick = {
                        menuOpen = false
                        onExport()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exports_title)) },
                onClick = {
                    menuOpen = false
                    onOpenExports()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                onClick = {
                    menuOpen = false
                    onOpenSettings()
                }
            )
        }
    }
}

@Composable
private fun exportErrorMessage(error: ExportError): String = exportErrorMessage(
    LocalContext.current,
    error
)

private fun exportErrorMessage(context: Context, error: ExportError): String = when (error) {
    ExportError.DOCUMENT_MISSING -> context.getString(ExportR.string.export_error_document_missing)
    ExportError.TTS_UNAVAILABLE -> context.getString(ExportR.string.export_error_tts_unavailable)
    ExportError.TTS_LANGUAGE_MISSING -> context.getString(ExportR.string.export_error_language_missing)
    ExportError.SYNTHESIS_FAILED -> context.getString(ExportR.string.export_error_synthesis_failed)
    ExportError.ENCODING_FAILED -> context.getString(ExportR.string.export_error_encoding_failed)
    ExportError.STORAGE_FAILED -> context.getString(ExportR.string.export_error_storage_failed)
    ExportError.FOREGROUND_START_NOT_ALLOWED -> {
        context.getString(ExportR.string.export_error_foreground_start_not_allowed)
    }
    ExportError.UNKNOWN -> context.getString(ExportR.string.export_error_unknown)
}
