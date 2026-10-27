package com.noloxtreme.tts.reader.ui.translation

import androidx.paging.PagingData
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.Section
import com.noloxtreme.tts.reader.domain.TranslatedDocumentRepository
import com.noloxtreme.tts.reader.domain.TranslatedParagraph
import com.noloxtreme.tts.reader.playback.SpeechConfiguration
import com.noloxtreme.tts.reader.playback.SpeechEngine
import com.noloxtreme.tts.reader.playback.SpeechEvent
import com.noloxtreme.tts.reader.playback.SpeechInitialization
import com.noloxtreme.tts.reader.playback.SpeechSegment
import com.noloxtreme.tts.reader.playback.VoiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val speech = FakeSpeech()
    private val runtime = FakeRuntime()
    private val content = FakeContent()
    private val documents = FakeDocuments()
    private val store = FakeTranslatedDocuments()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TranslationViewModel(speech, runtime, content, documents, store)

    @Test
    fun `one voiced language disables translation without downloading anything`() = runTest {
        speech.voices = speech.voices.take(1)
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()

        assertEquals(TranslationUnavailableReason.NEED_ANOTHER_VOICE, model.state.value.unavailable)
        assertFalse(model.state.value.canTranslate)
        assertEquals(0, runtime.sessions)
    }

    @Test
    fun `unknown document language requires a source choice`() = runTest {
        val model = viewModel()
        model.prepare(book.copy(languageTag = null))
        advanceUntilIdle()

        assertNull(model.state.value.sourceCode)
        assertFalse(model.state.value.canTranslate)
        model.selectSource("en")
        model.selectTarget("fr")
        advanceUntilIdle()
        assertTrue(model.state.value.canTranslate)
        assertEquals(0, runtime.sessions)
    }

    @Test
    fun `clearing a target while a model check runs clears its loading flag`() = runTest {
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()
        runtime.downloadGate = CompletableDeferred()
        model.selectTarget("fr")
        runCurrent()
        assertTrue(model.state.value.checkingModels)

        model.selectSource("fr")
        runCurrent()

        assertNull(model.state.value.targetCode)
        assertFalse(model.state.value.checkingModels)
        model.cancel()
    }

    @Test
    fun `cancelling translation leaves a device check alive until it finishes`() = runTest {
        runtime.downloadGate = CompletableDeferred()
        val model = viewModel()
        model.prepare(book)
        runCurrent()
        assertTrue(model.state.value.checking)

        model.cancel()
        runCurrent()

        assertTrue(model.state.value.checking)
        runtime.downloadGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(model.state.value.checking)
        assertFalse(model.state.value.checkingModels)
        assertFalse(model.state.value.canTranslate)
    }

    @Test
    fun `refresh while a model check runs clears its old loading flag`() = runTest {
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()
        runtime.downloadGate = CompletableDeferred()
        model.selectTarget("fr")
        runCurrent()
        assertTrue(model.state.value.checkingModels)

        model.refresh()
        runtime.downloadGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(model.state.value.checkingModels)
        assertTrue(model.state.value.canTranslate)
    }

    @Test
    fun `successful translation saves a separate copy with translated sections and closes session`() = runTest {
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()
        model.selectTarget("fr")
        advanceUntilIdle()

        model.translate()
        runCurrent()
        val finished = withContext(Dispatchers.Default) {
            withTimeout(5_000) { model.state.first { !it.busy } }
        }

        assertEquals(TranslationPhase.SUCCESS, finished.phase)
        assertEquals(DocumentId("translation"), finished.translatedDocumentId)
        assertEquals(1, store.saves)
        assertEquals(listOf(TranslatedParagraph(0, "translated Hello world")), store.savedParagraphs)
        assertEquals(mapOf(0 to "translated Chapter"), store.savedTitles)
        assertEquals(1, runtime.closedSessions)
    }

    @Test
    fun `failed translation closes its session and never publishes a partial copy`() = runTest {
        runtime.failTranslation = true
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()
        model.selectTarget("fr")
        advanceUntilIdle()

        model.translate()
        runCurrent()
        val finished = withContext(Dispatchers.Default) {
            withTimeout(5_000) { model.state.first { !it.busy } }
        }

        assertEquals(TranslationError.TRANSLATION_FAILED, finished.error)
        assertEquals(0, store.saves)
        assertEquals(1, runtime.closedSessions)
    }

    @Test
    fun `cancel during inference closes its session and never saves`() = runTest {
        runtime.prepareGate = CompletableDeferred()
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()
        model.selectTarget("fr")
        advanceUntilIdle()
        model.translate()
        runCurrent()
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { runtime.sessionStarted.await() }
        }

        model.cancel()
        runCurrent()
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { runtime.sessionClosed.await() }
        }

        assertEquals(TranslationPhase.IDLE, model.state.value.phase)
        assertEquals(0, store.saves)
        assertEquals(1, runtime.closedSessions)
        assertNull(model.state.value.error)
    }

    @Test
    fun `a voice removed after selection prevents translation before opening a session`() = runTest {
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()
        model.selectTarget("fr")
        advanceUntilIdle()
        speech.voices = speech.voices.take(1)

        model.translate()
        advanceUntilIdle()

        assertEquals(TranslationError.VOICE_UNAVAILABLE, model.state.value.error)
        assertEquals(TranslationUnavailableReason.NEED_ANOTHER_VOICE, model.state.value.unavailable)
        assertEquals(listOf("en"), model.state.value.languages.map { it.code })
        assertNull(model.state.value.targetCode)
        assertFalse(model.state.value.canTranslate)
        assertEquals(0, runtime.sessions)
        assertEquals(0, store.saves)
    }

    @Test
    fun `voice lost while translation is busy refreshes the picker without retrying`() = runTest {
        runtime.prepareGate = CompletableDeferred()
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()
        model.selectTarget("fr")
        advanceUntilIdle()
        model.translate()
        runCurrent()
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { runtime.sessionStarted.await() }
        }
        speech.voices = speech.voices.take(1)
        model.refresh() // Resuming from Android settings while busy intentionally defers this check.
        runtime.prepareGate?.complete(Unit)

        val finished = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                model.state.first { it.unavailable == TranslationUnavailableReason.NEED_ANOTHER_VOICE }
            }
        }

        assertEquals(TranslationError.VOICE_UNAVAILABLE, finished.error)
        assertEquals(listOf("en"), finished.languages.map { it.code })
        assertNull(finished.targetCode)
        assertFalse(finished.canTranslate)
        assertEquals(1, runtime.sessions)
        assertEquals(0, store.saves)
    }

    @Test
    fun `missing packs fail promptly on mobile until user opts in`() = runTest {
        runtime.downloaded = emptySet()
        runtime.problem = TranslationError.WIFI_REQUIRED
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()
        model.selectTarget("fr")
        advanceUntilIdle()

        model.translate()
        advanceUntilIdle()

        assertTrue(model.state.value.modelsNeeded)
        assertEquals(TranslationError.WIFI_REQUIRED, model.state.value.error)
        assertEquals(0, runtime.sessions)
        assertEquals(0, store.saves)
    }

    @Test
    fun `refresh removes a language whose voice was uninstalled`() = runTest {
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()
        model.selectTarget("fr")
        advanceUntilIdle()
        assertTrue(model.state.value.canTranslate)

        speech.voices = speech.voices.take(1)
        model.refresh()
        advanceUntilIdle()

        assertEquals(TranslationUnavailableReason.NEED_ANOTHER_VOICE, model.state.value.unavailable)
        assertEquals(listOf("en"), model.state.value.languages.map { it.code })
        assertFalse(model.state.value.canTranslate)
    }

    @Test
    fun `device runtime failure disables translation and refresh can recover`() = runTest {
        runtime.failInspection = true
        val model = viewModel()
        model.prepare(book)
        advanceUntilIdle()
        assertEquals(TranslationUnavailableReason.TRANSLATION_UNAVAILABLE, model.state.value.unavailable)

        runtime.failInspection = false
        model.refresh()
        advanceUntilIdle()

        assertNull(model.state.value.unavailable)
        assertEquals("en", model.state.value.sourceCode)
        assertEquals(0, runtime.sessions)
    }
}

