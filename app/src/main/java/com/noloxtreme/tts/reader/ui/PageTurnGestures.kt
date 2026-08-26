package com.noloxtreme.tts.reader.ui

/** The effect of a tap on the paged reader, by horizontal zone. */
enum class TapZone {
    PREVIOUS,
    NEXT,
    TOGGLE_CHROME
}

/** The page-turn direction a completed horizontal drag requests. */
enum class PageTurnDirection {
    FORWARD,
    BACKWARD
}

/** The default drag distance (as a fraction of the pane width) that turns a page. */
const val DRAG_PAGE_THRESHOLD_FRACTION = 0.15f

/**
 * Pure mapping of paged-reader gestures to actions, so the Kindle-style tap
 * zones and swipe thresholds stay unit-testable. The reader uses the default
 * third-of-the-screen tap zones and the 15% drag threshold.
 */
object PageTurnGestures {

    /** Left third turns back, right third turns forward, the middle toggles chrome. */
    fun tapZone(x: Float, width: Float): TapZone = when {
        width <= 0f -> TapZone.TOGGLE_CHROME
        x < width / 3f -> TapZone.PREVIOUS
        x > width * 2f / 3f -> TapZone.NEXT
        else -> TapZone.TOGGLE_CHROME
    }

    /**
     * The turn requested by a finished horizontal drag: dragging left (negative
     * distance) moves forward, dragging right moves back. Short drags return
     * null and the page snaps back in place.
     */
    fun dragDirection(
        distance: Float,
        width: Float,
        thresholdFraction: Float = DRAG_PAGE_THRESHOLD_FRACTION
    ): PageTurnDirection? {
        if (width <= 0f) return null
        val threshold = width * thresholdFraction
        return when {
            distance <= -threshold -> PageTurnDirection.FORWARD
            distance >= threshold -> PageTurnDirection.BACKWARD
            else -> null
        }
    }
}
