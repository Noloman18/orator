package com.noloxtreme.tts.reader.playback

import com.noloxtreme.tts.reader.domain.NarrationController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides
    @Singleton
    fun provideCoordinatorDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    fun provideAudioFocusController(implementation: SpeechAudioFocus): AudioFocusController =
        implementation

    @Provides
    @Singleton
    fun provideNarrationEnvironment(implementation: AndroidNarrationEnvironment): NarrationEnvironment =
        implementation

    @Provides
    @Singleton
    fun bindSpeechEngine(implementation: AndroidTtsEngine): SpeechEngine = implementation

    @Provides
    @Singleton
    fun bindNarrationController(implementation: NarrationCoordinator): NarrationController =
        implementation
}
