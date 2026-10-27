package com.noloxtreme.tts.reader.ui.translation

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.noloxtreme.tts.reader.ui.theme.OratorTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Optional native-graphics previews; no published artwork is overwritten. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w411dp-h823dp-420dpi")
class TranslationScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureLightTranslationSheet() = capture(darkTheme = false)

    @Test
    fun captureDarkTranslationSheet() = capture(darkTheme = true)

    private fun capture(darkTheme: Boolean) {
        assumeTrue(System.getProperty("orator.screenshot") == "true")
        compose.setContent {
            OratorTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                TranslationSheet(
                    state = TranslationUiState(
                        checking = false,
                        languages = listOf(
                            TranslationLanguage("en", "English", "English"),
                            TranslationLanguage("fr", "French", "Français"),
                            TranslationLanguage("af", "Afrikaans", "Afrikaans")
                        ),
                        sourceCode = "en",
                        targetCode = "fr"
                    ),
                    onSourceChange = {},
                    onTargetChange = {},
                    onTranslate = {},
                    onCancel = {},
                    onRefresh = {},
                    onOpenSpeechSettings = {},
                    onOpenDocument = {},
                    onDismiss = {}
                )
            }
        }
        compose.waitForIdle()
        // A modal sheet has its own dialog Window; capturing the Activity would show
        // only the dimmed reader underneath it.
        val window = requireNotNull(ShadowDialog.getLatestDialog().window)
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val result = AtomicReference(-1)
        compose.runOnUiThread {
            PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper()))
        }
        repeat(100) {
            if (result.get() == -1) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
        }
        assertEquals(PixelCopy.SUCCESS, result.get())
        File("/tmp/orator-translation-${if (darkTheme) "dark" else "light"}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
