package com.noloxtreme.tts.reader.ui.learning

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.SpokenRange
import com.noloxtreme.tts.reader.ui.theme.OratorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
class LanguageLearningPaneTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = mutableStateOf<LearningPassageState>(LearningPassageState.Ready(sampleLearningPassage()))
    private val position = mutableStateOf(DocumentPosition(0, 0, 0))
    private val range = mutableStateOf<SpokenRange?>(SpokenRange(0, 0, 2))
    private val revision = mutableStateOf(0)

    @Test
    fun portraitShowsBothLanguagesAndExplainsApproximateTracking() {
        show()
        compose.onNodeWithTag("learning_vertical").assertIsDisplayed()
        compose.onNodeWithText("Original · English").assertIsDisplayed()
        compose.onNodeWithText("Translation · French").assertIsDisplayed()
        compose.onNodeWithContentDescription("About language learning").performClick()
        compose.onNodeWithText("The translation is read aloud", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun landscapePlacesThePanesBesideEachOther() {
        show()
        compose.onNodeWithTag("learning_horizontal").assertIsDisplayed()
        val original = compose.onNodeWithTag("learning_original_text").fetchSemanticsNode().boundsInRoot
        val translated = compose.onNodeWithTag("learning_translation_text").fetchSemanticsNode().boundsInRoot
        assertTrue(translated.left > original.right)
        assertEquals(original.top, translated.top, 1f)
    }

    @Test
    fun bothMarkersAdvanceAndTheTranslationKeepsItsExactSpokenRange() {
        show()
        val before = highlightedRange("learning_original_text")
        compose.runOnIdle {
            position.value = DocumentPosition(0, 170, 170)
            range.value = SpokenRange(0, 170, 177)
        }
        val after = highlightedRange("learning_original_text")
        assertTrue(after.start > before.start)
        assertEquals(LearningTextRange(170, 177), highlightedRange("learning_translation_text"))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun bothPanesScrollToKeepLateParagraphMarkersVisible() {
        show()
        val passage = sampleLearningPassage()
        val offset = passage.translated.text.indexOf("Elle sourit")
        compose.runOnIdle {
            position.value = DocumentPosition(0, offset, offset.toLong())
            range.value = SpokenRange(0, offset, offset + 4)
        }
        listOf("learning_original_text", "learning_translation_text").forEach { tag ->
            val layouts = mutableListOf<TextLayoutResult>()
            val node = compose.onNodeWithTag(tag)
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val semantics = node.fetchSemanticsNode()
            val scroll = semantics.config[SemanticsProperties.VerticalScrollAxisRange].value()
            val textLayout = layouts.single()
            val line = textLayout.getLineForOffset(highlightedRange(tag).start)
            assertTrue("$tag must scroll: ${textLayout.lineCount} lines, viewport ${semantics.boundsInRoot.height}", scroll > 0)
            assertTrue("$tag marker starts above the viewport", textLayout.getLineTop(line) >= scroll - 1)
            assertTrue("$tag marker ends below the viewport", textLayout.getLineBottom(line) <= scroll + semantics.boundsInRoot.height + 1)
        }
    }

    @Test
    fun advancingToTheNextPassageDoesNotFlashAnEmptyView() {
        show()
        val previous = text("learning_original_text")
        compose.runOnIdle {
            state.value = LearningPassageState.Loading
            position.value = DocumentPosition(1, 0, 1000)
            range.value = null
        }
        assertEquals(previous, text("learning_original_text"))
        compose.onNodeWithText("Loading both languages…").assertDoesNotExist()
        compose.runOnIdle { state.value = LearningPassageState.Ready(sampleLearningPassage(1)) }
        assertTrue(text("learning_original_text").startsWith("Next passage."))
    }

    @Test
    fun scrollingHoldsThePassageUntilFollowNarrationIsTapped() {
        show()
        compose.onNodeWithTag("learning_original_text").performTouchInput { swipeUp() }
        compose.onNodeWithText("Follow narration").assertIsDisplayed()
        val oldText = text("learning_original_text")
        compose.runOnIdle {
            state.value = LearningPassageState.Ready(sampleLearningPassage(1))
            position.value = DocumentPosition(1, 0, 1000)
            range.value = SpokenRange(1, 0, 4)
        }
        assertEquals(oldText, text("learning_original_text"))
        compose.onNodeWithText("Follow narration").performClick()
        assertTrue(text("learning_original_text").startsWith("Next passage."))
        compose.onNodeWithText("Follow narration").assertDoesNotExist()
    }

    @Test
    fun explicitTransportOrSeekResumesFollowingAfterManualScroll() {
        show()
        compose.onNodeWithTag("learning_translation_text").performTouchInput { swipeUp() }
        compose.onNodeWithText("Follow narration").assertIsDisplayed()
        compose.runOnIdle {
            state.value = LearningPassageState.Ready(sampleLearningPassage(2))
            position.value = DocumentPosition(2, 0, 2000)
            range.value = SpokenRange(2, 0, 4)
            revision.value++
        }
        compose.onNodeWithText("Follow narration").assertDoesNotExist()
        assertTrue(text("learning_original_text").startsWith("Next passage."))
    }

    @Test
    fun missingOriginalOffersAnExitAndNeverShowsAStalePair() {
        var closed = false
        show(onClose = { closed = true })
        compose.runOnIdle { state.value = LearningPassageState.OriginalMissing }
        compose.onNodeWithText("The original book is no longer in your library.").assertIsDisplayed()
        compose.onNodeWithTag("learning_original_text").assertDoesNotExist()
        compose.onNodeWithText("Back to narration").performClick()
        assertTrue(closed)
    }

    @Test
    fun largerFontKeepsBothPanesAndTheCloseControlAccessible() {
        var closed = false
        show(fontScale = 1.6f, onClose = { closed = true })
        compose.onNodeWithTag("learning_original_text").assertIsDisplayed()
        compose.onNodeWithTag("learning_translation_text").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close language learning").performClick()
        assertTrue(closed)
    }

    @Test
    fun failedLoadSupportsRetry() {
        var retried = false
        state.value = LearningPassageState.Failed
        show(onRetry = { retried = true })
        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    private fun show(fontScale: Float = 1f, onClose: () -> Unit = {}, onRetry: () -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OratorTheme {
                    // Equivalent to the room left by reader chrome on a small phone.
                    Surface(Modifier.height(440.dp)) {
                        LanguageLearningPane(
                            state.value, position.value, range.value, 20, LineHeightPreference.COMFORTABLE,
                            revision.value, onClose, onRetry, Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    private fun text(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].single().text

    private fun highlightedRange(tag: String): LearningTextRange {
        val highlight = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].single().spanStyles.single()
        return LearningTextRange(highlight.start, highlight.end)
    }
}

internal fun sampleLearningPassage(index: Int = 0): LearningPassage {
    val original = (if (index > 0) "Next passage. " else "") +
        "The morning light fell across the quiet garden. She opened her book and listened to the birds in the trees. " +
        "Every familiar word seemed to carry a new meaning, as though the world had gently changed while she slept. " +
        "Beyond the wall, the village was waking. A bicycle passed along the narrow lane, and somewhere a door opened. " +
        "She smiled, turned the page, and began again."
    val translated = (if (index > 0) "Passage suivant. " else "") +
        "La lumière du matin éclairait le jardin tranquille. Elle ouvrit son livre et écouta les oiseaux dans les arbres. " +
        "Chaque mot familier semblait porter un sens nouveau, comme si le monde avait doucement changé pendant son sommeil. " +
        "Au-delà du mur, le village se réveillait. Un vélo passa dans la ruelle et, quelque part, une porte s’ouvrit. " +
        "Elle sourit, tourna la page et recommença."
    return LearningPassage(
        Paragraph(DocumentId("original"), index, 0, original, 0, original.length.toLong()),
        Paragraph(DocumentId("translated"), index, 0, translated, 0, translated.length.toLong()),
        "en", "fr"
    )
}
