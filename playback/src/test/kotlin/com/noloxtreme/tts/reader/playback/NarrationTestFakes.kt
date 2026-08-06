package com.noloxtreme.tts.reader.playback

import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.ProgressRepository
import com.noloxtreme.tts.reader.domain.ReadingProgress
import com.noloxtreme.tts.reader.domain.SettingsRepository
import com.noloxtreme.tts.reader.domain.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeSpeechEngine : SpeechEngine {
    val eventBus = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    override val events: Flow<SpeechEvent> = eventBus

    var initializationResult: SpeechInitialization = SpeechInitialization.Ready
    val initializeCalls = mutableListOf<SpeechConfiguration>()
    val spokenSegments = mutableListOf<SpeechSegment>()
    var stopCalls = 0
    var speakResult = true

    override suspend fun initialize(configuration: SpeechConfiguration): SpeechInitialization {
        initializeCalls += configuration
        return initializationResult
    }

    override fun speak(segment: SpeechSegment): Boolean {
        spokenSegments += segment
        return speakResult
    }

    override fun stop() {
        stopCalls += 1
    }

    override fun shutdown() = Unit

    var offlineVoicesResult: List<VoiceInfo> = emptyList()

    override suspend fun availableOfflineVoices(): List<VoiceInfo> = offlineVoicesResult

    override suspend fun speakPreview(text: String) = Unit

    fun emit(event: SpeechEvent) {
        eventBus.tryEmit(event)
    }
}

internal class FakeAudioFocus : AudioFocusController {
    val eventBus = MutableSharedFlow<AudioFocusEvent>(extraBufferCapacity = 16)
    override val events: Flow<AudioFocusEvent> = eventBus
    var requestResult = true
    var held = false
    var abandonCalls = 0

    override fun request(): Boolean {
        held = requestResult
        return requestResult
    }

    override fun abandon() {
        abandonCalls += 1
        held = false
    }

    fun emit(event: AudioFocusEvent) {
        eventBus.tryEmit(event)
    }
}

internal class FakeNarrationEnvironment : NarrationEnvironment {
    val eventBus = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 8)
    override val events: Flow<RouteEvent> = eventBus

    var wakeLockHeld = false
    var monitoring = false
    var acquireCalls = 0
    var releaseCalls = 0

    override fun acquireWakeLock() {
        acquireCalls += 1
        wakeLockHeld = true
    }

    override fun releaseWakeLock() {
        releaseCalls += 1
        wakeLockHeld = false
    }

    override fun beginRouteMonitoring() {
        monitoring = true
    }

    override fun endRouteMonitoring() {
        monitoring = false
    }

    fun emitNoisy() {
        eventBus.tryEmit(RouteEvent.Noisy)
    }
}

internal class FakeClock : TimeProvider {
    var now: Long = 1_000_000L
    override fun nowEpochMillis(): Long = now
}

internal class FakeDocumentRepository(
    private val documents: MutableMap<String, Document>
) : DocumentRepository {
    override fun observeLibrary(): Flow<List<Document>> =
        MutableStateFlow(documents.values.toList())

    override fun observeDocument(id: DocumentId): Flow<Document?> =
        MutableStateFlow(documents[id.value])

    override suspend fun getDocument(id: DocumentId): Document? = documents[id.value]

    override suspend fun updateLastOpened(id: DocumentId, epochMillis: Long) {
        documents[id.value]?.let { document ->
            documents[id.value] = document.copy(lastOpenedAtEpochMillis = epochMillis)
        }
    }

    override suspend fun deleteDocument(id: DocumentId) {
        documents.remove(id.value)
    }
}

internal class FakeContentRepository(
    paragraphs: List<Paragraph>
) : ContentRepository {
    private val paragraphsByIndex = paragraphs.associateBy { it.paragraphIndex }

    override fun pagedParagraphs(
        id: DocumentId,
        initialParagraphIndex: Int
    ): Flow<androidx.paging.PagingData<Paragraph>> =
        MutableStateFlow(androidx.paging.PagingData.empty())

    override suspend fun paragraph(id: DocumentId, index: Int): Paragraph? =
        paragraphsByIndex[index]

    override suspend fun paragraphContaining(
        id: DocumentId,
        absoluteOffset: Long
    ): Paragraph? = paragraphsByIndex.values.firstOrNull { paragraph ->
        absoluteOffset in paragraph.absoluteStart until paragraph.absoluteEnd
    }

    override suspend fun section(id: DocumentId, index: Int): com.noloxtreme.tts.reader.domain.Section? =
        null

    override suspend fun sentenceBefore(
        id: DocumentId,
        position: DocumentPosition
    ): DocumentPosition {
        val paragraph = paragraphsByIndex[position.paragraphIndex] ?: return position
        val starts = sentenceStarts(paragraph.text)
        val previous = starts.lastOrNull { it < position.offsetInParagraph }
        if (previous != null) return paragraph.positionAt(previous)
        val prior = paragraphsByIndex[position.paragraphIndex - 1]
            ?: return paragraph.positionAt(0)
        return prior.positionAt(sentenceStarts(prior.text).lastOrNull() ?: 0)
    }

    override suspend fun sentenceAfter(
        id: DocumentId,
        position: DocumentPosition
    ): DocumentPosition {
        val paragraph = paragraphsByIndex[position.paragraphIndex] ?: return position
        val starts = sentenceStarts(paragraph.text)
        val next = starts.firstOrNull { it > position.offsetInParagraph }
        if (next != null) return paragraph.positionAt(next)
        val following = paragraphsByIndex[position.paragraphIndex + 1]
            ?: return paragraph.positionAt(paragraph.text.length)
        return following.positionAt(0)
    }

    private fun sentenceStarts(text: String): List<Int> {
        val iterator = java.text.BreakIterator.getSentenceInstance(java.util.Locale.ENGLISH)
        iterator.setText(text)
        val starts = mutableListOf<Int>()
        var start = iterator.first()
        while (start != java.text.BreakIterator.DONE) {
            starts += start
            start = iterator.next()
        }
        return starts.filter { it < text.length }.ifEmpty { listOf(0) }
    }
}

private fun Paragraph.positionAt(offset: Int): DocumentPosition =
    DocumentPosition(
        paragraphIndex = paragraphIndex,
        offsetInParagraph = offset.coerceIn(0, text.length),
        absoluteOffset = absoluteStart + offset.coerceIn(0, text.length)
    )

internal class FakeProgressRepository(
    initial: ReadingProgress? = null
) : ProgressRepository {
    private val state = MutableStateFlow(initial)
    val saved = mutableListOf<ReadingProgress>()

    override fun observeProgress(id: DocumentId): Flow<ReadingProgress?> = state

    override suspend fun getProgress(id: DocumentId): ReadingProgress? = state.value

    override suspend fun saveProgress(progress: ReadingProgress) {
        saved += progress
        state.value = progress
    }
}

internal class FakeSettingsRepository(
    initial: OratorSettings = OratorSettings()
) : SettingsRepository {
    val state = MutableStateFlow(initial)

    override fun observeSettings(): Flow<OratorSettings> = state

    override suspend fun updateSettings(transform: (OratorSettings) -> OratorSettings) {
        state.value = transform(state.value)
    }
}
