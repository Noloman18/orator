package com.noloxtreme.tts.reader.domain

import com.noloxtreme.tts.reader.domain.usecase.UpdateSpeechSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSpeechSettingsTest {
    @Test
    fun clearVoiceRestoresAutomaticSelection() = runTest {
        val repository = FakeSettingsRepository(OratorSettings(voiceName = "installed-en"))

        UpdateSpeechSettings(repository).clearVoice()

        assertEquals(null, repository.observeSettings().first().voiceName)
    }

    @Test
    fun updatingRateKeepsSelectedVoice() = runTest {
        val repository = FakeSettingsRepository(OratorSettings(voiceName = "installed-en"))

        UpdateSpeechSettings(repository).execute(speechRate = 1.25f)

        assertEquals("installed-en", repository.observeSettings().first().voiceName)
        assertEquals(1.25f, repository.observeSettings().first().speechRate)
    }

    private class FakeSettingsRepository(initial: OratorSettings) : SettingsRepository {
        private val state = MutableStateFlow(initial)

        override fun observeSettings(): Flow<OratorSettings> = state

        override suspend fun updateSettings(transform: (OratorSettings) -> OratorSettings) {
            state.value = transform(state.value)
        }
    }
}
