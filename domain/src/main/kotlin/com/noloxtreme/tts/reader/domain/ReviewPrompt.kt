package com.noloxtreme.tts.reader.domain

/**
 * The persisted state used to decide when to ask a reader for a Play Store review.
 * A completed prompt is terminal: users who have reviewed or declined are never
 * prompted again.
 */
data class ReviewPromptProgress(
    val libraryLandingCount: Int = 0,
    val nextPromptLanding: Int = ReviewPromptPolicy.FIRST_PROMPT_LANDING,
    val completed: Boolean = false
)

data class LibraryLandingReviewPrompt(
    val progress: ReviewPromptProgress,
    val shouldPrompt: Boolean
)

object ReviewPromptPolicy {
    const val FIRST_PROMPT_LANDING = 20
    const val REMINDER_INTERVAL_LANDINGS = 5

    fun recordLibraryLanding(progress: ReviewPromptProgress): LibraryLandingReviewPrompt {
        val landingCount = progress.libraryLandingCount.coerceAtMost(Int.MAX_VALUE - 1) + 1
        val updated = progress.copy(libraryLandingCount = landingCount)
        return LibraryLandingReviewPrompt(
            progress = updated,
            shouldPrompt = !updated.completed && landingCount >= updated.nextPromptLanding
        )
    }

    fun remindLater(progress: ReviewPromptProgress): ReviewPromptProgress = progress.copy(
        nextPromptLanding = (progress.libraryLandingCount.toLong() + REMINDER_INTERVAL_LANDINGS)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    )

    fun complete(progress: ReviewPromptProgress): ReviewPromptProgress = progress.copy(completed = true)
}

interface ReviewPromptRepository {
    /** Records a library landing and returns whether this landing should show the prompt. */
    suspend fun recordLibraryLanding(): Boolean

    /** Defers the next prompt until five further library landings. */
    suspend fun remindLater()

    /** Permanently suppresses the prompt after a review request or a decline. */
    suspend fun complete()
}
