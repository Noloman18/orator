package com.noloxtreme.tts.reader.playback

import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController
import com.noloxtreme.tts.reader.domain.NarrationState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class NarrationService : MediaSessionService() {
    @Inject
    lateinit var narrationController: NarrationController

    private lateinit var player: TtsPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = TtsPlayer(narrationController, Looper.getMainLooper())
        mediaSession = MediaSession.Builder(this, player)
            .setId("orator")
            .build()
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)
        lifecycleScope.launch {
            narrationController.state.collect {
                player.refresh()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}

@OptIn(UnstableApi::class)
private class TtsPlayer(
    private val narrationController: NarrationController,
    applicationLooper: Looper
) : SimpleBasePlayer(applicationLooper) {
    private val commands = Player.Commands.Builder()
        .add(Player.COMMAND_PLAY_PAUSE)
        .add(Player.COMMAND_PREPARE)
        .add(Player.COMMAND_STOP)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
        .add(Player.COMMAND_SEEK_TO_NEXT)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        .add(Player.COMMAND_SET_MEDIA_ITEM)
        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
        .add(Player.COMMAND_GET_TIMELINE)
        .add(Player.COMMAND_GET_METADATA)
        .add(Player.COMMAND_RELEASE)
        .build()

    fun refresh() {
        invalidateState()
    }

    override fun getState(): State {
        val narrationState = narrationController.state.value
        val documentId = when (narrationState) {
            is NarrationState.Preparing -> narrationState.documentId
            is NarrationState.Playing -> narrationState.documentId
            is NarrationState.Paused -> narrationState.documentId
            is NarrationState.Completed -> narrationState.documentId
            is NarrationState.Error -> narrationState.documentId
            NarrationState.Idle -> null
        }
        val mediaItems = if (documentId == null) {
            emptyList()
        } else {
            listOf(mediaItemData(documentId))
        }
        val playbackState = when (narrationState) {
            is NarrationState.Preparing -> Player.STATE_BUFFERING
            is NarrationState.Playing -> Player.STATE_READY
            is NarrationState.Completed -> Player.STATE_ENDED
            is NarrationState.Error -> Player.STATE_IDLE
            is NarrationState.Paused -> Player.STATE_READY
            NarrationState.Idle -> Player.STATE_IDLE
        }
        val playWhenReady = narrationState is NarrationState.Playing
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setPlaylist(mediaItems)
            .setCurrentMediaItemIndex(if (mediaItems.isEmpty()) C.INDEX_UNSET else 0)
            .setContentPositionMs(0L)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean) = Futures.immediateVoidFuture().also {
        narrationController.dispatch(
            if (playWhenReady) NarrationCommand.Play else NarrationCommand.Pause()
        )
    }

    override fun handlePrepare() = Futures.immediateVoidFuture()

    override fun handleStop() = Futures.immediateVoidFuture().also {
        narrationController.dispatch(NarrationCommand.Stop)
    }

    override fun handleRelease() = Futures.immediateVoidFuture().also {
        narrationController.dispatch(NarrationCommand.Pause(userInitiated = false))
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ) = Futures.immediateVoidFuture().also {
        val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        mediaItems.getOrNull(index)?.mediaId?.takeIf { it.isNotBlank() }?.let { id ->
            narrationController.dispatch(NarrationCommand.Load(DocumentId(id)))
        }
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ) = Futures.immediateVoidFuture().also {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                narrationController.dispatch(NarrationCommand.PreviousSentence)
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                narrationController.dispatch(NarrationCommand.NextSentence)
        }
    }

    private fun mediaItemData(documentId: DocumentId): MediaItemData =
        MediaItemData.Builder(documentId.value)
            .setMediaItem(
                MediaItem.Builder()
                    .setMediaId(documentId.value)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Orator")
                            .setArtist("Text-to-speech reader")
                            .build()
                    )
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Orator")
                    .setArtist("Text-to-speech reader")
                    .build()
            )
            .setIsSeekable(true)
            .setIsDynamic(true)
            .build()
}
