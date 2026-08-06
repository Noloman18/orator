package com.noloxtreme.tts.reader.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@Singleton
class SpeechAudioFocus @Inject constructor(
    @ApplicationContext context: Context
) : AudioFocusController {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusEvents = MutableSharedFlow<AudioFocusEvent>(extraBufferCapacity = 8)

    override val events: Flow<AudioFocusEvent> = focusEvents

    private var request: AudioFocusRequest? = null
    private var held = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val event = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> AudioFocusEvent.Gained
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioFocusEvent.TransientLoss
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocusEvent.Duck
            AudioManager.AUDIOFOCUS_LOSS -> AudioFocusEvent.PermanentLoss
            else -> null
        }
        if (event != null) focusEvents.tryEmit(event)
    }

    override fun request(): Boolean {
        if (held) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(listener)
                .build()
            request = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (held) focusEvents.tryEmit(AudioFocusEvent.Gained)
        return held
    }

    override fun abandon() {
        if (!held) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        held = false
        request = null
    }
}
