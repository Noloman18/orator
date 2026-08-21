package com.noloxtreme.tts.reader.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.rules.TemporaryFolder

/** Shared EPUB fixture builder for data-layer unit tests. */
internal class TestEpubFactory(private val temporaryFolder: TemporaryFolder) {

    /** Entries map zip path to either String (UTF-8) or ByteArray content. */
    fun buildEpub(vararg entries: Pair<String, Any>): File {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                when (content) {
                    is String -> zip.write(content.toByteArray(Charsets.UTF_8))
                    is ByteArray -> zip.write(content)
                    else -> error("Unsupported entry content for $name")
                }
                zip.closeEntry()
            }
        }
        return temporaryFolder.newFile("book-${System.nanoTime()}.epub")
            .apply { writeBytes(output.toByteArray()) }
    }

    fun containerXml(opfPath: String = "OEBPS/content.opf"): Pair<String, String> =
        "META-INF/container.xml" to """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trim()

    fun opf3(
        title: String = "Test Book",
        manifest: String,
        spineRefs: List<String>,
        spineTocAttribute: String? = null
    ): Pair<String, String> = "OEBPS/content.opf" to """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$title</dc:title>
            <dc:language>en</dc:language>
          </metadata>
          <manifest>
            $manifest
          </manifest>
          <spine${spineTocAttribute?.let { """ toc="$it"""" } ?: ""}>
            ${spineRefs.joinToString(" ") { "<itemref idref=\"$it\"/>" }}
          </spine>
        </package>
    """.trim()

    fun xhtml(body: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head><title>Chapter</title></head>
        <body>$body</body>
        </html>
    """.trim()
}
