package com.noloxtreme.tts.reader.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.EpubRun
import com.noloxtreme.tts.reader.domain.EpubSpineContent
import com.noloxtreme.tts.reader.domain.LineHeightPreference
import com.noloxtreme.tts.reader.ui.theme.OratorTheme
import java.io.File
import java.io.FileOutputStream
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

/**
 * Renders the scrolling EPUB reader with Robolectric's native graphics and writes
 * a Play Store screenshot. Captures only when explicitly requested so regular
 * test runs stay side-effect free:
 * ./gradlew :app:testDebugUnitTest --tests "*ReaderScreenshotTest" -Porator.screenshot=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w411dp-h823dp-420dpi")
class ReaderScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureScrollingReaderScreenshot() {
        assumeTrue(
            "Set -Porator.screenshot=true to capture the reader screenshot",
            System.getProperty("orator.screenshot") == "true"
        )

        compose.setContent {
            OratorTheme(darkTheme = false, dynamicColor = false) {
                ReaderScreenshotShell()
            }
        }
        compose.waitForIdle()

        val window = compose.activity.window
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        // Compose's own captureToImage waits for a live draw traversal that the
        // Robolectric looper never performs while the test thread blocks, so
        // drive Robolectric's PixelCopy shadow directly and drain the looper.
        val result = AtomicReference(-1)
        compose.runOnUiThread {
            PixelCopy.request(
                window,
                bitmap,
                { code -> result.set(code) },
                Handler(Looper.getMainLooper())
            )
        }
        var attempts = 0
        while (result.get() == -1 && attempts < 100) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
            attempts += 1
        }
        assertEquals(PixelCopy.SUCCESS, result.get())

        val target = File(System.getProperty("user.dir"))
            .resolve("../playstore-docs/screenshots/phone/04-ebook-reader.jpg")
            .canonicalFile
        target.parentFile.mkdirs()
        FileOutputStream(target).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
    }

    /** Mirrors the reader screen chrome so the screenshot shows the real layout. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ReaderScreenshotShell() {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("The Time Machine", maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Outlined.Toc, contentDescription = null)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Outlined.Close, contentDescription = null)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Outlined.Settings, contentDescription = null)
                        }
                    }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {}) { Icon(Icons.Outlined.FastRewind, contentDescription = null) }
                        IconButton(onClick = {}) { Icon(Icons.Outlined.SkipPrevious, contentDescription = null) }
                        IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Outlined.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {}) { Icon(Icons.Outlined.SkipNext, contentDescription = null) }
                        IconButton(onClick = {}) { Icon(Icons.Outlined.FastForward, contentDescription = null) }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Book progress", style = MaterialTheme.typography.labelLarge)
                        Text("41%", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(value = 0.41f, onValueChange = {}, modifier = Modifier.fillMaxWidth())
                }
                EpubReaderPane(
                    spineContent = EpubSpineContent(
                        spineIndex = 1,
                        title = "Chapter One",
                        blocks = sampleBlocks()
                    ),
                    unavailable = false,
                    fontSizeSp = 20,
                    lineHeight = LineHeightPreference.COMFORTABLE,
                    jumpTarget = null,
                    onJumpTargetResolved = {},
                    onVisiblePositionChanged = { _, _ -> },
                    onRetry = {},
                    onToggleChrome = {},
                    loadImageBytes = { null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    private fun sampleBlocks(): List<EpubBlock> = buildList {
        add(EpubBlock.Heading(1, listOf(EpubRun("Chapter One"))))
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "The Time Traveller (for so it will be convenient to speak of him) was " +
                            "expounding a recondite matter to us. His grey eyes shone and twinkled, " +
                            "and his usually pale face was flushed and animated. The fire burned " +
                            "brightly, and the soft radiance of the incandescent lights in the " +
                            "lilies of silver caught the bubbles that flashed and passed in our " +
                            "glasses. Our chairs, being his patents, embraced and caressed us " +
                            "rather than submitted to be sat upon."
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"You must follow me carefully. I shall have to controvert one or two " +
                            "ideas that are almost universally accepted. The geometry, for " +
                            "instance, they taught you at school is founded on a misconception.\""
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"Is not that rather a large thing to expect us to begin upon?\" said " +
                            "Filby, an argumentative person with red hair."
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"I do not mean to ask you to accept anything without reasonable ground " +
                            "for it. You will soon admit as much as I need from you. You know of " +
                            "course that a mathematical line, a line of thickness nil, has no real " +
                            "existence. They taught you that? Neither has a mathematical plane. " +
                            "These things are mere abstractions.\""
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"That is all right,\" said the Psychologist."
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"Nor, having only length, breadth, and thickness, can a cube have a real " +
                            "existence.\""
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"There I object,\" said Filby. \"Of course a solid body may exist. All " +
                            "real things—\""
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"So most people think. But wait a moment. Can an instantaneous cube " +
                            "exist?\""
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"Don't follow you,\" said Filby."
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"Can a cube that does not last for any time at all, have a real " +
                            "existence?\""
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "Filby became pensive. \"Clearly,\" the Time Traveller proceeded, \"any " +
                            "real body must have extension in four directions: it must have Length, " +
                            "Breadth, Thickness, and—Duration. But through a natural infirmity of " +
                            "the flesh, which I will explain to you in a moment, we incline to " +
                            "overlook this fact. There are really four dimensions, three which we " +
                            "call the three planes of Space, and a fourth, Time. There is, however, " +
                            "a tendency to draw an unreal distinction between the former three " +
                            "dimensions and the latter, because it happens that our consciousness " +
                            "moves intermittently in one direction along the latter from the " +
                            "beginning to the end of our lives.\""
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"That,\" said a very young man, making spasmodic efforts to relight his " +
                            "cigar over the lamp; \"that . . . very clear indeed.\""
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"Now, it is very remarkable that this is so extensively overlooked,\" " +
                            "continued the Time Traveller, with a slight accession of cheerfulness. " +
                            "\"Really this is what is meant by the Fourth Dimension, though some " +
                            "people who talk about the Fourth Dimension do not know they mean it. " +
                            "It is only another way of looking at Time. There is no difference " +
                            "between Time and any of the three dimensions of Space except that our " +
                            "consciousness moves along it. But some foolish people have got hold of " +
                            "the wrong side of that idea. You have all heard what they have to say " +
                            "about this Fourth Dimension?\""
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"I have not,\" said the Provincial Mayor."
                    )
                )
            )
        )
        add(
            EpubBlock.Paragraph(
                listOf(
                    EpubRun(
                        "\"It is simply this. That Space, as our mathematicians have it, is spoken " +
                            "of as having three dimensions, which one may call Length, Breadth, and " +
                            "Thickness, and is always definable by reference to three planes, each " +
                            "at right angles to the others. But some philosophical people have been " +
                            "asking why three dimensions particularly—why not another direction at " +
                            "right angles to the other three?—and have even tried to construct a " +
                            "Four-Dimensional geometry. Professor Simon Newcomb was expounding this " +
                            "to the New York Mathematical Society only a month or so ago. You know " +
                            "how on a flat surface, which has only two dimensions, we can represent " +
                            "a figure of a three-dimensional solid, and similarly they think that " +
                            "by models of three dimensions they could represent one of four—if they " +
                            "could master the perspective of the thing. See?\""
                    )
                )
            )
        )
    }
}
