package com.noloxtreme.tts.reader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
abstract class RoomDatabaseTest {
    protected lateinit var database: OratorDatabase

    protected fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OratorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    protected fun closeDatabase() {
        database.close()
    }
}
