package com.noloxtreme.tts.reader.data

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.ProgressRepository
import com.noloxtreme.tts.reader.domain.ReadingProgress
import com.noloxtreme.tts.reader.domain.Section
import com.noloxtreme.tts.reader.domain.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.BreakIterator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomDocumentRepository @Inject constructor(
    private val database: OratorDatabase,
    @ApplicationContext private val context: Context
) : DocumentRepository {
    override fun observeLibrary(): Flow<List<Document>> =
        database.documentDao().observeAll().map { rows -> rows.map(DocumentEntity::toDomain) }

    override fun observeDocument(id: DocumentId): Flow<Document?> =
        database.documentDao().observeById(id.value).map { it?.toDomain() }

    override suspend fun getDocument(id: DocumentId): Document? =
        database.documentDao().getById(id.value)?.toDomain()

    override suspend fun updateLastOpened(id: DocumentId, epochMillis: Long) {
        database.documentDao().updateLastOpened(id.value, epochMillis)
    }

    override suspend fun deleteDocument(id: DocumentId) {
        val documentsRoot = context.filesDir.resolve("documents").canonicalFile
        val sourceDirectory = documentsRoot.resolve(id.value).canonicalFile
        require(sourceDirectory.parentFile == documentsRoot) { "Invalid document path" }
        val tombstone = documentsRoot.resolve(".delete-${id.value}").canonicalFile
        if (sourceDirectory.exists()) {
            check(sourceDirectory.renameTo(tombstone)) { "Unable to stage document deletion" }
        }
        try {
            database.withTransaction {
                database.documentDao().deleteById(id.value)
            }
            tombstone.deleteRecursively()
        } catch (error: Throwable) {
            if (tombstone.exists()) tombstone.renameTo(sourceDirectory)
            throw error
        }
    }
}

@Singleton
class RoomContentRepository @Inject constructor(
    private val contentDao: ContentDao,
    private val sectionDao: SectionDao
) : ContentRepository {
    override fun pagedParagraphs(
        id: DocumentId,
        initialParagraphIndex: Int
    ): Flow<PagingData<Paragraph>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            initialLoadSize = 60,
            prefetchDistance = 10,
            enablePlaceholders = true
        ),
        initialKey = initialParagraphIndex.coerceAtLeast(0),
        pagingSourceFactory = { contentDao.pagingSource(id.value) }
    ).flow.map { data -> data.map(ParagraphEntity::toDomain) }

    override suspend fun paragraph(id: DocumentId, index: Int): Paragraph? =
        contentDao.getParagraph(id.value, index)?.toDomain()

    override suspend fun paragraphContaining(
        id: DocumentId,
        absoluteOffset: Long
    ): Paragraph? = contentDao.findParagraphContaining(id.value, absoluteOffset)?.toDomain()

    override suspend fun section(id: DocumentId, index: Int): Section? =
        sectionDao.get(id.value, index)?.toDomain()

    override suspend fun sentenceBefore(
        id: DocumentId,
        position: DocumentPosition
    ): DocumentPosition {
        val current = paragraph(id, position.paragraphIndex) ?: return position
        val starts = sentenceStarts(current.text)
        val previous = starts.lastOrNull { it < position.offsetInParagraph }
        if (previous != null) return current.positionAt(previous)
        val priorParagraph = paragraph(id, position.paragraphIndex - 1)
            ?: return current.positionAt(0)
        val priorStarts = sentenceStarts(priorParagraph.text)
        return priorParagraph.positionAt(priorStarts.lastOrNull() ?: 0)
    }

    override suspend fun sentenceAfter(
        id: DocumentId,
        position: DocumentPosition
    ): DocumentPosition {
        val current = paragraph(id, position.paragraphIndex) ?: return position
        val starts = sentenceStarts(current.text)
        val next = starts.firstOrNull { it > position.offsetInParagraph }
        if (next != null) return current.positionAt(next)
        val nextParagraph = paragraph(id, position.paragraphIndex + 1)
            ?: return DocumentPosition(
                current.paragraphIndex,
                current.text.length,
                current.absoluteEnd
            )
        return nextParagraph.positionAt(0)
    }

    private fun sentenceStarts(text: String): List<Int> {
        val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
        iterator.setText(text)
        val starts = buildList {
            var start = iterator.first()
            while (start != BreakIterator.DONE) {
                add(start)
                start = iterator.next()
            }
        }.filter { it < text.length }
        return if (starts.isEmpty()) listOf(0) else starts
    }
}

