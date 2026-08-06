package com.noloxtreme.tts.reader.playback

import kotlinx.coroutines.flow.Flow

sealed interface AudioFocusEvent {
    data object Gained : AudioFocusEvent
    data object TransientLoss : AudioFocusEvent
    data object PermanentLoss : AudioFocusEvent
    data object Duck : AudioFocusEvent
}

interface AudioFocusController {
    val events: Flow<AudioFocusEvent>
    fun request(): Boolean
    fun abandon()
}
