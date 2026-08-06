package com.noloxtreme.tts.reader.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

private const val ACTION_AUDIO_BECOMING_NOISY = "android.media.AUDIO_BECOMING_NOISY"
private const val NARRATION_WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

sealed interface RouteEvent {
    data object Noisy : RouteEvent
}

interface NarrationEnvironment {
    val events: Flow<RouteEvent>

    /** Reacquire the playback wake lock with a fresh timeout; releases any held lock first. */
    fun acquireWakeLock()

    /** Release the playback wake lock immediately. */
    fun releaseWakeLock()

    /** Register the noisy-route receiver while playback is active. */
    fun beginRouteMonitoring()

    /** Unregister the noisy-route receiver when playback leaves Playing/Preparing. */
    fun endRouteMonitoring()
}

@Singleton
class AndroidNarrationEnvironment @Inject constructor(
    @ApplicationContext private val context: Context
) : NarrationEnvironment {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val routeEvents = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 4)

    override val events: Flow<RouteEvent> = routeEvents

    private var wakeLock: PowerManager.WakeLock? = null
    private var receiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_AUDIO_BECOMING_NOISY) {
                routeEvents.tryEmit(RouteEvent.Noisy)
            }
        }
    }

    override fun acquireWakeLock() {
        releaseWakeLock()
        val lock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Orator:Narration"
        ).apply { setReferenceCounted(false) }
        lock.acquire(NARRATION_WAKE_LOCK_TIMEOUT_MS)
        wakeLock = lock
    }

    override fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun beginRouteMonitoring() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun endRouteMonitoring() {
        if (!receiverRegistered) return
        context.unregisterReceiver(noisyReceiver)
        receiverRegistered = false
    }
}
