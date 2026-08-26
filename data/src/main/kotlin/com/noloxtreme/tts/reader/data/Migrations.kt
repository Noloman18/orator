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
