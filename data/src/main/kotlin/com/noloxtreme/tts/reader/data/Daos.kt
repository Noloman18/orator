package com.noloxtreme.tts.reader.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query(
        """
        SELECT * FROM documents
        ORDER BY
          CASE WHEN lastOpenedAt IS NULL THEN 1 ELSE 0 END,
          lastOpenedAt DESC,
          importedAt DESC
        """
    )
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: DocumentEntity)

    @Query(
        """
        UPDATE documents SET
          title = :title,
          languageTag = :languageTag,
          totalCharacterCount = :totalCharacterCount,
          sectionCount = :sectionCount
        WHERE id = :id
        """
    )
    suspend fun finalizeDocument(
        id: String,
        title: String,
        languageTag: String?,
        totalCharacterCount: Long,
        sectionCount: Int
    )

    @Query("UPDATE documents SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateLastOpened(id: String, timestamp: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SectionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(sections: List<SectionEntity>)

    @Query("SELECT * FROM sections WHERE documentId = :documentId ORDER BY sectionIndex")
    suspend fun allForDocument(documentId: String): List<SectionEntity>

    @Query(
        "SELECT * FROM sections WHERE documentId = :documentId AND sectionIndex = :sectionIndex LIMIT 1"
    )
    suspend fun get(documentId: String, sectionIndex: Int): SectionEntity?
}

@Dao
interface ContentDao {
    @Query(
        """
        SELECT * FROM paragraphs
        WHERE documentId = :documentId
        ORDER BY paragraphIndex
        """
    )
    fun pagingSource(documentId: String): PagingSource<Int, ParagraphEntity>

    @Query(
        """
        SELECT * FROM paragraphs
        WHERE documentId = :documentId AND paragraphIndex = :paragraphIndex
        LIMIT 1
        """
    )
    suspend fun getParagraph(documentId: String, paragraphIndex: Int): ParagraphEntity?

    @Query(
        """
        SELECT * FROM paragraphs
        WHERE documentId = :documentId
          AND absoluteStart <= :absoluteOffset
          AND absoluteEnd >= :absoluteOffset
        ORDER BY paragraphIndex
        LIMIT 1
        """
    )
    suspend fun findParagraphContaining(
        documentId: String,
        absoluteOffset: Long
    ): ParagraphEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(paragraphs: List<ParagraphEntity>)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM reading_progress WHERE documentId = :documentId LIMIT 1")
    fun observe(documentId: String): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE documentId = :documentId LIMIT 1")
    suspend fun get(documentId: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)
}
