package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.EpubContentStore
import com.noloxtreme.tts.reader.domain.EpubSpineContent

class LoadSpineContent @javax.inject.Inject constructor(
    private val epubContentStore: EpubContentStore
) {
    suspend fun execute(id: DocumentId, spineIndex: Int): EpubSpineContent? =
        epubContentStore.spineContent(id, spineIndex)
}
