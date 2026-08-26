package com.noloxtreme.tts.reader.playback

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SynthesisResult {
    data object Success : SynthesisResult
    data class Failure(val reason: Int) : SynthesisResult
    data object Timeout : SynthesisResult
}

/**
 * Synthesizes text to WAV files through a dedicated TextToSpeech instance.
 * The export worker owns its own engine so an export can run while live
 * narration keeps using [AndroidTtsEngine]. Voice selection and speech
 * configuration follow the same rules as live narration.
 */
interface FileSynthesizer {
    suspend fun initialize(configuration: SpeechConfiguration): SpeechInitialization

    /** Suspends until the engine reports the utterance written (or a timeout/error). */
    suspend fun synthesizeToFile(utteranceId: String, text: String, destination: File): SynthesisResult

    fun shutdown()
}

@Singleton
class AndroidTtsFileSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context
) : FileSynthesizer {
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var initializationStatus: Int = TextToSpeech.ERROR

    override suspend fun initialize(configuration: SpeechConfiguration): SpeechInitialization {
        val tts = ensureEngine() ?: return SpeechInitialization.EngineUnavailable
        if (initializationStatus != TextToSpeech.SUCCESS) {
            return SpeechInitialization.EngineUnavailable
        }
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

    override suspend fun synthesizeToFile(
        utteranceId: String,
        text: String,
        destination: File
    ): SynthesisResult = withContext(Dispatchers.Main.immediate) {
        val tts = engine ?: return@withContext SynthesisResult.Failure(TextToSpeech.ERROR)
        if (!initialized) return@withContext SynthesisResult.Failure(TextToSpeech.ERROR)
        val expectedUtteranceId = utteranceId
        val completion = CompletableDeferred<SynthesisResult>()
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == expectedUtteranceId && !completion.isCompleted) {
                    completion.complete(SynthesisResult.Success)
                }
            }

            override fun onError(utteranceId: String?) {
                if (!completion.isCompleted) {
                    completion.complete(SynthesisResult.Failure(TextToSpeech.ERROR))
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (!completion.isCompleted) {
                    completion.complete(SynthesisResult.Failure(errorCode))
                }
            }
        }
        tts.setOnUtteranceProgressListener(listener)
        val result = tts.synthesizeToFile(text, Bundle(), destination, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            return@withContext SynthesisResult.Failure(result)
        }
        withTimeoutOrNull(SEGMENT_TIMEOUT_MS) { completion.await() }
            ?: SynthesisResult.Timeout
    }

    override fun shutdown() {
        initialized = false
        engine?.shutdown()
        engine = null
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
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?, errorCode: Int) = Unit
        })
        val resolved = withTimeoutOrNull(INITIALIZATION_TIMEOUT_MS) {
            completion.await()
            engine
        }
        if (resolved == null || initializationStatus != TextToSpeech.SUCCESS) {
            created?.shutdown()
            engine = null
            initialized = false
        }
        engine
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
        const val INITIALIZATION_TIMEOUT_MS = 5_000L
        const val SEGMENT_TIMEOUT_MS = 600_000L
    }
}
