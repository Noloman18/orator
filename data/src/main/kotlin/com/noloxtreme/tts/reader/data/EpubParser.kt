package com.noloxtreme.tts.reader.data

import java.io.File
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipFile
import com.noloxtreme.tts.reader.domain.ImportError
import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.xml.sax.InputSource
import javax.xml.parsers.DocumentBuilderFactory

private const val MAX_EPUB_ENTRIES = 10_000
private const val MAX_EPUB_ENTRY_BYTES = 25L * 1024L * 1024L
private const val MAX_EPUB_TOTAL_BYTES = 250L * 1024L * 1024L

internal class EpubParser : BookParser {
    override val extension: String = "epub"
    override val supportedMimeTypes: Set<String> = setOf("application/epub+zip")

    override fun validate(file: File) {
        if (!isEpub(file)) throw ImportException(ImportError.MALFORMED_DOCUMENT)
    }

    override fun readMetadata(file: File, displayName: String): ParsedMetadata =
        openBook(file, displayName).use { it.metadata }

    override suspend fun forEachBlock(
        file: File,
        consumer: suspend (ParsedBlock) -> Unit
    ) {
        openBook(file, file.name).use { book ->
            var totalRead = 0L
            for ((index, item) in book.spine.withIndex()) {
                val entry = book.zip.getEntry(item.href)
                    ?: throw ImportException(ImportError.MALFORMED_DOCUMENT)
                val bytes = readEntry(book.zip, entry)
                totalRead += bytes.size
                if (totalRead > MAX_EPUB_TOTAL_BYTES) {
                    throw ImportException(ImportError.EPUB_LIMIT_EXCEEDED)
                }
                val document = Jsoup.parse(
                    String(bytes, StandardCharsets.UTF_8),
                    item.href
                )
                document.select("script,style,nav,form,svg,img,audio,video,iframe").remove()
                val sectionTitle = document.select("h1,h2,h3,h4,h5,h6")
                    .firstOrNull()?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: book.navLabels[item.href]
                var emitted = false
                for (block in document.select("h1,h2,h3,h4,h5,h6,p,li,blockquote,pre")) {
                    val text = block.text().trim()
                    if (text.isBlank()) continue
                    consumer(ParsedBlock(text, index, sectionTitle))
                    emitted = true
                }
                if (!emitted) {
                    val bodyText = document.body()?.text()?.trim()
                    if (!bodyText.isNullOrBlank()) {
                        consumer(ParsedBlock(bodyText, index, sectionTitle))
                    }
                }
            }
        }
    }

    companion object {
        fun isEpub(file: File): Boolean = try {
            ZipFile(file).use { zip ->
                val mimetype = zip.getEntry("mimetype") ?: return false
                String(readEntry(zip, mimetype), StandardCharsets.US_ASCII)
                    .trim() == "application/epub+zip"
            }
        } catch (_: Throwable) {
            false
        }
    }
}

private class ParsedEpub(
    val zip: ZipFile,
    val metadata: ParsedMetadata,
    val spine: List<SpineItem>,
    val navLabels: Map<String, String>
) : AutoCloseable {
    override fun close() = zip.close()
}

private data class SpineItem(
    val href: String,
    val mediaType: String,
    val properties: String?
)

private fun openBook(file: File, displayName: String): ParsedEpub {
    val zip = try {
        ZipFile(file)
    } catch (_: Throwable) {
        throw ImportException(ImportError.MALFORMED_DOCUMENT)
    }
    try {
        if (zip.size() > MAX_EPUB_ENTRIES) {
            throw ImportException(ImportError.EPUB_LIMIT_EXCEEDED)
        }
        val mimetype = zip.getEntry("mimetype")
            ?: throw ImportException(ImportError.MALFORMED_DOCUMENT)
        if (String(readEntry(zip, mimetype), StandardCharsets.US_ASCII).trim() != "application/epub+zip") {
            throw ImportException(ImportError.MALFORMED_DOCUMENT)
        }
        if (zip.getEntry("META-INF/encryption.xml") != null) {
            throw ImportException(ImportError.EPUB_ENCRYPTED)
        }
        val container = parseXml(
            readEntry(
                zip,
                zip.getEntry("META-INF/container.xml")
                    ?: throw ImportException(ImportError.MALFORMED_DOCUMENT)
            )
        )
        val rootFile = container.getElementsByTagNameNS("*", "rootfile")
            .item(0) as? Element
            ?: throw ImportException(ImportError.MALFORMED_DOCUMENT)
        val opfPath = safeZipPath("", rootFile.getAttribute("full-path"))
        val opf = parseXml(
            readEntry(
                zip,
                zip.getEntry(opfPath) ?: throw ImportException(ImportError.MALFORMED_DOCUMENT)
            )
        )
        val basePath = opfPath.substringBeforeLast('/', "")
        val manifest = linkedMapOf<String, SpineItem>()
        val items = opf.getElementsByTagNameNS("*", "item")
        for (index in 0 until items.length) {
            val item = items.item(index) as Element
            val id = item.getAttribute("id")
            val href = safeZipPath(basePath, item.getAttribute("href"))
            val mediaType = item.getAttribute("media-type")
            if (id.isNotBlank()) {
                manifest[id] = SpineItem(href, mediaType, item.getAttribute("properties"))
            }
        }
        val spineElement = opf.getElementsByTagNameNS("*", "spine")
            .item(0) as? Element
            ?: throw ImportException(ImportError.MALFORMED_DOCUMENT)
        val spine = buildList {
            val refs = spineElement.getElementsByTagNameNS("*", "itemref")
            for (index in 0 until refs.length) {
                val idref = (refs.item(index) as Element).getAttribute("idref")
                manifest[idref]?.let { add(it) }
            }
        }.filter {
            it.mediaType == "application/xhtml+xml" || it.mediaType == "text/html"
        }
        if (spine.isEmpty()) throw ImportException(ImportError.NO_READABLE_TEXT)
        val title = opf.getElementsByTagNameNS("*", "title").item(0)
            ?.textContent?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: displayName.substringBeforeLast('.', displayName)
        val language = opf.getElementsByTagNameNS("*", "language").item(0)
            ?.textContent?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { Locale.forLanguageTag(it).toLanguageTag() }
            ?.takeIf { it != Locale.ROOT.toLanguageTag() }
        val navLabels = navLabelsFor(zip, manifest, opf, basePath, opfPath)
        return ParsedEpub(
            zip = zip,
            metadata = ParsedMetadata(
                title = MetadataNormalizer.normalizeTitle(title),
                mimeType = "application/epub+zip",
                languageTag = language
            ),
            spine = spine,
            navLabels = navLabels
        )
    } catch (error: Throwable) {
        zip.close()
        if (error is ImportException) throw error
        throw ImportException(ImportError.MALFORMED_DOCUMENT)
    }
}

