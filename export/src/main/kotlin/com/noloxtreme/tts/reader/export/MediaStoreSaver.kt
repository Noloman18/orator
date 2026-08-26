package com.noloxtreme.tts.reader.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.noloxtreme.tts.reader.domain.ExportError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publishes the finished audio file to device storage: MediaStore "Music/Orator"
 * on API 29+, the legacy shared Music directory on API 24-28. A previous export
 * of the same book title is replaced rather than duplicated.
 */
@Singleton
class MediaStoreSaver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    sealed interface SaveResult {
        data class Saved(val uri: String, val displayName: String) : SaveResult
        data class Failed(val error: ExportError, val detail: String? = null) : SaveResult
    }

    suspend fun publish(sourceFile: File, bookTitle: String): SaveResult =
        withContext(Dispatchers.IO) {
            val displayName = sanitizeFileName(bookTitle)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    publishToMediaStore(sourceFile, displayName)
                } else {
                    publishLegacy(sourceFile, displayName)
                }
            } catch (error: Throwable) {
                SaveResult.Failed(ExportError.STORAGE_FAILED, describe(error))
            }
        }

    private fun publishToMediaStore(sourceFile: File, displayName: String): SaveResult {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.delete(
            collection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(displayName, EXPORT_DIRECTORY + "/")
        )
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, AUDIO_MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, EXPORT_DIRECTORY)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: return SaveResult.Failed(ExportError.STORAGE_FAILED)
        try {
            val output = resolver.openOutputStream(uri)
                ?: return SaveResult.Failed(ExportError.STORAGE_FAILED)
            output.use { out -> sourceFile.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return SaveResult.Saved(uri.toString(), displayName)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            return SaveResult.Failed(ExportError.STORAGE_FAILED, describe(error))
        }
    }

    private fun publishLegacy(sourceFile: File, displayName: String): SaveResult {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Orator"
        )
        if (!directory.exists() && !directory.mkdirs()) {
            return SaveResult.Failed(ExportError.STORAGE_FAILED)
        }
        val target = File(directory, displayName)
        sourceFile.copyTo(target, overwrite = true)
        return SaveResult.Saved(Uri.fromFile(target).toString(), displayName)
    }

    private fun sanitizeFileName(title: String): String {
        val cleaned = title
            .replace(Regex("[^A-Za-z0-9 _\\-.]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return (cleaned.ifBlank { "Orator Export" }.take(80)) + ".m4a"
    }

    private fun describe(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
            .ifBlank { error.javaClass.simpleName }
        return "${error.javaClass.simpleName}: $message".take(MAX_DETAIL_CHARS)
    }

    private companion object {
        const val EXPORT_DIRECTORY = "Music/Orator"
        const val AUDIO_MIME_TYPE = "audio/mp4"
        const val MAX_DETAIL_CHARS = 300
    }
}
