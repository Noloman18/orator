package com.noloxtreme.tts.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.SettingsRepository
import com.noloxtreme.tts.reader.domain.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {
    val settings: StateFlow<OratorSettings> = repository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OratorSettings())

    fun setRate(value: Float) = update { it.copy(speechRate = value) }
    fun setPitch(value: Float) = update { it.copy(speechPitch = value) }
    fun setFontSize(value: Int) = update { it.copy(readerFontSizeSp = value) }
    fun setLineHeight(value: LineHeightPreference) = update { it.copy(lineHeight = value) }
    fun setFollowSpokenText(value: Boolean) = update { it.copy(followSpokenText = value) }
    fun setTheme(value: ThemePreference) = update { it.copy(theme = value) }

    private fun update(transform: (OratorSettings) -> OratorSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }
}
