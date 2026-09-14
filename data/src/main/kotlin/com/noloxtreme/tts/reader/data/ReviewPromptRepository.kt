package com.noloxtreme.tts.reader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.noloxtreme.tts.reader.domain.ReviewPromptPolicy
import com.noloxtreme.tts.reader.domain.ReviewPromptProgress
import com.noloxtreme.tts.reader.domain.ReviewPromptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reviewPromptDataStore by preferencesDataStore(name = "orator_review_prompt")

@Singleton
class DataStoreReviewPromptRepository @Inject constructor(
    @ApplicationContext context: Context
) : ReviewPromptRepository {
    private val dataStore = context.reviewPromptDataStore

    override suspend fun recordLibraryLanding(): Boolean {
        var shouldPrompt = false
        dataStore.edit { preferences ->
            val result = ReviewPromptPolicy.recordLibraryLanding(preferences.toReviewPromptProgress())
            preferences.save(result.progress)
            shouldPrompt = result.shouldPrompt
        }
        return shouldPrompt
    }

    override suspend fun remindLater() {
        dataStore.edit { preferences ->
            val progress = preferences.toReviewPromptProgress()
            if (!progress.completed) preferences.save(ReviewPromptPolicy.remindLater(progress))
        }
    }

    override suspend fun complete() {
        dataStore.edit { preferences ->
            preferences.save(ReviewPromptPolicy.complete(preferences.toReviewPromptProgress()))
        }
    }
}

private val LIBRARY_LANDING_COUNT = intPreferencesKey("library_landing_count")
private val NEXT_PROMPT_LANDING = intPreferencesKey("next_review_prompt_landing")
private val REVIEW_PROMPT_COMPLETED = booleanPreferencesKey("review_prompt_completed")

private fun androidx.datastore.preferences.core.Preferences.toReviewPromptProgress(): ReviewPromptProgress =
    ReviewPromptProgress(
        libraryLandingCount = (get(LIBRARY_LANDING_COUNT) ?: 0).coerceAtLeast(0),
        nextPromptLanding = (get(NEXT_PROMPT_LANDING) ?: ReviewPromptPolicy.FIRST_PROMPT_LANDING)
            .coerceAtLeast(ReviewPromptPolicy.FIRST_PROMPT_LANDING),
        completed = get(REVIEW_PROMPT_COMPLETED) ?: false
    )

private fun androidx.datastore.preferences.core.MutablePreferences.save(progress: ReviewPromptProgress) {
    this[LIBRARY_LANDING_COUNT] = progress.libraryLandingCount
    this[NEXT_PROMPT_LANDING] = progress.nextPromptLanding
    this[REVIEW_PROMPT_COMPLETED] = progress.completed
}
