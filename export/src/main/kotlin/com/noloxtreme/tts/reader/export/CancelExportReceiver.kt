package com.noloxtreme.tts.reader.export

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

/** Cancels the audio-export job for a document, from the notification action. */
class CancelExportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val documentId = intent.getStringExtra(ExportKeys.DOCUMENT_ID) ?: return
        WorkManager.getInstance(context)
            .cancelUniqueWork(AudioExportCoordinator.uniqueName(documentId))
    }
}
