package com.noloxtreme.tts.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.usecase.ObserveSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    observeSettings: ObserveSettings
) : ViewModel() {
    val settings: StateFlow<OratorSettings> = observeSettings.execute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OratorSettings())
}
