package com.noloxtreme.tts.reader.playback

import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.PlaybackError
import com.noloxtreme.tts.reader.domain.ProgressRepository
import com.noloxtreme.tts.reader.domain.ReadingProgress
import com.noloxtreme.tts.reader.domain.SettingsRepository
import com.noloxtreme.tts.reader.domain.SpokenRange
import com.noloxtreme.tts.reader.domain.TimeProvider
import java.text.BreakIterator
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_TTS_CHARS = 3000
private const val CHECKPOINT_INTERVAL_MS = 1000L
private const val CHECKPOINT_CHAR_INTERVAL = 400
private const val MAX_TTS_RETRIES = 1

@Singleton
class NarrationCoordinator @Inject constructor(
    private val contentRepository: ContentRepository,
    private val documentRepository: DocumentRepository,
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val speechEngine: SpeechEngine,
    private val audioFocus: AudioFocusController,
    private val environment: NarrationEnvironment,
    coordinatorContext: CoroutineDispatcher
) : NarrationController {
    private val coordinatorScope = CoroutineScope(SupervisorJob() + coordinatorContext)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<NarrationState>(NarrationState.Idle)

    private var document: Document? = null
    private var progress: ReadingProgress? = null
    private var activeSegment: SpeechSegment? = null
    private var activeConfiguration: SpeechConfiguration? = null
    private var sessionToken: String? = null
    private var segmentNumber = 0
    private var retryCount = 0
    private var lastCheckpointAt = 0L
    private var lastCheckpointOffset = -1L
    private var resumeOnFocusGain = false
    private var lastAppliedSettings: OratorSettings? = null

    override val state: StateFlow<NarrationState> = mutableState.asStateFlow()

    init {
        coordinatorScope.launch {
            speechEngine.events.collectLatest { event ->
                mutex.withLock { handleSpeechEvent(event) }
            }
        }
        coordinatorScope.launch {
            audioFocus.events.collectLatest { event ->
                mutex.withLock { handleFocusEvent(event) }
            }
        }
        coordinatorScope.launch {
            settingsRepository.observeSettings().collectLatest { settings ->
                mutex.withLock { handleSettingsChange(settings) }
            }
        }
        coordinatorScope.launch {
            environment.events.collectLatest { event ->
                mutex.withLock {
                    if (event == RouteEvent.Noisy && currentStateWasPlaying()) {
                        pause(resumeOnFocusGain = false)
                    }
                }
            }
        }
    }

    override fun dispatch(command: NarrationCommand) {
        coordinatorScope.launch {
            mutex.withLock { handleCommand(command) }
        }
    }

    private suspend fun handleCommand(command: NarrationCommand) {
        when (command) {
            is NarrationCommand.Load -> load(command.documentId)
            NarrationCommand.Play -> play()
            is NarrationCommand.Pause -> pause(resumeOnFocusGain = false)
            NarrationCommand.PreviousSentence -> moveSentence(previous = true)
            NarrationCommand.NextSentence -> moveSentence(previous = false)
            is NarrationCommand.JumpSentences -> moveSentence(
                previous = command.previous,
                count = command.count
            )
            is NarrationCommand.SeekTo -> seekTo(command.position)
            NarrationCommand.Stop -> stop()
            NarrationCommand.RestartCompleted -> restartCompleted()
        }
    }

    private suspend fun load(id: DocumentId) {
        invalidateSession()
        audioFocus.abandon()
        releasePlaybackResources()
        resumeOnFocusGain = false
        val loaded = documentRepository.getDocument(id)
        if (loaded == null) {
            document = null
            progress = null
            mutableState.value = NarrationState.Error(id, PlaybackError.DOCUMENT_MISSING, false)
            return
        }
        document = loaded
        progress = progressRepository.getProgress(id) ?: ReadingProgress(
            documentId = id,
            position = DocumentPosition(0, 0, 0),
            updatedAtEpochMillis = timeProvider.nowEpochMillis(),
            completed = false
        )
        documentRepository.updateLastOpened(id, timeProvider.nowEpochMillis())
        mutableState.value = if (progress?.completed == true) {
            NarrationState.Completed(id)
        } else {
            NarrationState.Paused(id, progress!!.position, null)
        }
    }

    private suspend fun play() {
        val currentDocument = document ?: return setError(null, PlaybackError.DOCUMENT_MISSING, false)
        val currentProgress = progress ?: progressRepository.getProgress(currentDocument.id)
        if (currentProgress == null) {
            progress = ReadingProgress(
                currentDocument.id,
                DocumentPosition(0, 0, 0),
                timeProvider.nowEpochMillis(),
                false
            )
        } else {
            progress = currentProgress
        }
        if (progress?.completed == true) return
        val paragraph = contentRepository.paragraph(
            currentDocument.id,
            progress!!.position.paragraphIndex
        )
        if (paragraph == null) return setError(
            currentDocument.id,
            PlaybackError.DOCUMENT_MISSING,
            false
        )
        val start = progress!!.position.offsetInParagraph.coerceIn(0, paragraph.text.length)
        if (start >= paragraph.text.length) {
            val next = contentRepository.paragraph(currentDocument.id, paragraph.paragraphIndex + 1)
            if (next == null) {
                markCompleted(currentDocument.id)
            } else {
                savePosition(next.positionAt(0), false, force = true)
                startPlaybackSegment(next, currentDocument)
            }
            return
        }
        startPlaybackSegment(paragraph, currentDocument)
    }

    private suspend fun startPlaybackSegment(paragraph: Paragraph, currentDocument: Document) {
        retryCount = 0
        val currentPosition = progress?.position ?: paragraph.positionAt(0)
        mutableState.value = NarrationState.Preparing(currentDocument.id, currentPosition)
        if (!audioFocus.request()) {
            setError(currentDocument.id, PlaybackError.AUDIO_FOCUS_DENIED, true)
            return
        }
        environment.acquireWakeLock()
        environment.beginRouteMonitoring()
        val settings = settingsRepository.observeSettingsSnapshot()
        val configuration = SpeechConfiguration(
            languageTag = currentDocument.languageTag,
            voiceName = settings.voiceName,
            rate = settings.speechRate,
            pitch = settings.speechPitch
        )
        activeConfiguration = configuration
        when (speechEngine.initialize(configuration)) {
            SpeechInitialization.Ready -> Unit
            SpeechInitialization.EngineUnavailable -> {
                audioFocus.abandon()
                releasePlaybackResources()
                setError(currentDocument.id, PlaybackError.TTS_UNAVAILABLE, true)
                return
            }
            SpeechInitialization.LanguageUnavailable -> {
                audioFocus.abandon()
                releasePlaybackResources()
                setError(currentDocument.id, PlaybackError.TTS_LANGUAGE_MISSING, true)
                return
            }
        }
        val start = progress?.position?.offsetInParagraph?.coerceIn(0, paragraph.text.length) ?: 0
        val end = chooseEnd(paragraph.text, start)
        if (end <= start) {
            audioFocus.abandon()
            releasePlaybackResources()
            setError(currentDocument.id, PlaybackError.TTS_SPEAK_FAILED, true)
            return
        }
        val token = sessionToken ?: UUID.randomUUID().toString().also { sessionToken = it }
        val segment = SpeechSegment(
            utteranceId = token + "-" + segmentNumber++,
            documentId = currentDocument.id.value,
            paragraphIndex = paragraph.paragraphIndex,
            startInParagraph = start,
            endExclusiveInParagraph = end,
            text = paragraph.text.substring(start, end)
        )
        activeSegment = segment
        mutableState.value = NarrationState.Playing(
            currentDocument.id,
            paragraph.positionAt(start),
            SpokenRange(paragraph.paragraphIndex, start, end)
        )
        if (!speechEngine.speak(segment)) {
            handleSpeakFailure(segment, paragraph, currentDocument)
        }
    }

    private suspend fun pause(resumeOnFocusGain: Boolean) {
        val currentDocument = document ?: return
        if (mutableState.value is NarrationState.Completed) return
        this.resumeOnFocusGain = resumeOnFocusGain
        val currentState = mutableState.value
        val resumePosition = when (currentState) {
            is NarrationState.Playing -> currentState.safePosition
            is NarrationState.Preparing -> currentState.requestedPosition
            is NarrationState.Paused -> currentState.resumePosition
            else -> progress?.position ?: DocumentPosition(0, 0, 0)
        }
        invalidateSession()
        audioFocus.abandon()
        releasePlaybackResources()
        savePosition(resumePosition, false, force = true)
        mutableState.value = NarrationState.Paused(currentDocument.id, resumePosition, null)
    }

    private suspend fun moveSentence(previous: Boolean, count: Int = 1) {
        val currentDocument = document ?: return
        val currentPosition = when (val currentState = mutableState.value) {
            is NarrationState.Playing -> currentState.safePosition
            is NarrationState.Paused -> currentState.resumePosition
            is NarrationState.Preparing -> currentState.requestedPosition
            else -> progress?.position ?: DocumentPosition(0, 0, 0)
        }
        val wasPlaying = currentStateWasPlaying()
        invalidateSession()
        var moved = currentPosition
        repeat(count) {
            moved = if (previous) {
                contentRepository.sentenceBefore(currentDocument.id, moved)
            } else {
                contentRepository.sentenceAfter(currentDocument.id, moved)
            }
        }
        savePosition(moved, false, force = true)
        mutableState.value = NarrationState.Paused(currentDocument.id, moved, null)
        if (wasPlaying) {
            play()
        }
    }

    private fun currentStateWasPlaying(): Boolean =
        mutableState.value is NarrationState.Playing || mutableState.value is NarrationState.Preparing

    private suspend fun seekTo(position: DocumentPosition) {
        val currentDocument = document ?: return
        val paragraph = contentRepository.paragraph(currentDocument.id, position.paragraphIndex)
            ?: return
        val safePosition = paragraph.positionAt(position.offsetInParagraph)
        val shouldResume = currentStateWasPlaying()
        invalidateSession()
        savePosition(safePosition, false, force = true)
        mutableState.value = NarrationState.Paused(currentDocument.id, safePosition, null)
        if (shouldResume) play()
    }

    private suspend fun stop() {
        if (mutableState.value is NarrationState.Completed) return
        invalidateSession()
        audioFocus.abandon()
        releasePlaybackResources()
        resumeOnFocusGain = false
        document?.let {
            mutableState.value = NarrationState.Paused(
                it.id,
                progress?.position ?: DocumentPosition(0, 0, 0),
                null
            )
        } ?: run { mutableState.value = NarrationState.Idle }
    }

    private suspend fun restartCompleted() {
        val currentDocument = document ?: return
        invalidateSession()
        val first = contentRepository.paragraph(currentDocument.id, 0) ?: return
        savePosition(first.positionAt(0), false, force = true)
        mutableState.value = NarrationState.Paused(currentDocument.id, first.positionAt(0), null)
        play()
    }

    private suspend fun handleSettingsChange(settings: OratorSettings) {
        val previous = lastAppliedSettings
        lastAppliedSettings = settings
        val currentState = mutableState.value
        if (currentState !is NarrationState.Playing) return
        if (previous == null) return
        if (previous.voiceName == settings.voiceName &&
            previous.speechRate == settings.speechRate &&
            previous.speechPitch == settings.speechPitch
        ) {
            return
        }
        savePosition(currentState.safePosition, false, force = true)
        invalidateSession()
        play()
    }

    private suspend fun handleFocusEvent(event: AudioFocusEvent) {
        when (event) {
            AudioFocusEvent.Gained -> {
                if (resumeOnFocusGain && mutableState.value is NarrationState.Paused) {
                    resumeOnFocusGain = false
                    play()
                }
            }
            AudioFocusEvent.TransientLoss -> {
                if (currentStateWasPlaying()) pause(resumeOnFocusGain = true)
            }
            AudioFocusEvent.PermanentLoss,
            AudioFocusEvent.Duck -> {
                if (currentStateWasPlaying()) pause(resumeOnFocusGain = false)
            }
        }
    }

    private suspend fun handleSpeechEvent(event: SpeechEvent) {
        val segment = activeSegment ?: return
        val utteranceId = when (event) {
            is SpeechEvent.Started -> event.utteranceId
            is SpeechEvent.RangeStarted -> event.utteranceId
            is SpeechEvent.Completed -> event.utteranceId
            is SpeechEvent.Failed -> event.utteranceId
        }
        if (segment.utteranceId != utteranceId || !utteranceId.startsWith(sessionToken.orEmpty())) {
            return
        }
        val currentDocument = document ?: return
        val paragraph = contentRepository.paragraph(currentDocument.id, segment.paragraphIndex) ?: return
        when (event) {
            is SpeechEvent.Started -> {
                mutableState.value = NarrationState.Playing(
                    currentDocument.id,
                    paragraph.positionAt(segment.startInParagraph),
                    SpokenRange(segment.paragraphIndex, segment.startInParagraph, segment.endExclusiveInParagraph)
                )
            }
            is SpeechEvent.RangeStarted -> {
                val start = (segment.startInParagraph + event.start).coerceIn(
                    segment.startInParagraph,
                    segment.endExclusiveInParagraph
                )
                val end = (segment.startInParagraph + event.endExclusive).coerceIn(
                    start + 1,
                    segment.endExclusiveInParagraph
                )
                val safePosition = paragraph.positionAt(start)
                mutableState.value = NarrationState.Playing(
                    currentDocument.id,
                    safePosition,
                    SpokenRange(segment.paragraphIndex, start, end)
                )
                maybeCheckpoint(safePosition)
            }
            is SpeechEvent.Completed -> completeSegment(segment, paragraph, currentDocument)
            is SpeechEvent.Failed -> handleSpeakFailure(segment, paragraph, currentDocument)
        }
    }

    private suspend fun completeSegment(
        segment: SpeechSegment,
        paragraph: Paragraph,
        currentDocument: Document
    ) {
        val endPosition = paragraph.positionAt(segment.endExclusiveInParagraph)
        activeSegment = null
        environment.releaseWakeLock()
        savePosition(endPosition, false, force = true)
        if (segment.endExclusiveInParagraph < paragraph.text.length) {
            startPlaybackSegment(paragraph, currentDocument)
            return
        }
        val next = contentRepository.paragraph(currentDocument.id, paragraph.paragraphIndex + 1)
        if (next == null) {
            markCompleted(currentDocument.id)
        } else {
            savePosition(next.positionAt(0), false, force = true)
            startPlaybackSegment(next, currentDocument)
        }
    }

    private suspend fun handleSpeakFailure(
        segment: SpeechSegment,
        paragraph: Paragraph,
        currentDocument: Document
    ) {
        maybeCheckpoint(paragraph.positionAt(segment.startInParagraph), force = true)
        if (retryCount < MAX_TTS_RETRIES) {
            retryCount++
            val configuration = activeConfiguration
            if (configuration != null &&
                speechEngine.initialize(configuration) == SpeechInitialization.Ready &&
                speechEngine.speak(segment)
            ) {
                return
            }
        }
        retryCount = 0
        activeSegment = null
        audioFocus.abandon()
        releasePlaybackResources()
        setError(currentDocument.id, PlaybackError.TTS_SPEAK_FAILED, true)
    }

    private suspend fun markCompleted(id: DocumentId) {
        val current = progress?.position ?: DocumentPosition(0, 0, 0)
        savePosition(current, true, force = true)
        invalidateSession()
        audioFocus.abandon()
        releasePlaybackResources()
        resumeOnFocusGain = false
        mutableState.value = NarrationState.Completed(id)
    }

    private fun releasePlaybackResources() {
        environment.releaseWakeLock()
        environment.endRouteMonitoring()
    }

    private suspend fun savePosition(position: DocumentPosition, completed: Boolean, force: Boolean) {
        val currentDocument = document ?: return
        val value = ReadingProgress(
            documentId = currentDocument.id,
            position = position,
            updatedAtEpochMillis = timeProvider.nowEpochMillis(),
            completed = completed
        )
        progress = value
        if (force || value.position.absoluteOffset != lastCheckpointOffset) {
            progressRepository.saveProgress(value)
            lastCheckpointAt = value.updatedAtEpochMillis
            lastCheckpointOffset = value.position.absoluteOffset
        }
    }

    private suspend fun maybeCheckpoint(position: DocumentPosition, force: Boolean = false) {
        val now = timeProvider.nowEpochMillis()
        if (force || now - lastCheckpointAt >= CHECKPOINT_INTERVAL_MS ||
            kotlin.math.abs(position.absoluteOffset - lastCheckpointOffset) >= CHECKPOINT_CHAR_INTERVAL
        ) {
            savePosition(position, false, force = true)
        } else {
            progress = progress?.copy(position = position)
        }
    }

    private fun invalidateSession() {
        speechEngine.stop()
        activeSegment = null
        sessionToken = null
    }

    private fun setError(id: DocumentId?, error: PlaybackError, recoverable: Boolean) {
        mutableState.value = NarrationState.Error(id, error, recoverable)
    }

    private fun chooseEnd(text: String, start: Int): Int {
        val hardEnd = (start + MAX_TTS_CHARS).coerceAtMost(text.length)
        if (hardEnd == text.length) return hardEnd
        val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
        iterator.setText(text)
        var boundary = iterator.following(start)
        var best = start
        while (boundary != BreakIterator.DONE && boundary <= hardEnd) {
            best = boundary
            boundary = iterator.next()
        }
        if (best > start) return best
        val whitespace = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), hardEnd - 1)
        return if (whitespace > start + 1) whitespace else hardEnd
    }

    private fun Paragraph.positionAt(offset: Int): DocumentPosition =
        DocumentPosition(
            paragraphIndex = paragraphIndex,
            offsetInParagraph = offset.coerceIn(0, text.length),
            absoluteOffset = absoluteStart + offset.coerceIn(0, text.length)
        )

    private suspend fun SettingsRepository.observeSettingsSnapshot(): OratorSettings =
        observeSettings().first()
}
