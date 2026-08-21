package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.EpubRun
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XhtmlBlockExtractorTest {

    private fun blocks(bodyHtml: String, basePath: String = "text"): List<EpubBlock> {
        val body = Jsoup.parse("<body>$bodyHtml</body>").body()
        return XhtmlBlockExtractor.extract(body, basePath)
    }

    private fun text(block: EpubBlock): String = when (block) {
        is EpubBlock.Heading -> block.runs.joinToString("") { it.text }
        is EpubBlock.Paragraph -> block.runs.joinToString("") { it.text }
        is EpubBlock.Quote -> block.runs.joinToString("") { it.text }
        is EpubBlock.ListItem -> block.runs.joinToString("") { it.text }
        is EpubBlock.Image -> "img:" + block.resourcePath
        EpubBlock.Divider -> "hr"
    }

    @Test
    fun headingsAndParagraphsKeepDocumentOrder() {
        val result = blocks(
            """
            <h1>Chapter One</h1>
            <p>First.</p>
            <div><h2>Nested Section</h2><p>Second.</p></div>
            """.trimIndent()
        )

        assertEquals(
            listOf("Chapter One", "First.", "Nested Section", "Second."),
            result.map(::text)
        )
        assertEquals(listOf(1, 0, 2, 0), result.map { (it as? EpubBlock.Heading)?.level ?: 0 })
    }

    @Test
    fun boldAndItalicBecomeStyledRuns() {
        val paragraph = blocks("<p>Plain <b>b</b> and <em>e</em> end.</p>")
            .filterIsInstance<EpubBlock.Paragraph>()
            .single()

        assertEquals(5, paragraph.runs.size)
        assertEquals(EpubRun("Plain ", false, false), paragraph.runs[0])
        assertEquals(EpubRun("b", true, false), paragraph.runs[1])
        assertEquals(EpubRun(" and ", false, false), paragraph.runs[2])
        assertEquals(EpubRun("e", false, true), paragraph.runs[3])
        assertEquals(EpubRun(" end.", false, false), paragraph.runs[4])
    }

    @Test
    fun nestedInlineStylesCombine() {
        val paragraph = blocks("<p><strong><em>both</em></strong></p>")
            .filterIsInstance<EpubBlock.Paragraph>()
            .single()

        assertEquals(listOf(EpubRun("both", true, true)), paragraph.runs)
    }

    @Test
    fun inlineImageSplitsSurroundingText() {
        val result = blocks(
            """<p>Before <img src="pic.png" alt="A picture"/> after.</p>""",
            basePath = "text"
        )

        assertEquals(
            listOf(
                EpubBlock.Paragraph(listOf(EpubRun("Before ", false, false))),
                EpubBlock.Image("text/pic.png", "A picture"),
                EpubBlock.Paragraph(listOf(EpubRun(" after.", false, false)))
            ),
            result
        )
    }

    @Test
    fun relativeImagePathsResolveAcrossDirectories() {
        val result = blocks("""<img src="../images/cover.jpg"/>""", basePath = "text/ch1")
            .filterIsInstance<EpubBlock.Image>()

        assertEquals("text/images/cover.jpg", result.single().resourcePath)
    }

    @Test
    fun remoteImagesAreDropped() {
        val result = blocks("""<p>a</p><img src="https://example.com/x.png"/><p>b</p>""")

        assertTrue(result.none { it is EpubBlock.Image })
        assertEquals(listOf("a", "b"), result.map(::text))
    }

    @Test
    fun traversalImagePathsAreDropped() {
        val result = blocks("""<img src="../../secret.png"/>""", basePath = "text")

        assertTrue(result.isEmpty())
    }

    @Test
    fun listsReceiveMarkersInOrder() {
        val items = blocks(
            """
            <ol><li>One</li><li>Two<ul><li>Nested</li></ul></li></ol>
            <ul><li>Bullet</li></ul>
            """.trimIndent()
        ).filterIsInstance<EpubBlock.ListItem>()

        assertEquals(
            listOf("One", "Two", "Nested", "Bullet"),
            items.map { it.runs.joinToString("") { run -> run.text } }
        )
        assertEquals(listOf("1.", "2.", "\u2022", "\u2022"), items.map { it.marker })
        assertEquals(listOf(true, true, false, false), items.map { it.ordered })
    }

    @Test
    fun quotesDividersAndTablesFlatten() {
        val result = blocks(
            """
            <blockquote>Wise words.</blockquote>
            <hr/>
            <table>
              <tr><th>H1</th><td>D1</td></tr>
              <tr><td>D2</td><td>D3</td></tr>
            </table>
            """.trimIndent()
        )

        assertEquals(
            listOf("Wise words.", "hr", "H1", "D1", "D2", "D3"),
            result.map(::text)
        )
        assertTrue(result.filterIsInstance<EpubBlock.Quote>().single().runs.all { !it.italic })
    }

    @Test
    fun scriptStyleNavAndFormsAreSkipped() {
        val result = blocks(
            """
            <script>alert(1)</script>
            <style>p{}</style>
            <nav><ol><li>Hidden</li></ol></nav>
            <form><input/><button>Skip</button></form>
            <p>Visible.</p>
            """.trimIndent()
        )

        assertEquals(listOf("Visible."), result.map(::text))
    }

    @Test
    fun blankBlocksAreDropped() {
        val result = blocks("<p>   </p><p></p><p>Real.</p><h1>  </h1>")

        assertEquals(listOf("Real."), result.map(::text))
    }

    @Test
    fun whitespaceCollapsesInsideRuns() {
        val paragraph = blocks("<p>Spaced\n   out\t\twords.</p>")
            .filterIsInstance<EpubBlock.Paragraph>()
            .single()

        assertEquals(listOf(EpubRun("Spaced out words.", false, false)), paragraph.runs)
    }

    @Test
    fun imageWithoutAltHasNullDescription() {
        val image = blocks("""<img src="x.png" alt="   "/>""")
            .filterIsInstance<EpubBlock.Image>()
            .single()

        assertNull(image.contentDescription)
    }

    @Test
    fun resolveResourcePathRejectsSchemes() {
        assertNull(resolveResourcePath("text", "data:image/png;base64,AAAA"))
        assertNull(resolveResourcePath("text", ""))
        assertEquals("text/pic.png", resolveResourcePath("text", "./pic.png"))
    }
}
