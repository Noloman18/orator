package com.noloxtreme.tts.reader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.SettingsRepository
import com.noloxtreme.tts.reader.domain.ThemePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "orator_settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) : SettingsRepository {
    private val dataStore = context.settingsDataStore

    override fun observeSettings(): Flow<OratorSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it.toDomain() }

    override suspend fun updateSettings(transform: (OratorSettings) -> OratorSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toDomain())
            preferences[VOICE_NAME] = updated.voiceName.orEmpty()
            preferences[SPEECH_RATE] = updated.speechRate.coerceIn(0.5f, 2.0f)
            preferences[SPEECH_PITCH] = updated.speechPitch.coerceIn(0.5f, 1.5f)
            preferences[FONT_SIZE] = updated.readerFontSizeSp.coerceIn(16, 32)
            preferences[LINE_HEIGHT] = updated.lineHeight.name
            preferences[FOLLOW_TEXT] = updated.followSpokenText
            preferences[THEME] = updated.theme.name
        }
    }
}

private val VOICE_NAME = stringPreferencesKey("voice_name")
private val SPEECH_RATE = floatPreferencesKey("speech_rate")
private val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
private val FONT_SIZE = intPreferencesKey("reader_font_size_sp")
private val LINE_HEIGHT = stringPreferencesKey("line_height")
private val FOLLOW_TEXT = booleanPreferencesKey("follow_spoken_text")
private val THEME = stringPreferencesKey("theme")

private fun Preferences.toDomain(): OratorSettings = OratorSettings(
    voiceName = get(VOICE_NAME)?.takeIf { it.isNotBlank() },
    speechRate = (get(SPEECH_RATE) ?: 1.0f).coerceIn(0.5f, 2.0f),
    speechPitch = (get(SPEECH_PITCH) ?: 1.0f).coerceIn(0.5f, 1.5f),
    readerFontSizeSp = (get(FONT_SIZE) ?: 20).coerceIn(16, 32),
    lineHeight = runCatching {
        LineHeightPreference.valueOf(get(LINE_HEIGHT) ?: LineHeightPreference.COMFORTABLE.name)
    }.getOrDefault(LineHeightPreference.COMFORTABLE),
    followSpokenText = get(FOLLOW_TEXT) ?: true,
    theme = runCatching {
        ThemePreference.valueOf(get(THEME) ?: ThemePreference.SYSTEM.name)
    }.getOrDefault(ThemePreference.SYSTEM)
)

private fun emptyPreferences(): Preferences = androidx.datastore.preferences.core.emptyPreferences()
