package com.noloxtreme.tts.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptPolicyTest {
    @Test
    fun `first prompt is shown on twentieth library landing`() {
        var progress = ReviewPromptProgress()

        repeat(19) {
            val result = ReviewPromptPolicy.recordLibraryLanding(progress)
            progress = result.progress
            assertFalse(result.shouldPrompt)
        }

        val twentiethLanding = ReviewPromptPolicy.recordLibraryLanding(progress)

        assertTrue(twentiethLanding.shouldPrompt)
        assertEquals(20, twentiethLanding.progress.libraryLandingCount)
    }

    @Test
    fun `later defers the next prompt by five library landings`() {
        val prompted = ReviewPromptProgress(libraryLandingCount = 20)
        var progress = ReviewPromptPolicy.remindLater(prompted)

        assertEquals(25, progress.nextPromptLanding)
        repeat(4) {
            val result = ReviewPromptPolicy.recordLibraryLanding(progress)
            progress = result.progress
            assertFalse(result.shouldPrompt)
        }

        val fifthLanding = ReviewPromptPolicy.recordLibraryLanding(progress)

        assertTrue(fifthLanding.shouldPrompt)
        assertEquals(25, fifthLanding.progress.libraryLandingCount)
    }

    @Test
    fun `completed prompt is never shown again`() {
        var progress = ReviewPromptPolicy.complete(ReviewPromptProgress(libraryLandingCount = 20))

        repeat(100) {
            val result = ReviewPromptPolicy.recordLibraryLanding(progress)
            progress = result.progress
            assertFalse(result.shouldPrompt)
        }

        assertTrue(progress.completed)
    }
}
