package com.noloxtreme.tts.reader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.noloxtreme.tts.reader.domain.ImportError
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfParserTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val parser = PdfParser(context)

    @Test
    fun extractsTextInPageOrderAndUsesEmbeddedTitle() = runTest {
        val file = createPdf("Fixture PDF", "First page sentence.", "Second page sentence.")
        val blocks = mutableListOf<ParsedBlock>()

        val metadata = parser.readMetadata(file, "fallback.pdf")
        parser.forEachBlock(file, blocks::add)

        assertEquals("Fixture PDF", metadata.title)
        assertEquals("application/pdf", metadata.mimeType)
        assertEquals(listOf(0, 1), blocks.map { it.sectionIndex })
        assertEquals(
            listOf("First page sentence.", "Second page sentence."),
            blocks.map { it.text.replace(Regex("""\s+"""), " ").trim() }
        )
    }

    @Test
    fun rejectsInvalidPdfSignature() {
        val file = File.createTempFile("orator-invalid", ".pdf").apply {
            deleteOnExit()
            writeText("not a PDF")
        }

        val error = assertThrows(ImportException::class.java) { parser.validate(file) }

        assertEquals(ImportError.MALFORMED_DOCUMENT, error.error)
    }

    @Test
    fun rejectsEncryptedPdf() {
        val file = File.createTempFile("orator-protected", ".pdf").apply { deleteOnExit() }
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.protect(
                StandardProtectionPolicy("owner-password", "reader-password", AccessPermission())
            )
            document.save(file)
        }

        val error = assertThrows(ImportException::class.java) {
            parser.readMetadata(file, "protected.pdf")
        }

        assertEquals(ImportError.PDF_ENCRYPTED, error.error)
    }

    private fun createPdf(title: String, vararg pages: String): File {
        val file = File.createTempFile("orator-pdf", ".pdf").apply { deleteOnExit() }
        PDDocument().use { document ->
            document.documentInformation.title = title
            pages.forEach { text ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font.HELVETICA, 12f)
                    content.newLineAtOffset(72f, 720f)
                    content.showText(text)
                    content.endText()
                }
            }
            document.save(file)
        }
        return file
    }
}
