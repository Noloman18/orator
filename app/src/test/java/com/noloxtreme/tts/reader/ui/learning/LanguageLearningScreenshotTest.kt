package com.noloxtreme.tts.reader.ui.learning

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.noloxtreme.tts.reader.ReaderControls
import com.noloxtreme.tts.reader.ReaderFooter
import com.noloxtreme.tts.reader.ReaderProgress
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import com.noloxtreme.tts.reader.domain.NarrationState
import com.noloxtreme.tts.reader.domain.SpokenRange
import com.noloxtreme.tts.reader.ui.NarrationTransport
import com.noloxtreme.tts.reader.ui.VisualReadingUnit
import com.noloxtreme.tts.reader.ui.theme.OratorTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose previews with the same space reserved for the existing reader chrome. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w411dp-h823dp-420dpi")
class LanguageLearningScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun portraitLight() = capture("light", dark = false)
    @Test fun portraitDark() = capture("dark", dark = true)
    @Test @Config(qualifiers = "w800dp-h360dp-420dpi")
    fun landscape() = capture("landscape", dark = false)

    @OptIn(ExperimentalMaterial3Api::class)
    private fun capture(name: String, dark: Boolean) {
        assumeTrue(System.getProperty("orator.screenshot") == "true")
        val passage = sampleLearningPassage()
        val offset = passage.translated.text.indexOf("les oiseaux")
        compose.setContent {
            OratorTheme(darkTheme = dark, dynamicColor = false) {
                val compact = compactLearningChrome()
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("The Quiet Garden · French", maxLines = 1) },
                            // Robolectric omits system bar insets; reserve a typical status bar.
                            windowInsets = WindowInsets(top = 24.dp),
                            navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                            actions = { IconButton(onClick = {}) { Icon(Icons.Outlined.MoreVert, "More actions") } }
                        )
                    },
                    bottomBar = {
                        ReaderFooter(translated = true, compact = compact) {
                            ReaderControls(
                                narration = NarrationState.Playing(DocumentId("translated"), DocumentPosition(0, offset, offset.toLong()), null),
                                playing = true, transport = NarrationTransport({}, {}, {}), reading = false,
                                speechRate = 1f, visualReadingPace = 1f, visualReadingUnit = VisualReadingUnit.WORD,
                                visualReadingCompleted = false, onToggleVisualUnit = {}, onPlay = {}
                            )
                        }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        val progress: @Composable () -> Unit = { ReaderProgress(24, compact, 0.24f, {}, {}, true) }
                        if (!compact) progress()
                        LanguageLearningPane(
                            LearningPassageState.Ready(passage), DocumentPosition(0, offset, offset.toLong()),
                            SpokenRange(0, offset, offset + "les oiseaux".length), 20, LineHeightPreference.COMFORTABLE,
                            0, {}, {}, Modifier.weight(1f), progressControls = progress.takeIf { compact }
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        listOf("learning_original_text", "learning_translation_text").forEach { tag ->
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            // Even with all reader controls present, each pane must fit two full lines.
            val minimum = with(compose.density) { 20.sp.toPx() * 1.55f * 2 }
            assertTrue("$name: $tag has too little reading space", node.boundsInRoot.height >= minimum)
            assertTrue(node.config[SemanticsProperties.Text].single().spanStyles.isNotEmpty())
        }
        val window = compose.activity.window
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
        File("/tmp/orator-learning-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
