package com.noloxtreme.tts.reader.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 → 2: adds the reader_positions table that persists where the
 * visual read mode was left (spine item and packed page) per document.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reader_positions` (
                `documentId` TEXT NOT NULL,
                `spineIndex` INTEGER NOT NULL,
                `pageIndex` INTEGER NOT NULL,
                PRIMARY KEY(`documentId`),
                FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
        )
    }
}

/** Version 2 → 3: adds text and voice notes anchored to a document position. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `book_notes` (
                `id` TEXT NOT NULL,
                `documentId` TEXT NOT NULL,
                `paragraphIndex` INTEGER NOT NULL,
                `offsetInParagraph` INTEGER NOT NULL,
                `absoluteOffset` INTEGER NOT NULL,
                `text` TEXT,
                `voiceRelativePath` TEXT,
                `voiceDurationMillis` INTEGER,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_book_notes_documentId_createdAt` " +
                "ON `book_notes` (`documentId`, `createdAt`)"
        )
    }
}
