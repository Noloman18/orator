package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.ImportError
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val parser = EpubParser()

    private fun blocks(file: File): List<ParsedBlock> = runBlocking {
        val collected = mutableListOf<ParsedBlock>()
        parser.forEachBlock(file) { collected += it }
        collected
    }

    private fun buildEpub(vararg entries: Pair<String, String>): File {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
        return temporaryFolder.newFile("book.epub").apply { writeBytes(output.toByteArray()) }
    }

    private fun containerXml(opfPath: String = "OEBPS/content.opf"): Pair<String, String> =
        "META-INF/container.xml" to """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trim()

    private fun opf2(
        title: String = "Test Book",
        language: String = "en-US",
        spineRefs: List<String>,
        manifest: String
    ): Pair<String, String> = "OEBPS/content.opf" to """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="uid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$title</dc:title>
            <dc:language>$language</dc:language>
          </metadata>
          <manifest>
            $manifest
          </manifest>
          <spine>
            ${spineRefs.joinToString(" ") { "<itemref idref=\"$it\"/>" }}
          </spine>
        </package>
    """.trim()

    private fun xhtml(body: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head><title>Chapter</title></head>
        <body>$body</body>
        </html>
    """.trim()

    @Test
    fun epub2FollowsSpineOrderAndExtractsHeadingsAndParagraphs() {
        val chapter1 = xhtml("""
            <h1>First Heading</h1>
            <p>First paragraph.</p>
            <p>Second <b>paragraph</b>.</p>
            <script>var ignored = true;</script>
        """.trim())
        val chapter2 = xhtml("""
            <h1>Second Heading</h1>
            <p>Chapter two body.</p>
        """.trim())
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            containerXml(),
            opf2(
                spineRefs = listOf("c2", "c1"),
                manifest = """
                    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                """.trim()
            ),
            "OEBPS/chapter1.xhtml" to chapter1,
            "OEBPS/chapter2.xhtml" to chapter2
        )

        val metadata = parser.readMetadata(file, "filename.epub")
        val parsed = blocks(file)

        assertEquals("Test Book", metadata.title)
        assertEquals("application/epub+zip", metadata.mimeType)
        assertEquals("en-US", metadata.languageTag)
        assertEquals(
            listOf(
                "Second Heading", "Chapter two body.",
                "First Heading", "First paragraph.", "Second paragraph."
            ),
            parsed.map { it.text }
        )
        assertEquals(listOf(0, 0, 1, 1, 1), parsed.map { it.sectionIndex })
        assertEquals(
            listOf("Second Heading", "Second Heading", "First Heading", "First Heading", "First Heading"),
            parsed.map { it.sectionTitle }
        )
    }

    @Test
    fun epub3NavLabelIsUsedWhenSpineItemHasNoHeading() {
        val nav = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <body>
              <nav epub:type="toc"><ol>
                <li><a href="chapter1.xhtml">The First Chapter Label</a></li>
                <li><a href="chapter3.xhtml">The Third Chapter Label</a></li>
              </ol></nav>
            </body>
            </html>
        """.trim()
        val opf = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Epub Three</dc:title>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="c3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="c3"/>
                <itemref idref="c1"/>
              </spine>
            </package>
        """.trim()
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            containerXml(),
            "OEBPS/content.opf" to opf,
            "OEBPS/nav.xhtml" to nav,
            "OEBPS/chapter1.xhtml" to xhtml("<p>No heading here.</p>"),
            "OEBPS/chapter2.xhtml" to xhtml("<p>Nav target.</p>"),
            "OEBPS/chapter3.xhtml" to xhtml("<h1>Real Heading</h1><p>Third chapter.</p>")
        )

        val parsed = blocks(file)

        assertEquals(
            listOf("Real Heading", "Third chapter.", "No heading here."),
            parsed.map { it.text }
        )
        assertEquals(
            listOf("Real Heading", "Real Heading", "The First Chapter Label"),
            parsed.map { it.sectionTitle }
        )
    }

    @Test
    fun epub2GuideTocIsUsedAsLabelFallback() {
        val opf = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="uid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Guide Book</dc:title>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                <item id="toc" href="toc.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="c2"/>
              </spine>
              <guide>
                <reference type="toc" href="toc.xhtml"/>
              </guide>
            </package>
        """.trim()
        val toc = """
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
            <h1>Contents</h1>
            <ol><li><a href="chapter2.xhtml">Guide Label Two</a></li></ol>
            </body></html>
        """.trim()
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            containerXml(),
            "OEBPS/content.opf" to opf,
            "OEBPS/toc.xhtml" to toc,
            "OEBPS/chapter1.xhtml" to xhtml("<p>Ignore me.</p>"),
            "OEBPS/chapter2.xhtml" to xhtml("<p>Guide chapter.</p>")
        )

        val parsed = blocks(file)

        assertEquals("Guide Label Two", parsed.single().sectionTitle)
    }

    @Test
    fun encryptedEpubIsRejected() {
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            "META-INF/encryption.xml" to "<encryption xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"/>",
            containerXml(),
            opf2(spineRefs = listOf("c1"), manifest = "<item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"),
            "OEBPS/chapter1.xhtml" to xhtml("<p>Secret.</p>")
        )

        assertImportError(ImportError.EPUB_ENCRYPTED) { parser.readMetadata(file, "book.epub") }
    }

    @Test
    fun traversalHrefIsRejected() {
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            containerXml(),
            opf2(
                spineRefs = listOf("c1"),
                manifest = "<item id=\"c1\" href=\"../../outside.xhtml\" media-type=\"application/xhtml+xml\"/>"
            ),
            "OEBPS/chapter1.xhtml" to xhtml("<p>Body.</p>")
        )

        assertImportError(ImportError.MALFORMED_DOCUMENT) { parser.readMetadata(file, "book.epub") }
    }

    @Test
    fun emptyOrNonXhtmlSpineIsRejected() {
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            containerXml(),
            opf2(
                spineRefs = listOf("cover"),
                manifest = "<item id=\"cover\" href=\"cover.jpg\" media-type=\"image/jpeg\"/>"
            )
        )

        assertImportError(ImportError.NO_READABLE_TEXT) { parser.readMetadata(file, "book.epub") }
    }

    @Test
    fun blankContentYieldsNoBlocks() {
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            containerXml(),
            opf2(
                spineRefs = listOf("c1"),
                manifest = "<item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
            ),
            "OEBPS/chapter1.xhtml" to xhtml("<p>   </p><nav><ol><li>Hidden</li></ol></nav>")
        )

        assertTrue(blocks(file).isEmpty())
    }

    @Test
    fun malformedZipIsRejected() {
        val file = temporaryFolder.newFile("bad.epub").apply {
            writeBytes("this is not a zip file".toByteArray())
        }

        assertImportError(ImportError.MALFORMED_DOCUMENT) { parser.readMetadata(file, "bad.epub") }
        assertEquals(false, EpubParser.isEpub(file))
    }

    @Test
    fun zipWithoutMimetypeIsRejected() {
        val opf = opf2(
            spineRefs = listOf("c1"),
            manifest = "<item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
        )
        val file = buildEpub(
            containerXml(),
            opf,
            "OEBPS/chapter1.xhtml" to xhtml("<p>Body.</p>")
        )

        assertImportError(ImportError.MALFORMED_DOCUMENT) { parser.readMetadata(file, "book.epub") }
        assertEquals(false, EpubParser.isEpub(file))
    }

    @Test
    fun missingContainerIsRejected() {
        val opf = opf2(
            spineRefs = listOf("c1"),
            manifest = "<item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
        )
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            opf
        )

        assertImportError(ImportError.MALFORMED_DOCUMENT) { parser.readMetadata(file, "book.epub") }
    }

    @Test
    fun oversizedSpineEntryIsRejected() {
        val bigBody = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>" +
            "A".repeat(26 * 1024 * 1024) + "</p></body></html>"
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            containerXml(),
            opf2(
                spineRefs = listOf("c1"),
                manifest = "<item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
            ),
            "OEBPS/chapter1.xhtml" to bigBody
        )

        assertImportError(ImportError.EPUB_LIMIT_EXCEEDED) { blocks(file) }
    }

    @Test
    fun dublinCoreTitleFallbackUsesFilename() {
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            containerXml(),
            opf2(
                title = "   ",
                spineRefs = listOf("c1"),
                manifest = "<item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
            ),
            "OEBPS/chapter1.xhtml" to xhtml("<p>Body.</p>")
        )

        val metadata = parser.readMetadata(file, "fallback-novel.epub")

        assertEquals("fallback-novel", metadata.title)
    }

    @Test
    fun invalidLanguageTagBecomesNull() {
        val file = buildEpub(
            "mimetype" to "application/epub+zip",
            containerXml(),
            opf2(
                language = "!!!not-a-tag",
                spineRefs = listOf("c1"),
                manifest = "<item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
            ),
            "OEBPS/chapter1.xhtml" to xhtml("<p>Body.</p>")
        )

        val metadata = parser.readMetadata(file, "book.epub")

        assertEquals(null, metadata.languageTag)
    }

    private fun assertImportError(expected: ImportError, block: () -> Unit) {
        try {
            block()
        } catch (error: ImportException) {
            assertEquals(expected, error.error)
            return
        }
        fail("Expected ImportException($expected) but nothing was thrown")
    }
}