private val book = Document(
    DocumentId("original"), "Original", "original.txt", "text/plain", "sha", "en",
    totalCharacterCount = 11, sectionCount = 1, importedAtEpochMillis = 0, lastOpenedAtEpochMillis = null
)

private class FakeSpeech : SpeechEngine {
    var voices = listOf(VoiceInfo("english", "en-US", false), VoiceInfo("french", "fr-FR", false))
    override val events: Flow<SpeechEvent> = emptyFlow()
    override suspend fun initialize(configuration: SpeechConfiguration) = SpeechInitialization.Ready
    override fun speak(segment: SpeechSegment) = false
    override fun stop() = Unit
    override fun shutdown() = Unit
    override suspend fun availableOfflineVoices() = voices
    override suspend fun speakPreview(text: String) = false
}

private class FakeRuntime : TranslationRuntime {
    override val supportedLanguageCodes = setOf("en", "fr", "zh")
    var downloaded = setOf("fr")
    var downloadGate: CompletableDeferred<Unit>? = null
    var prepareGate: CompletableDeferred<Unit>? = null
    val sessionStarted = CompletableDeferred<Unit>()
    val sessionClosed = CompletableDeferred<Unit>()
    var failInspection = false
    var failTranslation = false
    var sessions = 0
    var closedSessions = 0
    var problem: TranslationError? = null
    override suspend fun downloadedLanguageCodes(): Set<String> {
        downloadGate?.await()
        if (failInspection) error("Runtime cannot initialize")
        return downloaded
    }
    override fun preflight(missingModels: Set<String>, allowMobileData: Boolean, characterCount: Long) = problem
    override fun session(source: String, target: String): TranslationSession {
        sessions++
        return object : TranslationSession {
            override suspend fun prepare(allowMobileData: Boolean) {
                sessionStarted.complete(Unit)
                prepareGate?.await()
            }
            override suspend fun translate(text: String): String {
                if (failTranslation) error("Inference failed")
                return "translated $text"
            }
            override fun close() {
                closedSessions++
                sessionClosed.complete(Unit)
            }
        }
    }
}

