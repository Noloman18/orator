package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.LineHeightPreference
import com.noloxtreme.tts.reader.domain.SettingsRepository
import com.noloxtreme.tts.reader.domain.ThemePreference

class UpdateReaderSettings @javax.inject.Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(
        readerFontSizeSp: Int? = null,
        lineHeight: LineHeightPreference? = null,
        followSpokenText: Boolean? = null,
        theme: ThemePreference? = null
    ) = settingsRepository.updateSettings { current ->
        current.copy(
            readerFontSizeSp = readerFontSizeSp ?: current.readerFontSizeSp,
            lineHeight = lineHeight ?: current.lineHeight,
            followSpokenText = followSpokenText ?: current.followSpokenText,
            theme = theme ?: current.theme
        )
    }
}
