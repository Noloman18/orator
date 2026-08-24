package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.EpubContentStore

class LoadCoverPresence @javax.inject.Inject constructor(
    private val epubContentStore: EpubContentStore
) {
    suspend fun execute(id: DocumentId): Boolean = epubContentStore.hasCoverPage(id)
}