private class FakeDocuments : DocumentRepository {
    override fun observeLibrary() = flowOf(listOf(book))
    override fun observeDocument(id: DocumentId) = flowOf(book)
    override suspend fun getDocument(id: DocumentId) = book
    override suspend fun updateLastOpened(id: DocumentId, epochMillis: Long) = Unit
    override suspend fun deleteDocument(id: DocumentId) = Unit
}

private class FakeContent : ContentRepository {
    private val paragraph = Paragraph(book.id, 0, 0, "Hello world", 0, 11)
    override fun pagedParagraphs(id: DocumentId, initialParagraphIndex: Int): Flow<PagingData<Paragraph>> = emptyFlow()
    override suspend fun paragraph(id: DocumentId, index: Int) = paragraph.takeIf { index == 0 }
    override suspend fun paragraphContaining(id: DocumentId, absoluteOffset: Long) = paragraph
    override suspend fun section(id: DocumentId, index: Int) = Section(id, 0, "Chapter", 0, 0, 0, 11)
    override suspend fun allParagraphs(id: DocumentId) = listOf(paragraph)
    override suspend fun sentenceBefore(id: DocumentId, position: DocumentPosition) = position
    override suspend fun sentenceAfter(id: DocumentId, position: DocumentPosition) = position
}

private class FakeTranslatedDocuments : TranslatedDocumentRepository {
    var saves = 0
    var savedParagraphs: List<TranslatedParagraph>? = null
    var savedTitles: Map<Int, String?>? = null
    override suspend fun save(source: Document, targetLanguageTag: String, paragraphs: List<TranslatedParagraph>, sectionTitles: Map<Int, String?>): DocumentId {
        saves++
        savedParagraphs = paragraphs
        savedTitles = sectionTitles
        return DocumentId("translation")
    }
}
