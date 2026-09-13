package com.noloxtreme.tts.reader.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.noloxtreme.tts.reader.domain.FAST_JUMP_SENTENCE_COUNT
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController

/** Media notification per spec Section 9.2: ongoing while active, dismissible when paused. */
@androidx.media3.common.util.UnstableApi
class OratorNotificationProvider(
    private val context: Context,
    private val narrationController: NarrationController
) : MediaNotification.Provider {
    /**
     * Media3 reads the channel metadata from this provider, but custom providers still need to
     * create their channel before the first foreground notification is posted.
     */
    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
        )
    }

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        MediaNotification.Provider.NotificationChannelInfo(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_channel)
        )

    override fun handleCustomCommand(
        session: MediaSession,
        customAction: String,
        extras: Bundle
    ): Boolean = when (customAction) {
        CUSTOM_REWIND_ACTION -> {
            narrationController.dispatch(
                NarrationCommand.JumpSentences(
                    previous = true,
                    count = FAST_JUMP_SENTENCE_COUNT
                )
            )
            true
        }
        CUSTOM_INCREASE_SPEED_ACTION -> {
            narrationController.dispatch(NarrationCommand.IncreaseSpeechRate)
            true
        }
        else -> false
    }

    override fun createNotification(
        session: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val player = session.player
        val metadata = player.mediaMetadata
        val active = player.playbackState == Player.STATE_BUFFERING || player.isPlaying
        val playing = player.isPlaying

        val builder = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                metadata.title?.toString()?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.notification_default_title)
            )
            .setContentText(
                metadata.artist?.toString()?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.notification_default_section)
            )
            .setOngoing(active)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(session.sessionActivity)
            .setDeleteIntent(actionFactory.createNotificationDismissalIntent(session))
            .addAction(
                actionFactory.createMediaAction(
                    session,
                    IconCompat.createWithResource(context, PREVIOUS_ICON),
                    context.getString(R.string.notification_previous),
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                )
            )
            .addAction(
                actionFactory.createCustomAction(
                    session,
                    IconCompat.createWithResource(context, REWIND_ICON),
                    context.getString(R.string.notification_rewind),
                    CUSTOM_REWIND_ACTION,
                    Bundle.EMPTY
                )
            )
            .addAction(
                actionFactory.createMediaAction(
                    session,
                    IconCompat.createWithResource(
                        context,
                        if (playing) PAUSE_ICON else PLAY_ICON
                    ),
                    context.getString(if (playing) R.string.notification_pause else R.string.notification_play),
                    Player.COMMAND_PLAY_PAUSE
                )
            )
            .addAction(
                actionFactory.createCustomAction(
                    session,
                    IconCompat.createWithResource(context, FAST_FORWARD_ICON),
                    context.getString(R.string.notification_increase_speed),
                    CUSTOM_INCREASE_SPEED_ACTION,
                    Bundle.EMPTY
                )
            )
            .addAction(
                actionFactory.createMediaAction(
                    session,
                    IconCompat.createWithResource(context, NEXT_ICON),
                    context.getString(R.string.notification_next),
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                )
            )

        val artworkData = metadata.artworkData
        if (artworkData != null) {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(
                artworkData,
                0,
                artworkData.size
            )
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }
        }

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "orator_playback"
        const val NOTIFICATION_ID = 1001
        const val PREVIOUS_ICON = android.R.drawable.ic_media_previous
        const val REWIND_ICON = android.R.drawable.ic_media_rew
        const val PLAY_ICON = android.R.drawable.ic_media_play
        const val PAUSE_ICON = android.R.drawable.ic_media_pause
        const val FAST_FORWARD_ICON = android.R.drawable.ic_media_ff
        const val NEXT_ICON = android.R.drawable.ic_media_next
        const val CUSTOM_REWIND_ACTION = "com.noloxtreme.tts.reader.action.REWIND"
        const val CUSTOM_INCREASE_SPEED_ACTION = "com.noloxtreme.tts.reader.action.INCREASE_SPEED"
    }
}
