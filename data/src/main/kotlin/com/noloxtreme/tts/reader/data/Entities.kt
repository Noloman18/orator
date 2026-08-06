package com.noloxtreme.tts.reader.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "documents",
    indices = [Index(value = ["sha256"], unique = true)]
)
data class DocumentEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val originalFileName: String,
    val mimeType: String,
    val sourceExtension: String,
    val privateSourcePath: String,
    val sha256: String,
    val languageTag: String?,
    val totalCharacterCount: Long,
    val sectionCount: Int,
    val importedAt: Long,
    val lastOpenedAt: Long?
)

@Entity(
    tableName = "sections",
    primaryKeys = ["documentId", "sectionIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId", "sectionIndex"])]
)
data class SectionEntity(
    val documentId: String,
    val sectionIndex: Int,
    val title: String?,
    val firstParagraphIndex: Int,
    val lastParagraphIndex: Int,
    val absoluteStart: Long,
    val absoluteEnd: Long
)

@Entity(
    tableName = "paragraphs",
    primaryKeys = ["documentId", "paragraphIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId", "sectionIndex", "paragraphIndex"]),
        Index(value = ["documentId", "absoluteStart", "absoluteEnd"])
    ]
)
data class ParagraphEntity(
    val documentId: String,
    val paragraphIndex: Int,
    val sectionIndex: Int,
    val text: String,
    val absoluteStart: Long,
    val absoluteEnd: Long
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ReadingProgressEntity(
    @androidx.room.PrimaryKey val documentId: String,
    val paragraphIndex: Int,
    val offsetInParagraph: Int,
    val absoluteOffset: Long,
    val updatedAt: Long,
    @ColumnInfo(name = "completed") val isCompleted: Boolean
)
