package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveReaderContent @javax.inject.Inject constructor(
    private val documentRepository: DocumentRepository,
    private val progressRepository: ProgressRepository
) {
    fun execute(id: DocumentId): Flow<ReaderContent> = combine(
        documentRepository.observeDocument(id),
        progressRepository.observeProgress(id)
    ) { document, progress -> ReaderContent(document, progress) }
}
