package com.noloxtreme.tts.reader.playback

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

data class SpeechConfiguration(
    val languageTag: String?,
    val voiceName: String?,
    val rate: Float,
    val pitch: Float
)

data class SpeechSegment(
    val utteranceId: String,
    val documentId: String,
    val paragraphIndex: Int,
    val startInParagraph: Int,
    val endExclusiveInParagraph: Int,
    val text: String
)

sealed interface SpeechEvent {
    data class Started(val utteranceId: String) : SpeechEvent
    data class RangeStarted(
        val utteranceId: String,
        val start: Int,
        val endExclusive: Int
    ) : SpeechEvent
    data class Completed(val utteranceId: String) : SpeechEvent
    data class Failed(val utteranceId: String, val reason: Int) : SpeechEvent
}

sealed interface SpeechInitialization {
    data object Ready : SpeechInitialization
    data object EngineUnavailable : SpeechInitialization
    data object LanguageUnavailable : SpeechInitialization
}

interface SpeechEngine {
    val events: Flow<SpeechEvent>

    suspend fun initialize(configuration: SpeechConfiguration): SpeechInitialization

    fun speak(segment: SpeechSegment): Boolean

    fun stop()

    fun shutdown()

    /** Installed offline voices only, sorted by name; empty when the engine is unavailable. */
    suspend fun availableOfflineVoices(): List<VoiceInfo>

    /**
     * Queues a preview utterance without changing saved progress.
     * Returns false when the engine rejects the request immediately.
     */
    suspend fun speakPreview(text: String): Boolean
}

@Singleton
class AndroidTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechEngine {
    private val eventBus = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var initializationStatus: Int = TextToSpeech.ERROR

    override val events: Flow<SpeechEvent> = eventBus

    override suspend fun initialize(configuration: SpeechConfiguration): SpeechInitialization {
        val tts = ensureEngine() ?: return SpeechInitialization.EngineUnavailable
        if (initializationStatus != TextToSpeech.SUCCESS) {
            return SpeechInitialization.EngineUnavailable
        }
        initialized = false
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        val selectedVoice = findInstalledOfflineVoice(tts, configuration)
        if (selectedVoice != null) {
            // setVoice accepts a Voice returned by getVoices(), so it can be used
            // directly for a selected Arabic (or other non-default-language) voice.
            if (tts.setVoice(selectedVoice) != TextToSpeech.SUCCESS) {
                return SpeechInitialization.LanguageUnavailable
            }
        } else {
            val languageStatus = tts.setLanguage(localeFor(configuration.languageTag))
            if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                return SpeechInitialization.LanguageUnavailable
            }
        }
        if (tts.voice?.isUsableOffline() != true) {
            return SpeechInitialization.LanguageUnavailable
        }
        tts.setSpeechRate(configuration.rate.coerceIn(0.5f, 2.0f))
        tts.setPitch(configuration.pitch.coerceIn(0.5f, 2.0f))
        initialized = true
        return SpeechInitialization.Ready
    }

    override fun speak(segment: SpeechSegment): Boolean {
        val tts = engine ?: return false
        if (!initialized) return false
        val result = tts.speak(
            segment.text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            segment.utteranceId
        )
        if (result == TextToSpeech.ERROR) {
            eventBus.tryEmit(SpeechEvent.Failed(segment.utteranceId, result))
            return false
        }
        return true
    }

    override fun stop() {
        engine?.stop()
    }

    override fun shutdown() {
        initialized = false
        engine?.shutdown()
        engine = null
    }

    override suspend fun availableOfflineVoices(): List<VoiceInfo> {
        val tts = ensureEngine() ?: return emptyList()
        if (initializationStatus != TextToSpeech.SUCCESS) return emptyList()
        return VoiceResolver.offlineVoices(
            tts.voices.orEmpty().map { voice ->
                VoiceInfo(
                    name = voice.name,
                    localeLanguageTag = voice.locale.toLanguageTag(),
                    networkRequired = voice.isNetworkConnectionRequired,
                    installed = voice.isInstalled()
                )
            }
        )
    }

    override suspend fun speakPreview(text: String): Boolean = withContext(Dispatchers.Main.immediate) {
        val tts = engine ?: return@withContext false
        if (!initialized) return@withContext false
        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            "preview-" + System.currentTimeMillis()
        ) == TextToSpeech.SUCCESS
    }

    private suspend fun ensureEngine(): TextToSpeech? = withContext(Dispatchers.Main.immediate) {
        engine?.let { return@withContext it }
        val completion = CompletableDeferred<Unit>()
        var created: TextToSpeech? = null
        created = TextToSpeech(context) { status ->
                initializationStatus = status
                if (!completion.isCompleted) completion.complete(Unit)
            }
        engine = created
        created?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.let { eventBus.tryEmit(SpeechEvent.Started(it)) }
                }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { eventBus.tryEmit(SpeechEvent.Completed(it)) }
                }

                override fun onError(utteranceId: String?) {
                    utteranceId?.let { eventBus.tryEmit(SpeechEvent.Failed(it, TextToSpeech.ERROR)) }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    utteranceId?.let { eventBus.tryEmit(SpeechEvent.Failed(it, errorCode)) }
                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int
                ) {
                    utteranceId?.let {
                        eventBus.tryEmit(SpeechEvent.RangeStarted(it, start, end))
                    }
                }
            })
        val resolved = withTimeoutOrNull(TTS_INITIALIZATION_TIMEOUT_MS) {
            completion.await()
            engine
        }
        if (resolved == null) {
            created?.shutdown()
            engine = null
            initialized = false
        }
        resolved
    }

    private fun findInstalledOfflineVoice(
        tts: TextToSpeech,
        configuration: SpeechConfiguration
    ) = run {
        val voices = tts.voices.orEmpty()
        val selected = VoiceResolver.select(
            voices = voices.map { voice ->
                VoiceInfo(
                    name = voice.name,
                    localeLanguageTag = voice.locale.toLanguageTag(),
                    networkRequired = voice.isNetworkConnectionRequired,
                    installed = voice.isInstalled()
                )
            },
            requestedName = configuration.voiceName,
            languageTag = configuration.languageTag
        )
        selected?.let { selection -> voices.firstOrNull { it.name == selection.name } }
    }

    private fun localeFor(languageTag: String?): Locale =
        languageTag?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag)
            ?.takeUnless { it.language.isBlank() }
            ?: Locale.getDefault()

    private fun android.speech.tts.Voice.isInstalled(): Boolean =
        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features

    private fun android.speech.tts.Voice.isUsableOffline(): Boolean =
        !isNetworkConnectionRequired && isInstalled()

    private companion object {
        const val TTS_INITIALIZATION_TIMEOUT_MS = 5_000L
    }
}
