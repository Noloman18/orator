package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.SettingsRepository

class UpdateSpeechSettings @javax.inject.Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(
        voiceName: String? = null,
        speechRate: Float? = null,
        speechPitch: Float? = null
    ) = settingsRepository.updateSettings { current ->
        current.copy(
            voiceName = voiceName ?: current.voiceName,
            speechRate = speechRate ?: current.speechRate,
            speechPitch = speechPitch ?: current.speechPitch
        )
    }

    suspend fun clearVoice() = settingsRepository.updateSettings { current ->
        current.copy(voiceName = null)
    }
}
