package com.noloxtreme.tts.reader.data

import android.content.Context
import androidx.room.Room
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.DocumentImporter
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.ProgressRepository
import com.noloxtreme.tts.reader.domain.SettingsRepository
import com.noloxtreme.tts.reader.domain.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(implementation: RoomDocumentRepository): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindContentRepository(implementation: RoomContentRepository): ContentRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(implementation: RoomProgressRepository): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(implementation: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindTimeProvider(implementation: SystemTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindDocumentImporter(implementation: SafDocumentImporter): DocumentImporter
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OratorDatabase =
        Room.databaseBuilder(context, OratorDatabase::class.java, "orator.db")
            .build()

    @Provides
    @IntoSet
    fun provideTxtParser(): BookParser = TxtParser()

    @Provides
    @IntoSet
    fun provideEpubParser(): BookParser = EpubParser()

    @Provides
    @Singleton
    fun provideBookParserRegistry(
        parsers: Set<@JvmSuppressWildcards BookParser>
    ): BookParserRegistry = BookParserRegistry(parsers)

    @Provides
    fun provideDocumentDao(database: OratorDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideSectionDao(database: OratorDatabase): SectionDao = database.sectionDao()

    @Provides
    fun provideContentDao(database: OratorDatabase): ContentDao = database.contentDao()

    @Provides
    fun provideProgressDao(database: OratorDatabase): ProgressDao = database.progressDao()
}
