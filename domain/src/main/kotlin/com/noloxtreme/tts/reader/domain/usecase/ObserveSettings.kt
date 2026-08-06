package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow

class ObserveSettings @javax.inject.Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    fun execute(): Flow<OratorSettings> = settingsRepository.observeSettings()
}