private fun navLabelsFor(
    zip: ZipFile,
    manifest: Map<String, SpineItem>,
    opf: org.w3c.dom.Document,
    basePath: String,
    opfPath: String
): Map<String, String> {
    val navItem = manifest.values.firstOrNull { item ->
        item.mediaType == "application/xhtml+xml" &&
            item.properties?.split(' ')?.any { it == "nav" } == true
    } ?: run {
        val reference = opf.getElementsByTagNameNS("*", "reference").item(0) as? Element
        reference?.takeIf { it.getAttribute("type") == "toc" }
            ?.getAttribute("href")
            ?.takeIf { it.isNotBlank() }
            ?.let { href -> manifest.values.firstOrNull { item -> item.href == safeZipPath(basePath, href) } }
    } ?: return emptyMap()
    val entry = zip.getEntry(navItem.href) ?: return emptyMap()
    val navDocument = try {
        Jsoup.parse(String(readEntry(zip, entry), StandardCharsets.UTF_8), navItem.href)
    } catch (_: Throwable) {
        return emptyMap()
    }
    val labels = linkedMapOf<String, String>()
    for (anchor in navDocument.select("nav a[href], [epub\\:type=toc] a[href], a[href]")) {
        val label = anchor.text().trim()
        if (label.isBlank()) continue
        val target = anchor.attr("href").substringBefore('#')
        if (target.isBlank()) continue
        val targetPath = safeZipPathOrNull(navItem.href.substringBeforeLast('/', ""), target) ?: continue
        if (targetPath !in labels) labels[targetPath] = label
    }
    return labels
}

private fun safeZipPathOrNull(base: String, href: String): String? = try {
    safeZipPath(base, href)
} catch (_: Throwable) {
    null
}

private fun readEntry(zip: ZipFile, entry: java.util.zip.ZipEntry): ByteArray {
    if (entry.isDirectory || entry.size > MAX_EPUB_ENTRY_BYTES) {
        throw ImportException(ImportError.EPUB_LIMIT_EXCEEDED)
    }
    val output = java.io.ByteArrayOutputStream()
    zip.getInputStream(entry).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_EPUB_ENTRY_BYTES) {
                throw ImportException(ImportError.EPUB_LIMIT_EXCEEDED)
            }
            output.write(buffer, 0, read)
        }
    }
    return output.toByteArray()
}

private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
    if (containsDoctype(bytes)) {
        throw ImportException(ImportError.MALFORMED_DOCUMENT)
    }
    return try {
        /*
         * Android's built-in DocumentBuilderFactory only supports the basic
         * namespace/validation features. Apache/Xerces feature URIs such as
         * disallow-doctype-decl are rejected during factory configuration on
         * Android, which would make every otherwise-valid EPUB look malformed.
         * The resolver keeps metadata parsing offline on both Android and JVM.
         */
        val builder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        builder.parse(bytes.inputStream())
    } catch (error: Throwable) {
        throw ImportException(ImportError.MALFORMED_DOCUMENT).apply { initCause(error) }
    }
}

private fun containsDoctype(bytes: ByteArray): Boolean {
    val text = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        bytes.size >= 2 && bytes[0] == '<'.code.toByte() && bytes[1] == 0x00.toByte() ->
            String(bytes, StandardCharsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0x00.toByte() && bytes[1] == '<'.code.toByte() ->
            String(bytes, StandardCharsets.UTF_16BE)
        else -> String(bytes, StandardCharsets.UTF_8)
    }
    return text.contains("<!DOCTYPE", ignoreCase = true)
}

private fun safeZipPath(base: String, href: String): String {
    val decoded = URLDecoder.decode(href.substringBefore('#'), "UTF-8")
        .replace(Char(92), '/')
    val components = (if (base.isBlank()) decoded else "$base/$decoded")
        .split('/')
    val result = ArrayDeque<String>()
    for (component in components) {
        when {
            component.isBlank() || component == "." -> Unit
            component == ".." -> {
                if (result.isEmpty()) throw ImportException(ImportError.MALFORMED_DOCUMENT)
                result.removeLast()
            }
            else -> result.addLast(component)
        }
    }
    return result.joinToString("/")
}
