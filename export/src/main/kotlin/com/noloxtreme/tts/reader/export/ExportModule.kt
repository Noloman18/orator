package com.noloxtreme.tts.reader.export

import com.noloxtreme.tts.reader.domain.AudioExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {
    @Binds
    @Singleton
    abstract fun bindAudioExporter(implementation: AudioExportCoordinator): AudioExporter
}
