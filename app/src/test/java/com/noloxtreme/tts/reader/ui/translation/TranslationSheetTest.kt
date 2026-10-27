package com.noloxtreme.tts.reader.ui.translation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.ui.theme.OratorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
class TranslationSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val languages = listOf(
        TranslationLanguage("en", "English", "English"),
        TranslationLanguage("fr", "French", "Français")
    )

    @Test
    fun missingSourcePreventsTranslation() {
        show(TranslationUiState(checking = false, languages = languages, targetCode = "fr"))

        compose.onNodeWithText("Translate with Google").assertIsNotEnabled()
    }

    @Test
    fun unavailableDeviceProvidesVoiceSetupWithoutTranslationAction() {
        var openedSettings = false
        show(
            TranslationUiState(checking = false, unavailable = TranslationUnavailableReason.NEED_ANOTHER_VOICE),
            onOpenSpeechSettings = { openedSettings = true }
        )

        compose.onNodeWithText("Translate with Google").assertDoesNotExist()
        compose.onNodeWithText("Open device voice settings").performClick()
        assertEquals(true, openedSettings)
    }

    @Test
    fun downloadsUseWifiUnlessMobileDataIsChosen() {
        val downloadChoices = mutableListOf<Boolean>()
        show(
            TranslationUiState(checking = false, languages = languages, sourceCode = "en", targetCode = "fr"),
            onTranslate = { downloadChoices += it }
        )

        compose.onNodeWithText("Translate with Google").performClick()
        compose.onNodeWithText("Allow mobile data for this download").performScrollTo().performClick()
        compose.onNodeWithText("Translate with Google").performClick()

        assertEquals(listOf(false, true), downloadChoices)
    }

    @Test
    fun savedTranslationOpensItsOwnDocument() {
        val translatedId = DocumentId("translated-copy")
        var openedId: DocumentId? = null
        show(
            TranslationUiState(checking = false, phase = TranslationPhase.SUCCESS, translatedDocumentId = translatedId),
            onOpenDocument = { openedId = it }
        )

        compose.onNodeWithText("Open translated copy").performScrollTo().performClick()

        assertEquals(translatedId, openedId)
        compose.onNodeWithText("Translate with Google").assertDoesNotExist()
    }

    @Test
    fun largeTextKeepsTranslationActionVisibleOnSmallPhone() {
        show(
            TranslationUiState(checking = false, languages = languages, sourceCode = "en", targetCode = "fr"),
            fontScale = 1.6f
        )

        compose.onNodeWithText("Translate with Google").assertIsDisplayed()
    }

    @Test
    fun downloadErrorIsVisibleBesideRetryAction() {
        show(
            TranslationUiState(
                checking = false,
                languages = languages,
                sourceCode = "en",
                targetCode = "fr",
                error = TranslationError.WIFI_REQUIRED
            )
        )

        compose.onNodeWithText("Connect to Wi-Fi, or allow mobile data above, then try again.").assertIsDisplayed()
        compose.onNodeWithText("Translate with Google").assertIsDisplayed()
    }

    private fun show(
        state: TranslationUiState,
        onTranslate: (Boolean) -> Unit = {},
        onOpenSpeechSettings: () -> Unit = {},
        onOpenDocument: (DocumentId) -> Unit = {},
        fontScale: Float = 1f
    ) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                OratorTheme {
                    TranslationSheet(
                        state = state,
                        onSourceChange = {},
                        onTargetChange = {},
                        onTranslate = onTranslate,
                        onCancel = {},
                        onRefresh = {},
                        onOpenSpeechSettings = onOpenSpeechSettings,
                        onOpenDocument = onOpenDocument,
                        onDismiss = {}
                    )
                }
            }
        }
        compose.waitForIdle()
    }
}
