package com.noloxtreme.tts.reader.playback

import com.noloxtreme.tts.reader.domain.NarrationController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun bindSpeechEngine(implementation: AndroidTtsEngine): SpeechEngine

    @Binds
    @Singleton
    abstract fun bindNarrationController(
        implementation: NarrationCoordinator
    ): NarrationController
}
