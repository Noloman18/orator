package com.noloxtreme.tts.reader.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.noloxtreme.tts.reader.domain.ExportError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foreground-service progress notification, completion notification, and
 * failure notification for audio exports. The progress notification carries a
 * cancel action that routes through [CancelExportReceiver].
 */
@Singleton
class ExportNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    PROGRESS_CHANNEL_ID,
                    context.getString(R.string.export_channel_progress),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    COMPLETION_CHANNEL_ID,
                    context.getString(R.string.export_channel_completion),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    fun foregroundInfo(percent: Int, bookTitle: String, documentId: String): ForegroundInfo {
        val notification = baseBuilder(PROGRESS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.export_notification_title, bookTitle))
            .setContentText(context.getString(R.string.export_notification_percent, percent))
            .setContentIntent(launchAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.export_cancel_action),
                    cancelPendingIntent(documentId)
                )
            )
            .build()
        return ForegroundInfo(
            PROGRESS_NOTIFICATION_ID,
            notification,
            foregroundServiceType()
        )
    }

    fun completedNotification(uri: String, displayName: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uri), "audio/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pending = PendingIntent.getActivity(
            context,
            COMPLETION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = baseBuilder(COMPLETION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.export_done_title))
            .setContentText(context.getString(R.string.export_done_text, displayName))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    fun failedNotification(error: ExportError, bookTitle: String, detail: String? = null) {
        val text = errorText(error) + detail
            ?.takeIf { it.isNotBlank() }
            ?.let { " — $it" }
            .orEmpty()
        val notification = baseBuilder(COMPLETION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.export_failed_title))
            .setContentText(context.getString(R.string.export_failed_text, text))
            .setAutoCancel(true)
            .build()
        notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    fun errorText(error: ExportError): String = when (error) {
        ExportError.DOCUMENT_MISSING -> context.getString(R.string.export_error_document_missing)
        ExportError.TTS_UNAVAILABLE -> context.getString(R.string.export_error_tts_unavailable)
        ExportError.TTS_LANGUAGE_MISSING -> context.getString(R.string.export_error_language_missing)
        ExportError.SYNTHESIS_FAILED -> context.getString(R.string.export_error_synthesis_failed)
        ExportError.ENCODING_FAILED -> context.getString(R.string.export_error_encoding_failed)
        ExportError.STORAGE_FAILED -> context.getString(R.string.export_error_storage_failed)
        ExportError.UNKNOWN -> context.getString(R.string.export_error_unknown)
    }

    private fun baseBuilder(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)

    private fun launchAppIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            LAUNCH_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun cancelPendingIntent(documentId: String): PendingIntent {
        val intent = Intent(context, CancelExportReceiver::class.java)
            .putExtra(ExportKeys.DOCUMENT_ID, documentId)
        return PendingIntent.getBroadcast(
            context,
            CANCEL_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // mediaProcessing has no time cap and is semantically correct for TTS-to-audio.
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            // dataSync is the valid type below Android 15; capped at 6 hours by the platform.
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }

    private companion object {
        const val PROGRESS_CHANNEL_ID = "audio_export_progress"
        const val COMPLETION_CHANNEL_ID = "audio_export_completion"
        const val PROGRESS_NOTIFICATION_ID = 1001
        const val COMPLETION_NOTIFICATION_ID = 1002
        const val LAUNCH_REQUEST_CODE = 1001
        const val CANCEL_REQUEST_CODE = 1002
        const val COMPLETION_REQUEST_CODE = 1003
    }
}