@Singleton
class RoomProgressRepository @Inject constructor(
    private val dao: ProgressDao,
    private val contentDao: ContentDao
) : ProgressRepository {
    override fun observeProgress(id: DocumentId): Flow<ReadingProgress?> =
        dao.observe(id.value).map { it?.toDomain() }

    override suspend fun getProgress(id: DocumentId): ReadingProgress? =
        dao.get(id.value)?.toDomain()

    override suspend fun saveProgress(progress: ReadingProgress) {
        validateProgress(progress)
        dao.upsert(progress.toEntity())
    }

    private suspend fun validateProgress(progress: ReadingProgress) {
        val paragraph = contentDao.getParagraph(
            progress.documentId.value,
            progress.position.paragraphIndex
        ) ?: throw IllegalArgumentException(
            "Progress references missing paragraph ${progress.position.paragraphIndex}"
        )
        require(progress.position.offsetInParagraph in 0..paragraph.text.length) {
            "Progress offset ${progress.position.offsetInParagraph} is outside paragraph bounds"
        }
        require(progress.position.absoluteOffset ==
            paragraph.absoluteStart + progress.position.offsetInParagraph
        ) {
            "Progress absolute offset is inconsistent with its paragraph"
        }
        if (progress.completed) {
            val last = contentDao.lastParagraph(progress.documentId.value)
                ?: throw IllegalArgumentException("Completed progress requires paragraphs")
            require(
                last.paragraphIndex == progress.position.paragraphIndex &&
                    progress.position.offsetInParagraph == last.text.length
            ) {
                "Completed progress must point at the end of the final paragraph"
            }
        }
    }
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

private fun DocumentEntity.toDomain() = Document(
    id = DocumentId(id),
    title = title,
    originalFileName = originalFileName,
    mimeType = mimeType,
    sha256 = sha256,
    languageTag = languageTag,
    totalCharacterCount = totalCharacterCount,
    sectionCount = sectionCount,
    importedAtEpochMillis = importedAt,
    lastOpenedAtEpochMillis = lastOpenedAt
)

private fun SectionEntity.toDomain() = Section(
    documentId = DocumentId(documentId),
    sectionIndex = sectionIndex,
    title = title,
    firstParagraphIndex = firstParagraphIndex,
    lastParagraphIndex = lastParagraphIndex,
    absoluteStart = absoluteStart,
    absoluteEnd = absoluteEnd
)

private fun ParagraphEntity.toDomain() = Paragraph(
    documentId = DocumentId(documentId),
    paragraphIndex = paragraphIndex,
    sectionIndex = sectionIndex,
    text = text,
    absoluteStart = absoluteStart,
    absoluteEnd = absoluteEnd
)

private fun ReadingProgressEntity.toDomain() = ReadingProgress(
    documentId = DocumentId(documentId),
    position = DocumentPosition(paragraphIndex, offsetInParagraph, absoluteOffset),
    updatedAtEpochMillis = updatedAt,
    completed = isCompleted
)

private fun ReadingProgress.toEntity() = ReadingProgressEntity(
    documentId = documentId.value,
    paragraphIndex = position.paragraphIndex,
    offsetInParagraph = position.offsetInParagraph,
    absoluteOffset = position.absoluteOffset,
    updatedAt = updatedAtEpochMillis,
    isCompleted = completed
)

private fun Paragraph.positionAt(offset: Int): DocumentPosition =
    DocumentPosition(paragraphIndex, offset.coerceIn(0, text.length), absoluteStart + offset)
