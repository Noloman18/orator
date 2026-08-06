package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentImporter
import com.noloxtreme.tts.reader.domain.ImportSource
import com.noloxtreme.tts.reader.domain.ImportState
import kotlinx.coroutines.flow.Flow

class ImportDocument @javax.inject.Inject constructor(
    private val documentImporter: DocumentImporter
) {
    fun execute(source: ImportSource): Flow<ImportState> = documentImporter.import(source)

    suspend fun cancelActiveImport() = documentImporter.cancelActiveImport()
}
