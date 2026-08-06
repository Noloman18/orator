package com.noloxtreme.tts.reader.playback

import android.content.Intent
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
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController
import com.noloxtreme.tts.reader.domain.NarrationState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
@UnstableApi
class NarrationService : MediaSessionService() {
    companion object {
        const val EXTRA_DOCUMENT_ID = "com.noloxtreme.tts.reader.extra.DOCUMENT_ID"
    }

    @Inject
    lateinit var narrationController: NarrationController

    @Inject
    lateinit var documentRepository: DocumentRepository

    @Inject
    lateinit var contentRepository: ContentRepository

    private lateinit var player: TtsPlayer
    private lateinit var mediaSession: MediaSession
    private var artworkCache: Pair<String, ByteArray>? = null

    override fun onCreate() {
        super.onCreate()
        player = TtsPlayer(
            narrationController = narrationController,
            applicationLooper = Looper.getMainLooper(),
            fallbackTitle = getString(R.string.notification_default_title),
            fallbackSection = getString(R.string.notification_default_section)
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionBuilder = MediaSession.Builder(this, player).setId("orator")
        if (launchIntent != null) {
            sessionBuilder.setSessionActivity(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                    PendingIntentFlags.IMMUTABLE or PendingIntentFlags.UPDATE_CURRENT
                )
            )
        }
        mediaSession = sessionBuilder.build()
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)
        setMediaNotificationProvider(OratorNotificationProvider(this))
        lifecycleScope.launch {
            narrationController.state.collect { state ->
                player.refreshState()
                updateMetadata(state)
            }
        }
    }

    private suspend fun updateMetadata(state: NarrationState) {
        val documentId = state.documentId()
        if (documentId == null) {
            player.setDocumentMetadata(null, null, null)
            return
        }
        val document = documentRepository.getDocument(documentId)
        val paragraphIndex = state.activeParagraphIndex()
        val sectionTitle = document?.let { currentDocument ->
            if (paragraphIndex >= 0) {
                val paragraph = contentRepository.paragraph(currentDocument.id, paragraphIndex)
                paragraph?.let { contentRepository.section(currentDocument.id, it.sectionIndex)?.title }
            } else {
                null
            }
        }
        val artwork = document?.let { cachedArtwork(it) }
        player.setDocumentMetadata(document, sectionTitle, artwork)
    }

    private suspend fun cachedArtwork(document: Document): ByteArray? {
        val cached = artworkCache
        if (cached != null && cached.first == document.id.value) return cached.second
        return withContext(Dispatchers.Default) {
            PlaceholderArtworkRenderer.renderPng(document.title, document.sha256)
        }.also { artworkCache = document.id.value to it }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_DOCUMENT_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { id ->
                val current = narrationController.state.value.documentId()
                if (current?.value != id) {
                    narrationController.dispatch(NarrationCommand.Load(DocumentId(id)))
                }
            }
        // A recreated media service may restore a paused document from the last start intent,
        // but it must never begin speaking without an explicit Play command.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}

private object PendingIntentFlags {
    const val IMMUTABLE = android.app.PendingIntent.FLAG_IMMUTABLE
    const val UPDATE_CURRENT = android.app.PendingIntent.FLAG_UPDATE_CURRENT
}

@UnstableApi
private class TtsPlayer(
    private val narrationController: NarrationController,
    applicationLooper: Looper,
    private val fallbackTitle: String,
    private val fallbackSection: String
) : SimpleBasePlayer(applicationLooper) {
    private var documentTitle: String? = null
    private var sectionTitle: String? = null
    private var artworkData: ByteArray? = null

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

    fun refreshState() {
        invalidateState()
    }

    fun setDocumentMetadata(
        document: Document?,
        sectionTitle: String?,
        artworkData: ByteArray?
    ) {
        this.documentTitle = document?.title
        this.sectionTitle = sectionTitle
        this.artworkData = artworkData
        invalidateState()
    }

    override fun getState(): State {
        val narrationState = narrationController.state.value
        val documentId = narrationState.documentId()
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
            .setContentPositionMs(C.TIME_UNSET)
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
        narrationController.dispatch(NarrationCommand.Stop)
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
                            .setTitle(documentTitle ?: fallbackTitle)
                            .setArtist(sectionTitle?.takeIf { it.isNotBlank() } ?: fallbackSection)
                            .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build()
                    )
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(documentTitle ?: fallbackTitle)
                    .setArtist(sectionTitle?.takeIf { it.isNotBlank() } ?: fallbackSection)
                    .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
            )
            .setIsSeekable(false)
            .setIsDynamic(true)
            .build()
}

private fun NarrationState.documentId(): DocumentId? = when (this) {
    is NarrationState.Preparing -> documentId
    is NarrationState.Playing -> documentId
    is NarrationState.Paused -> documentId
    is NarrationState.Completed -> documentId
    is NarrationState.Error -> documentId
    NarrationState.Idle -> null
}

private fun NarrationState.activeParagraphIndex(): Int = when (this) {
    is NarrationState.Preparing -> requestedPosition.paragraphIndex
    is NarrationState.Playing -> safePosition.paragraphIndex
    is NarrationState.Paused -> resumePosition.paragraphIndex
    else -> -1
}
