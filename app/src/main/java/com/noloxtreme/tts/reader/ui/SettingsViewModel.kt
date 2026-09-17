package com.noloxtreme.tts.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import com.noloxtreme.tts.reader.domain.NarrationController
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.ThemePreference
import com.noloxtreme.tts.reader.domain.usecase.ObserveSettings
import com.noloxtreme.tts.reader.domain.usecase.PauseNarration
import com.noloxtreme.tts.reader.domain.usecase.UpdateReaderSettings
import com.noloxtreme.tts.reader.domain.usecase.UpdateSpeechSettings
import com.noloxtreme.tts.reader.playback.SpeechEngine
import com.noloxtreme.tts.reader.playback.SpeechConfiguration
import com.noloxtreme.tts.reader.playback.SpeechInitialization
import com.noloxtreme.tts.reader.playback.VoiceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PREVIEW_SENTENCE = "The quick brown fox jumps over the lazy dog."

enum class VoicePreviewStatus {
    ENGINE_UNAVAILABLE,
    VOICE_UNAVAILABLE,
    REQUEST_REJECTED
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettings,
    private val updateSpeechSettings: UpdateSpeechSettings,
    private val updateReaderSettings: UpdateReaderSettings,
    private val pauseNarration: PauseNarration,
    private val speechEngine: SpeechEngine,
    private val narrationController: NarrationController
) : ViewModel() {
    private val settingsSource = observeSettings.execute()

    val settings: StateFlow<OratorSettings> = settingsSource
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OratorSettings())

    private val mutableVoices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val voices: StateFlow<List<VoiceInfo>> = mutableVoices.asStateFlow()

    private val mutablePreviewStatus = MutableStateFlow<VoicePreviewStatus?>(null)
    val previewStatus: StateFlow<VoicePreviewStatus?> = mutablePreviewStatus.asStateFlow()

    init {
        viewModelScope.launch {
            val installedVoices = speechEngine.availableOfflineVoices()
            mutableVoices.value = installedVoices
            val savedVoice = settingsSource.first().voiceName
            // A previously saved voice can disappear when its Android voice data is
            // removed. Revert to automatic selection rather than leaving narration
            // unable to start.
            if (savedVoice != null && installedVoices.isNotEmpty() &&
                installedVoices.none { it.name == savedVoice }
            ) {
                updateSpeechSettings.clearVoice()
                mutablePreviewStatus.value = VoicePreviewStatus.VOICE_UNAVAILABLE
            }
        }
    }

    fun setRate(value: Float) {
        viewModelScope.launch { updateSpeechSettings.execute(speechRate = value) }
    }

    fun setPitch(value: Float) {
        viewModelScope.launch { updateSpeechSettings.execute(speechPitch = value) }
    }

    fun setVoice(name: String?) {
        viewModelScope.launch {
            mutablePreviewStatus.value = null
            if (name == null) {
                updateSpeechSettings.clearVoice()
            } else if (mutableVoices.value.any { it.name == name }) {
                updateSpeechSettings.execute(voiceName = name)
            }
        }
    }

    fun setFontSize(value: Int) {
        viewModelScope.launch { updateReaderSettings.execute(readerFontSizeSp = value) }
    }

    fun setLineHeight(value: LineHeightPreference) {
        viewModelScope.launch { updateReaderSettings.execute(lineHeight = value) }
    }

    fun setFollowSpokenText(value: Boolean) {
        viewModelScope.launch { updateReaderSettings.execute(followSpokenText = value) }
    }

    fun setTheme(value: ThemePreference) {
        viewModelScope.launch { updateReaderSettings.execute(theme = value) }
    }

    fun previewVoice() {
        viewModelScope.launch {
            mutablePreviewStatus.value = null
            if (narrationController.state.value is NarrationState.Playing ||
                narrationController.state.value is NarrationState.Preparing
            ) {
                pauseNarration.execute()
            }
            val current = settings.value
            val initialized = speechEngine.initialize(
                SpeechConfiguration(
                    languageTag = null,
                    voiceName = current.voiceName,
                    rate = current.speechRate,
                    pitch = current.speechPitch
                )
            )
            when (initialized) {
                SpeechInitialization.Ready -> {
                    if (!speechEngine.speakPreview(PREVIEW_SENTENCE)) {
                        mutablePreviewStatus.value = VoicePreviewStatus.REQUEST_REJECTED
                    }
                }
                SpeechInitialization.EngineUnavailable -> {
                    mutablePreviewStatus.value = VoicePreviewStatus.ENGINE_UNAVAILABLE
                }
                SpeechInitialization.LanguageUnavailable -> {
                    mutablePreviewStatus.value = VoicePreviewStatus.VOICE_UNAVAILABLE
                    if (current.voiceName != null) updateSpeechSettings.clearVoice()
                }
            }
        }
    }
}
