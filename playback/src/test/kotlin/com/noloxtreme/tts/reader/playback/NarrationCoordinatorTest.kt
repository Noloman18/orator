package com.noloxtreme.tts.reader.playback

import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.PlaybackError
import com.noloxtreme.tts.reader.domain.ReadingProgress
import com.noloxtreme.tts.reader.domain.SpokenRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NarrationCoordinatorTest {

    private val documentId = DocumentId("doc-1")
    private val document = Document(
        id = documentId,
        title = "Test Book",
        originalFileName = "test.txt",
        mimeType = "text/plain",
        sha256 = "hash",
        languageTag = "en",
        totalCharacterCount = 100,
        sectionCount = 1,
        importedAtEpochMillis = 0L,
        lastOpenedAtEpochMillis = null
    )

    private val firstParagraph = Paragraph(
        documentId = documentId,
        paragraphIndex = 0,
        sectionIndex = 0,
        text = "Hello world. Second sentence here.",
        absoluteStart = 0L,
        absoluteEnd = 34L
    )
    private val secondParagraph = Paragraph(
        documentId = documentId,
        paragraphIndex = 1,
        sectionIndex = 0,
        text = "Final paragraph.",
        absoluteStart = 36L,
        absoluteEnd = 52L
    )

    private class Harness(
        val engine: FakeSpeechEngine,
        val focus: FakeAudioFocus,
        val environment: FakeNarrationEnvironment,
        val clock: FakeClock,
        val documents: FakeDocumentRepository,
        val content: FakeContentRepository,
        val progress: FakeProgressRepository,
        val settings: FakeSettingsRepository,
        val coordinator: NarrationCoordinator
    )

    private fun TestScope.harness(
        progress: ReadingProgress? = null,
        completed: Boolean = false
    ): Harness {
        val engine = FakeSpeechEngine()
        val focus = FakeAudioFocus()
        val environment = FakeNarrationEnvironment()
        val clock = FakeClock()
        val documents = FakeDocumentRepository(mutableMapOf(documentId.value to document))
        val content = FakeContentRepository(listOf(firstParagraph, secondParagraph))
        val initialProgress = progress ?: ReadingProgress(
            documentId = documentId,
            position = DocumentPosition(0, 0, 0),
            updatedAtEpochMillis = 0L,
            completed = completed
        )
        val progressRepo = FakeProgressRepository(initialProgress)
        val settings = FakeSettingsRepository(OratorSettings(speechRate = 1.0f))
        val coordinator = NarrationCoordinator(
            contentRepository = content,
            documentRepository = documents,
            progressRepository = progressRepo,
            settingsRepository = settings,
            timeProvider = clock,
            speechEngine = engine,
            audioFocus = focus,
            environment = environment,
            coordinatorContext = StandardTestDispatcher(testScheduler)
        )
        return Harness(engine, focus, environment, clock, documents, content, progressRepo, settings, coordinator)
    }

    @Test
    fun loadCreatesPausedSessionAtSavedPosition() = runTest {
        val h = harness(
            progress = ReadingProgress(
                documentId = documentId,
                position = DocumentPosition(0, 12, 12L),
                updatedAtEpochMillis = 5L,
                completed = false
            )
        )

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()

        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Paused)
        assertEquals(12, (state as NarrationState.Paused).resumePosition.offsetInParagraph)
        assertTrue(h.engine.spokenSegments.isEmpty())
    }

    @Test
    fun loadMissingDocumentEmitsError() = runTest {
        val h = harness()
        h.documents.deleteDocument(documentId)

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()

        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Error)
        assertEquals(PlaybackError.DOCUMENT_MISSING, (state as NarrationState.Error).code)
    }

    @Test
    fun playSubmitsSegmentFromSavedPositionAndReportsRange() = runTest {
        val h = harness()
        h.progress.saved.clear()

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()

        val segment = h.engine.spokenSegments.single()
        assertEquals(0, segment.paragraphIndex)
        assertEquals(0, segment.startInParagraph)
        assertTrue(segment.text.startsWith("Hello world."))
        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Playing)
        assertEquals(SpokenRange(0, 0, segment.endExclusiveInParagraph), (state as NarrationState.Playing).activeRange)
    }

    @Test
    fun playBeginsAtSavedOffset() = runTest {
        val h = harness(
            progress = ReadingProgress(
                documentId = documentId,
                position = DocumentPosition(0, 13, 13L),
                updatedAtEpochMillis = 1L,
                completed = false
            )
        )

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()

        val segment = h.engine.spokenSegments.single()
        assertEquals(13, segment.startInParagraph)
        assertEquals("Second sentence here.", segment.text)
    }

    @Test
    fun completedSegmentPersistsSafeProgressBeforeNextSegment() = runTest {
        val h = harness()
        h.progress.saved.clear()

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val first = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(first.utteranceId))
        h.engine.emit(SpeechEvent.Completed(first.utteranceId))
        advanceUntilIdle()

        assertEquals(2, h.engine.spokenSegments.size)
        val second = h.engine.spokenSegments[1]
        assertEquals(1, second.paragraphIndex)
        assertEquals(0, second.startInParagraph)
        assertTrue(
            h.progress.saved.any {
                it.position.paragraphIndex == 0 && it.position.absoluteOffset == 34L
            }
        )
        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Playing)
        assertEquals(1, (state as NarrationState.Playing).safePosition.paragraphIndex)
    }

    @Test
    fun finalSegmentCompletionMarksCompletedAtParagraphEnd() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()

        val first = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(first.utteranceId))
        h.engine.emit(SpeechEvent.Completed(first.utteranceId))
        advanceUntilIdle()
        val second = h.engine.spokenSegments[1]
        h.engine.emit(SpeechEvent.Started(second.utteranceId))
        h.engine.emit(SpeechEvent.Completed(second.utteranceId))
        advanceUntilIdle()

        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Completed)
        val finalSave = h.progress.saved.last()
        assertTrue(finalSave.completed)
        assertEquals(1, finalSave.position.paragraphIndex)
        assertEquals(secondParagraph.text.length, finalSave.position.offsetInParagraph)
    }

    @Test
    fun pauseSavesActiveRangeStartAndStopsEngine() = runTest {
        val h = harness()
        h.progress.saved.clear()

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val segment = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(segment.utteranceId))
        h.engine.emit(SpeechEvent.RangeStarted(segment.utteranceId, 6, 12))
        advanceUntilIdle()

        h.coordinator.dispatch(NarrationCommand.Pause())
        advanceUntilIdle()

        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Paused)
        assertEquals(6, (state as NarrationState.Paused).resumePosition.offsetInParagraph)
        assertEquals(6, h.progress.saved.last().position.offsetInParagraph)
        assertTrue(h.engine.stopCalls >= 1)
        assertFalse(h.focus.held)
    }

    @Test
    fun pauseNeverAdvancesBeyondActiveSegmentStart() = runTest {
        val h = harness()
        h.progress.saved.clear()

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val segment = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(segment.utteranceId))
        advanceUntilIdle()

        h.coordinator.dispatch(NarrationCommand.Pause())
        advanceUntilIdle()

        assertEquals(segment.startInParagraph, h.progress.saved.last().position.offsetInParagraph)
    }

    @Test
    fun staleSessionCallbacksAreIgnored() = runTest {
        val h = harness()
        h.progress.saved.clear()

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val segment = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(segment.utteranceId))
        advanceUntilIdle()

        h.coordinator.dispatch(NarrationCommand.Pause())
        advanceUntilIdle()
        val paused = h.coordinator.state.value
        assertTrue(paused is NarrationState.Paused)

        h.engine.emit(SpeechEvent.Completed(segment.utteranceId))
        h.engine.emit(SpeechEvent.RangeStarted(segment.utteranceId, 20, 30))
        advanceUntilIdle()

        val after = h.coordinator.state.value
        assertEquals(paused, after)
        assertTrue(h.engine.spokenSegments.size == 1)
    }

    @Test
    fun nextSentenceFromPausedUpdatesCursorWithoutSpeech() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()

        h.coordinator.dispatch(NarrationCommand.NextSentence)
        advanceUntilIdle()

        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Paused)
        assertEquals(13, (state as NarrationState.Paused).resumePosition.offsetInParagraph)
        assertTrue(h.engine.spokenSegments.isEmpty())
    }

    @Test
    fun jumpSentencesMovesBySentenceBoundariesWithoutSpeech() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()

        h.coordinator.dispatch(NarrationCommand.JumpSentences(previous = false, count = 2))
        advanceUntilIdle()

        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Paused)
        assertEquals(1, (state as NarrationState.Paused).resumePosition.paragraphIndex)
        assertEquals(0, state.resumePosition.offsetInParagraph)
        assertEquals(36L, state.resumePosition.absoluteOffset)
        assertTrue(h.engine.spokenSegments.isEmpty())
    }

    @Test
    fun previousSentenceWhilePlayingMovesAndResumes() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val first = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(first.utteranceId))
        h.engine.emit(SpeechEvent.Completed(first.utteranceId))
        advanceUntilIdle()
        assertEquals(2, h.engine.spokenSegments.size)

        h.coordinator.dispatch(NarrationCommand.PreviousSentence)
        advanceUntilIdle()

        val resumed = h.engine.spokenSegments.last()
        assertEquals(0, resumed.paragraphIndex)
        assertEquals(13, resumed.startInParagraph)
        assertTrue(h.coordinator.state.value is NarrationState.Playing)
    }

    @Test
    fun failedSubmissionIsRetriedOnceThenSurfacesError() = runTest {
        val h = harness()
        h.engine.speakResult = false
        h.progress.saved.clear()

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()

        assertEquals(2, h.engine.spokenSegments.size)
        assertEquals(2, h.engine.initializeCalls.size)
        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Error)
        assertEquals(PlaybackError.TTS_SPEAK_FAILED, (state as NarrationState.Error).code)
        assertTrue((state as NarrationState.Error).recoverable)
        assertEquals(0, h.progress.saved.last().position.offsetInParagraph)
    }

    @Test
    fun failedEventIsRetriedOnceThenSurfacesError() = runTest {
        val h = harness()
        h.progress.saved.clear()

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val segment = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(segment.utteranceId))
        advanceUntilIdle()

        h.engine.speakResult = false
        h.engine.emit(SpeechEvent.Failed(segment.utteranceId, 0))
        advanceUntilIdle()

        assertEquals(2, h.engine.spokenSegments.size)
        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Error)
        assertEquals(PlaybackError.TTS_SPEAK_FAILED, (state as NarrationState.Error).code)
        assertEquals(segment.startInParagraph, h.progress.saved.last().position.offsetInParagraph)
    }

    @Test
    fun transientFocusLossPausesAndGainResumes() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val segment = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(segment.utteranceId))
        h.engine.emit(SpeechEvent.RangeStarted(segment.utteranceId, 6, 12))
        advanceUntilIdle()

        h.focus.emit(AudioFocusEvent.TransientLoss)
        advanceUntilIdle()
        assertTrue(h.coordinator.state.value is NarrationState.Paused)

        h.focus.emit(AudioFocusEvent.Gained)
        advanceUntilIdle()
        val resumed = h.coordinator.state.value
        assertTrue(resumed is NarrationState.Playing)
        assertEquals(6, (resumed as NarrationState.Playing).safePosition.offsetInParagraph)
    }

    @Test
    fun permanentFocusLossDoesNotAutoResume() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()

        h.focus.emit(AudioFocusEvent.PermanentLoss)
        advanceUntilIdle()
        h.focus.emit(AudioFocusEvent.Gained)
        advanceUntilIdle()

        assertTrue(h.coordinator.state.value is NarrationState.Paused)
    }

    @Test
    fun duckRequestPausesInsteadOfLoweringVolume() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()

        h.focus.emit(AudioFocusEvent.Duck)
        advanceUntilIdle()

        assertTrue(h.coordinator.state.value is NarrationState.Paused)
    }

    @Test
    fun speechSettingsChangeWhilePlayingRestartsFromActiveRange() = runTest {
        val h = harness()
        h.progress.saved.clear()

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val first = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(first.utteranceId))
        h.engine.emit(SpeechEvent.RangeStarted(first.utteranceId, 6, 12))
        advanceUntilIdle()

        h.settings.state.value = OratorSettings(speechRate = 1.5f, speechPitch = 1.0f)
        advanceUntilIdle()

        val segments = h.engine.spokenSegments
        assertEquals(2, segments.size)
        assertEquals(6, segments[1].startInParagraph)
        assertTrue(h.engine.initializeCalls.last().rate == 1.5f)
        assertTrue(h.progress.saved.any { it.position.offsetInParagraph == 6 })
    }

    @Test
    fun increaseSpeechRatePersistsTheNextPresetAndRestartsAtTheActiveRange() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val first = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.RangeStarted(first.utteranceId, 6, 12))
        advanceUntilIdle()

        h.coordinator.dispatch(NarrationCommand.IncreaseSpeechRate)
        advanceUntilIdle()

        assertEquals(1.25f, h.settings.state.value.speechRate)
        assertEquals(2, h.engine.spokenSegments.size)
        assertEquals(6, h.engine.spokenSegments.last().startInParagraph)
        assertEquals(1.25f, h.engine.initializeCalls.last().rate)
    }

    @Test
    fun restartCompletedResetsProgressToZeroBeforeSpeaking() = runTest {
        val h = harness(completed = true)
        h.progress.saved.clear()

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        assertTrue(h.coordinator.state.value is NarrationState.Completed)

        h.coordinator.dispatch(NarrationCommand.RestartCompleted)
        advanceUntilIdle()

        assertEquals(0, h.engine.spokenSegments.single().startInParagraph)
        assertEquals(0, h.progress.saved.last().position.paragraphIndex)
        assertEquals(0, h.progress.saved.last().position.offsetInParagraph)
        assertFalse(h.progress.saved.last().completed)
    }

    @Test
    fun stopWhilePlayingSavesPositionAndPauses() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val segment = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(segment.utteranceId))
        h.engine.emit(SpeechEvent.RangeStarted(segment.utteranceId, 6, 12))
        advanceUntilIdle()

        h.coordinator.dispatch(NarrationCommand.Stop)
        advanceUntilIdle()

        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Paused)
        assertEquals(6, (state as NarrationState.Paused).resumePosition.offsetInParagraph)
        assertTrue(h.engine.stopCalls >= 1)
        assertFalse(h.focus.held)
    }

    @Test
    fun audioFocusDeniedEntersRecoverableError() = runTest {
        val h = harness()
        h.focus.requestResult = false

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()

        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Error)
        assertEquals(PlaybackError.AUDIO_FOCUS_DENIED, (state as NarrationState.Error).code)
        assertTrue((state as NarrationState.Error).recoverable)
        assertTrue(h.engine.spokenSegments.isEmpty())
    }

    @Test
    fun engineUnavailableMapsToTtsUnavailable() = runTest {
        val h = harness()
        h.engine.initializationResult = SpeechInitialization.EngineUnavailable

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()

        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Error)
        assertEquals(PlaybackError.TTS_UNAVAILABLE, (state as NarrationState.Error).code)
    }

    @Test
    fun userPauseClearsResumeOnFocusGain() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()

        h.focus.emit(AudioFocusEvent.TransientLoss)
        advanceUntilIdle()
        assertTrue(h.coordinator.state.value is NarrationState.Paused)

        h.coordinator.dispatch(NarrationCommand.Pause())
        advanceUntilIdle()
        h.focus.emit(AudioFocusEvent.Gained)
        advanceUntilIdle()

        assertTrue(h.coordinator.state.value is NarrationState.Paused)
    }

    @Test
    fun noisyRouteWhilePlayingPausesWithoutAutoResume() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val segment = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(segment.utteranceId))
        advanceUntilIdle()

        h.environment.emitNoisy()
        advanceUntilIdle()

        assertTrue(h.coordinator.state.value is NarrationState.Paused)
        h.focus.emit(AudioFocusEvent.Gained)
        advanceUntilIdle()
        assertTrue(h.coordinator.state.value is NarrationState.Paused)
    }

    @Test
    fun wakeLockHeldOnlyWhilePlayingAndMonitoringOnlyWhileActive() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        assertFalse(h.environment.wakeLockHeld)
        assertFalse(h.environment.monitoring)

        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        assertTrue(h.environment.wakeLockHeld)
        assertTrue(h.environment.monitoring)

        val segment = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(segment.utteranceId))
        h.engine.emit(SpeechEvent.Completed(segment.utteranceId))
        advanceUntilIdle()
        assertTrue(h.environment.wakeLockHeld)
        assertTrue(h.environment.monitoring)

        h.coordinator.dispatch(NarrationCommand.Pause())
        advanceUntilIdle()
        assertFalse(h.environment.wakeLockHeld)
        assertFalse(h.environment.monitoring)
        assertTrue(h.environment.releaseCalls >= 1)
    }

    @Test
    fun seekToWhilePlayingMovesCursorAndResumesFromIt() = runTest {
        val h = harness()
        h.progress.saved.clear()

        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()
        val first = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(first.utteranceId))
        h.engine.emit(SpeechEvent.RangeStarted(first.utteranceId, 6, 12))
        advanceUntilIdle()

        h.coordinator.dispatch(
            NarrationCommand.SeekTo(DocumentPosition(0, 13, 13L))
        )
        advanceUntilIdle()

        val segments = h.engine.spokenSegments
        assertEquals(2, segments.size)
        assertEquals(13, segments[1].startInParagraph)
        assertEquals(13, h.progress.saved.last().position.offsetInParagraph)
        assertTrue(h.coordinator.state.value is NarrationState.Playing)
    }

    @Test
    fun seekToWhilePausedUpdatesCursorWithoutSpeech() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()

        h.coordinator.dispatch(NarrationCommand.SeekTo(DocumentPosition(1, 3, 39L)))
        advanceUntilIdle()

        val state = h.coordinator.state.value
        assertTrue(state is NarrationState.Paused)
        assertEquals(1, (state as NarrationState.Paused).resumePosition.paragraphIndex)
        assertEquals(3, (state as NarrationState.Paused).resumePosition.offsetInParagraph)
        assertTrue(h.engine.spokenSegments.isEmpty())
    }

    @Test
    fun completionOfFinalParagraphReleasesWakeLock() = runTest {
        val h = harness()
        h.coordinator.dispatch(NarrationCommand.Load(documentId))
        advanceUntilIdle()
        h.coordinator.dispatch(NarrationCommand.Play)
        advanceUntilIdle()

        val first = h.engine.spokenSegments.single()
        h.engine.emit(SpeechEvent.Started(first.utteranceId))
        h.engine.emit(SpeechEvent.Completed(first.utteranceId))
        advanceUntilIdle()
        val second = h.engine.spokenSegments[1]
        h.engine.emit(SpeechEvent.Started(second.utteranceId))
        h.engine.emit(SpeechEvent.Completed(second.utteranceId))
        advanceUntilIdle()

        assertTrue(h.coordinator.state.value is NarrationState.Completed)
        assertFalse(h.environment.wakeLockHeld)
        assertFalse(h.environment.monitoring)
    }
}
