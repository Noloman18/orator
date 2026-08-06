package com.noloxtreme.tts.reader.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [
        DocumentEntity::class,
        SectionEntity::class,
        ParagraphEntity::class,
        ReadingProgressEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class OratorDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun sectionDao(): SectionDao
    abstract fun contentDao(): ContentDao
    abstract fun progressDao(): ProgressDao
}

class DatabaseConverters {
    @TypeConverter
    fun booleanToInt(value: Boolean): Int = if (value) 1 else 0

    @TypeConverter
    fun intToBoolean(value: Int): Boolean = value != 0
}
