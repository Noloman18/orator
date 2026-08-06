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

    /** Offline-capable voices only, sorted by name; empty when the engine is unavailable. */
    suspend fun availableOfflineVoices(): List<VoiceInfo>

    /** Speak a preview utterance; must not interfere with or alter saved progress. */
    suspend fun speakPreview(text: String)
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
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        val locale = localeFor(configuration.languageTag)
        val languageStatus = tts.setLanguage(locale)
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
            languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return SpeechInitialization.LanguageUnavailable
        }
        selectOfflineVoice(tts, configuration.voiceName, locale)
        if (tts.voice?.isNetworkConnectionRequired == true) {
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
                    networkRequired = voice.isNetworkConnectionRequired
                )
            }
        )
    }

    override suspend fun speakPreview(text: String) = withContext(Dispatchers.Main.immediate) {
        val tts = engine ?: return@withContext
        if (!initialized) return@withContext
        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            "preview-" + System.currentTimeMillis()
        )
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

    private fun selectOfflineVoice(
        tts: TextToSpeech,
        requestedName: String?,
        locale: Locale
    ) {
        val voices = tts.voices.orEmpty()
        val selected = VoiceResolver.select(
            voices = voices.map { voice ->
                VoiceInfo(
                    name = voice.name,
                    localeLanguageTag = voice.locale.toLanguageTag(),
                    networkRequired = voice.isNetworkConnectionRequired
                )
            },
            requestedName = requestedName,
            languageTag = locale.toLanguageTag()
        )
        if (selected != null) {
            tts.voice = voices.firstOrNull { it.name == selected.name }
        }
    }

    private fun localeFor(languageTag: String?): Locale =
        languageTag?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag)
            ?.takeUnless { it.language.isBlank() }
            ?: Locale.getDefault()

    private companion object {
        const val TTS_INITIALIZATION_TIMEOUT_MS = 5_000L
    }
}
