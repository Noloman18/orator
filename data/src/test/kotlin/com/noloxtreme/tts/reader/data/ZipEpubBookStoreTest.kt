package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.EpubRun
import com.noloxtreme.tts.reader.domain.EpubTocEntry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipEpubBookStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val factory = TestEpubFactory(temporaryFolder)

    private class FixedLocator(private val file: File?) : EpubSourceLocator {
        override suspend fun sourceFile(id: DocumentId): File? = file
    }

    private fun store(file: File?): ZipEpubBookStore = ZipEpubBookStore(FixedLocator(file))

    private fun navEpub(): File {
        val nav = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <body>
              <nav epub:type="toc"><ol>
                <li><a href="chapter1.xhtml">Part One</a>
                  <ol><li><a href="chapter2.xhtml">Section 1.1</a></li></ol>
                </li>
                <li><a href="chapter3.xhtml">Part Two</a></li>
                <li><a href="unlisted.xhtml">Not In Spine</a></li>
              </ol></nav>
            </body>
            </html>
        """.trim()
        return factory.buildEpub(
            "mimetype" to "application/epub+zip",
            factory.containerXml(),
            factory.opf3(
                manifest = """
                    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                """.trimIndent(),
                spineRefs = listOf("c1", "c2", "c3", "nav")
            ),
            "OEBPS/nav.xhtml" to nav,
            "OEBPS/chapter1.xhtml" to factory.xhtml("<h1>Part One</h1><p>Hello.</p>"),
            "OEBPS/chapter2.xhtml" to factory.xhtml("<h1>Section 1.1</h1><p>Deep.</p>"),
            "OEBPS/chapter3.xhtml" to factory.xhtml("<h1>Part Two</h1><p>Bye.</p>")
        )
    }

    private fun ncxEpub(): File {
        val ncx = """
            <?xml version="1.0"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <head/>
              <docTitle><text>Ncx Book</text></docTitle>
              <navMap>
                <navPoint id="n1" playOrder="1">
                  <navLabel><text>First</text></navLabel>
                  <content src="text/chapter1.xhtml"/>
                  <navPoint id="n2" playOrder="2">
                    <navLabel><text>Sub</text></navLabel>
                    <content src="chapter2.xhtml"/>
                  </navPoint>
                </navPoint>
                <navPoint id="n3" playOrder="3">
                  <navLabel><text>Second</text></navLabel>
                  <content src="chapter3.xhtml#frag"/>
                </navPoint>
              </navMap>
            </ncx>
        """.trim()
        return factory.buildEpub(
            "mimetype" to "application/epub+zip",
            factory.containerXml(),
            factory.opf3(
                manifest = """
                    <item id="c1" href="text/chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                """.trimIndent(),
                spineRefs = listOf("c1", "c2", "c3"),
                spineTocAttribute = "ncx"
            ),
            "OEBPS/toc.ncx" to ncx,
            "OEBPS/text/chapter1.xhtml" to factory.xhtml("<p>One.</p>"),
            "OEBPS/chapter2.xhtml" to factory.xhtml("<p>Two.</p>"),
            "OEBPS/chapter3.xhtml" to factory.xhtml("<p>Three.</p>")
        )
    }

    @Test
    fun tocComesFromNestedEpub3NavWithSpineIndexes() = runBlocking {
        val file = navEpub()

        val toc = store(file).tableOfContents(DocumentId("doc"))

        assertEquals(
            listOf(
                EpubTocEntry("Part One", 0, 0),
                EpubTocEntry("Section 1.1", 1, 1),
                EpubTocEntry("Part Two", 2, 0)
            ),
            toc
        )
    }

    @Test
    fun tocFallsBackToNcxWhenNavIsAbsent() = runBlocking {
        val file = ncxEpub()

        val toc = store(file).tableOfContents(DocumentId("doc"))

        assertEquals(
            listOf(
                EpubTocEntry("First", 0, 0),
                EpubTocEntry("Sub", 1, 1),
                EpubTocEntry("Second", 2, 0)
            ),
            toc
        )
    }

    @Test
    fun spineContentExtractsBlocksInOrderWithImages() = runBlocking {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val file = factory.buildEpub(
            "mimetype" to "application/epub+zip",
            factory.containerXml(),
            factory.opf3(
                manifest = """
                    <item id="c1" href="text/chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="img" href="images/pic.png" media-type="image/png"/>
                """.trimIndent(),
                spineRefs = listOf("c1")
            ),
            "OEBPS/text/chapter1.xhtml" to factory.xhtml(
                """
                <h1>Chapter</h1>
                <p>Lead <em>styled</em> text.</p>
                <img src="../images/pic.png" alt="Cover"/>
                <p>Tail.</p>
                """.trimIndent()
            ),
            "OEBPS/images/pic.png" to pngBytes
        )

        val content = store(file).spineContent(DocumentId("doc"), 0)

        assertTrue(content != null)
        assertEquals("Chapter", content!!.title)
        assertEquals(0, content.spineIndex)
        assertEquals(
            listOf<EpubBlock>(
                EpubBlock.Heading(1, listOf(EpubRun("Chapter"))),
                EpubBlock.Paragraph(
                    listOf(EpubRun("Lead ", false, false), EpubRun("styled", false, true), EpubRun(" text.", false, false))
                ),
                EpubBlock.Image("OEBPS/images/pic.png", "Cover"),
                EpubBlock.Paragraph(listOf(EpubRun("Tail.", false, false)))
            ),
            content.blocks
        )
        val loaded = store(file).imageResource(DocumentId("doc"), "OEBPS/images/pic.png")
        assertEquals(pngBytes.toList(), loaded!!.toList())
    }

    @Test
    fun spineContentOutOfRangeReturnsNull() = runBlocking {
        val file = navEpub()

        assertNull(store(file).spineContent(DocumentId("doc"), -1))
        assertNull(store(file).spineContent(DocumentId("doc"), 99))
    }

    @Test
    fun imageResourceRejectsTraversalAndUnknownPaths() = runBlocking {
        val file = navEpub()

        assertNull(store(file).imageResource(DocumentId("doc"), "../../etc/passwd"))
        assertNull(store(file).imageResource(DocumentId("doc"), "missing.png"))
    }

    @Test
    fun missingSourceYieldsEmptyResults() = runBlocking {
        val empty = store(null)

        assertTrue(empty.tableOfContents(DocumentId("doc")).isEmpty())
        assertNull(empty.spineContent(DocumentId("doc"), 0))
        assertNull(empty.imageResource(DocumentId("doc"), "x.png"))
        assertEquals(0, empty.spineCount(DocumentId("doc")))
    }

    @Test
    fun spineCountReflectsSpineSize() = runBlocking {
        assertEquals(4, store(navEpub()).spineCount(DocumentId("doc")))
    }
}
