package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.EpubContentStore
import com.noloxtreme.tts.reader.domain.EpubTocEntry

class LoadTableOfContents @javax.inject.Inject constructor(
    private val epubContentStore: EpubContentStore
) {
    suspend fun execute(id: DocumentId): List<EpubTocEntry> = epubContentStore.tableOfContents(id)
}
