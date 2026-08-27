package com.noloxtreme.tts.reader.ui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A finished audio export as visible in the app's export location. */
data class ExportItem(
    val contentUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val dateModifiedMillis: Long
)

/**
 * Lists the audio files Orator has exported by querying MediaStore directly,
 * so the screen always reflects what is actually on the device. Exporting is
 * not required to happen in this process: the list is rebuilt from storage.
 */
@HiltViewModel
class ExportsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val mutableItems = MutableStateFlow<List<ExportItem>>(emptyList())
    val items: StateFlow<List<ExportItem>> = mutableItems.asStateFlow()

    private val mutableDeleteFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleteFailed: SharedFlow<Unit> = mutableDeleteFailed.asSharedFlow()

    init {
        refresh()
    }

    /** Reloads the exported audio list from MediaStore. */
    fun refresh() {
        viewModelScope.launch {
            mutableItems.value = withContext(Dispatchers.IO) { queryExports() }
        }
    }

    /** Removes an exported audio file from device storage. */
    fun delete(item: ExportItem) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.delete(Uri.parse(item.contentUri), null, null) > 0
                }.getOrDefault(false)
            }
            if (deleted) {
                refresh()
            } else {
                mutableDeleteFailed.tryEmit(Unit)
            }
        }
    }

    private fun queryExports(): List<ExportItem> = runCatching {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        // RELATIVE_PATH exists only on API 29+; on older versions the DATA
        // column points into the shared Music/Orator folder.
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?"
        }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("$EXPORT_DIRECTORY/")
        } else {
            arrayOf("%/$EXPORT_DIRECTORY/%")
        }
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ExportItem(
                            contentUri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex)).toString(),
                            displayName = cursor.getString(nameIndex).orEmpty(),
                            sizeBytes = cursor.getLong(sizeIndex),
                            dateModifiedMillis = cursor.getLong(dateIndex) * 1000L
                        )
                    )
                }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private companion object {
        /** Must match the export location used by [com.noloxtreme.tts.reader.export.MediaStoreSaver]. */
        const val EXPORT_DIRECTORY = "Music/Orator"
    }
}
