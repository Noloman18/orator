package com.noloxtreme.tts.reader.data

import android.content.Context
import com.noloxtreme.tts.reader.domain.ImportError
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val MAX_PDF_PAGES = 10_000
private const val MAX_PDF_EXTRACTED_CHARACTERS = 25_000_000L
private val PDF_CONTROL_CHARACTERS = Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]""")

internal class PdfParser(context: Context) : BookParser {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    override val extension: String = "pdf"
    override val supportedMimeTypes: Set<String> = setOf("application/pdf")

    override fun validate(file: File) {
        val signature = file.inputStream().use { input ->
            val bytes = ByteArray(5)
            val count = input.read(bytes)
            if (count == bytes.size) String(bytes, StandardCharsets.US_ASCII) else ""
        }
        if (signature != "%PDF-") throw ImportException(ImportError.MALFORMED_DOCUMENT)
    }

    override fun readMetadata(file: File, displayName: String): ParsedMetadata =
        loadPdf(file).use { document ->
            enforcePageLimit(document)
            val embeddedTitle = document.documentInformation?.title
                ?.takeIf { it.isNotBlank() }
            ParsedMetadata(
                title = MetadataNormalizer.normalizeTitle(
                    embeddedTitle ?: displayName.substringBeforeLast('.', displayName)
                ),
                mimeType = "application/pdf",
                languageTag = null
            )
        }

    override suspend fun forEachBlock(file: File, consumer: suspend (ParsedBlock) -> Unit) {
        loadPdf(file).use { document ->
            enforcePageLimit(document)
            val stripper = PDFTextStripper().apply {
                setSortByPosition(true)
                setAddMoreFormatting(true)
            }
            var extractedCharacters = 0L
            for (pageNumber in 1..document.numberOfPages) {
                currentCoroutineContext().ensureActive()
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber
                val pageText = try {
                    stripper.getText(document)
                } catch (error: IOException) {
                    throw ImportException(ImportError.MALFORMED_DOCUMENT).apply { initCause(error) }
                } catch (error: RuntimeException) {
                    throw ImportException(ImportError.MALFORMED_DOCUMENT).apply { initCause(error) }
                }
                extractedCharacters += pageText.length
                if (extractedCharacters > MAX_PDF_EXTRACTED_CHARACTERS) {
                    throw ImportException(ImportError.PDF_LIMIT_EXCEEDED)
                }
                pageText
                    .replace(PDF_CONTROL_CHARACTERS, " ")
                    .split(Regex("""(?:\r?\n)\s*(?:\r?\n)+"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { paragraph ->
                        consumer(ParsedBlock(paragraph, pageNumber - 1, null))
                    }
            }
        }
    }

    private fun loadPdf(file: File): PDDocument = try {
        PDDocument.load(file).also { document ->
            if (document.isEncrypted) {
                document.close()
                throw ImportException(ImportError.PDF_ENCRYPTED)
            }
        }
    } catch (error: InvalidPasswordException) {
        throw ImportException(ImportError.PDF_ENCRYPTED).apply { initCause(error) }
    } catch (error: ImportException) {
        throw error
    } catch (error: IOException) {
        throw ImportException(ImportError.MALFORMED_DOCUMENT).apply { initCause(error) }
    } catch (error: RuntimeException) {
        throw ImportException(ImportError.MALFORMED_DOCUMENT).apply { initCause(error) }
    }

    private fun enforcePageLimit(document: PDDocument) {
        if (document.numberOfPages > MAX_PDF_PAGES) {
            throw ImportException(ImportError.PDF_LIMIT_EXCEEDED)
        }
    }
}
