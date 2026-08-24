package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.EpubContentStore
import com.noloxtreme.tts.reader.domain.EpubSpineContent
import com.noloxtreme.tts.reader.domain.EpubTocEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.w3c.dom.Element as DomElement

/**
 * Resolves a document id to its imported EPUB file inside app-private
 * storage, or null when the document is missing or not an EPUB.
 */
internal interface EpubSourceLocator {
    suspend fun sourceFile(id: DocumentId): File?
}

@Singleton
internal class DocumentEpubLocator @Inject constructor(
    private val database: OratorDatabase,
    @ApplicationContext private val context: android.content.Context
) : EpubSourceLocator {
    override suspend fun sourceFile(id: DocumentId): File? = withContext(Dispatchers.IO) {
        val entity = database.documentDao().getById(id.value) ?: return@withContext null
        if (!entity.sourceExtension.equals("epub", ignoreCase = true)) return@withContext null
        val root = context.filesDir.canonicalFile
        val resolved = root.resolve(entity.privateSourcePath).canonicalFile
        var parent = resolved.parentFile
        while (parent != null && parent != root) parent = parent.parentFile
        if (parent != root) return@withContext null
        resolved.takeIf { it.isFile }
    }
}

/**
 * Reads visual EPUB content (chapters, table of contents, images) from the
 * imported private copy. One-shot [openBook] per request keeps lifecycle
 * handling simple; the import-time zip limits are enforced here as well.
 */
@Singleton
internal class ZipEpubBookStore @Inject constructor(
    private val sourceLocator: EpubSourceLocator
) : EpubContentStore {

    override suspend fun tableOfContents(id: DocumentId): List<EpubTocEntry> =
        withContext(Dispatchers.IO) {
            val file = sourceLocator.sourceFile(id) ?: return@withContext emptyList()
            try {
                openBook(file, file.name).use { book ->
                    tocFromNav(book) ?: tocFromNcx(book)
                } ?: emptyList()
            } catch (_: Throwable) {
                emptyList()
            }
        }

    override suspend fun spineContent(id: DocumentId, spineIndex: Int): EpubSpineContent? =
        withContext(Dispatchers.IO) {
            if (spineIndex < 0) return@withContext null
            val file = sourceLocator.sourceFile(id) ?: return@withContext null
            try {
                openBook(file, file.name).use { book ->
                    val item = book.spine.getOrNull(spineIndex) ?: return@use null
                    if (item.isCover) {
                        return@use EpubSpineContent(
                            spineIndex = spineIndex,
                            title = null,
                            blocks = listOf(
                                EpubBlock.Image(resourcePath = item.href, contentDescription = null)
                            )
                        )
                    }
                    val entry = book.zip.getEntry(item.href) ?: return@use null
                    val bytes = readEntry(book.zip, entry)
                    val document = Jsoup.parse(String(bytes, Charsets.UTF_8), item.href)
                    val body = document.body() ?: return@use null
                    EpubSpineContent(
                        spineIndex = spineIndex,
                        title = spineTitle(body, book.navLabels[item.href]),
                        blocks = XhtmlBlockExtractor.extract(
                            body,
                            basePath = item.href.substringBeforeLast('/', "")
                        )
                    )
                }
            } catch (_: Throwable) {
                null
            }
        }

    override suspend fun imageResource(id: DocumentId, resourcePath: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = sourceLocator.sourceFile(id) ?: return@withContext null
            try {
                java.util.zip.ZipFile(file).use { zip ->
                    if (zip.size() > MAX_EPUB_ENTRIES) return@use null
                    val path = safeZipPathOrNull("", resourcePath) ?: return@use null
                    val entry = zip.getEntry(path) ?: return@use null
                    readEntry(zip, entry)
                }
            } catch (_: Throwable) {
                null
            }
        }

    override suspend fun spineCount(id: DocumentId): Int = withContext(Dispatchers.IO) {
        val file = sourceLocator.sourceFile(id) ?: return@withContext 0
        try {
            openBook(file, file.name).use { book -> book.spine.size }
        } catch (_: Throwable) {
            0
        }
    }

    override suspend fun hasCoverPage(id: DocumentId): Boolean = withContext(Dispatchers.IO) {
        val file = sourceLocator.sourceFile(id) ?: return@withContext false
        try {
            openBook(file, file.name).use { book -> book.spine.firstOrNull()?.isCover == true }
        } catch (_: Throwable) {
            false
        }
    }

    private fun spineTitle(body: Element, navLabel: String?): String? = body
        .select("h1,h2,h3,h4,h5,h6")
        .firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
        ?: navLabel

    /** EPUB 3: the manifest item marked `properties="nav"`. */
    private fun tocFromNav(book: ParsedEpub): List<EpubTocEntry>? {
        val navItem = book.spine.firstOrNull { item ->
            item.properties?.split(' ')?.any { it.equals("nav", ignoreCase = true) } == true
        } ?: return null
        val entry = book.zip.getEntry(navItem.href) ?: return null
        val navDocument = Jsoup.parse(
            String(readEntry(book.zip, entry), Charsets.UTF_8),
            navItem.href
        )
        val hrefToSpine = spineIndexLookup(book)
        val rootList = navDocument.selectFirst("nav ol") ?: return null
        val entries = ArrayList<EpubTocEntry>()
        walkNavList(
            list = rootList,
            depth = 0,
            entries = entries,
            hrefToSpine = hrefToSpine,
            base = navItem.href.substringBeforeLast('/', "")
        )
        return entries.ifEmpty { null }
    }

    private fun walkNavList(
        list: Element,
        depth: Int,
        entries: MutableList<EpubTocEntry>,
        hrefToSpine: Map<String, Int>,
        base: String
    ) {
        for (item in list.children()) {
            if (!item.tagName().equals("li", ignoreCase = true)) continue
            item.selectFirst("> a")?.let { anchor ->
                val title = anchor.text().trim()
                val target = resolveResourcePath(base, anchor.attr("href"))?.substringBefore('#')
                val spineIndex = target?.let { hrefToSpine[it] }
                if (title.isNotEmpty() && spineIndex != null) {
                    entries += EpubTocEntry(title = title, spineIndex = spineIndex, depth = depth)
                }
            }
            for (nested in item.children()) {
                if (nested.tagName().equals("ol", ignoreCase = true)) {
                    walkNavList(nested, depth + 1, entries, hrefToSpine, base)
                }
            }
        }
    }

    /** EPUB 2 fallback: `toc.ncx` referenced by the spine `toc` attribute. */
    private fun tocFromNcx(book: ParsedEpub): List<EpubTocEntry>? {
        val ncxHref = book.ncxHref ?: return null
        val entry = book.zip.getEntry(ncxHref) ?: return null
        val ncx = parseXml(readEntry(book.zip, entry))
        val navMap = ncx.getElementsByTagNameNS("*", "navMap").item(0) as? DomElement
            ?: return null
        val hrefToSpine = spineIndexLookup(book)
        val entries = ArrayList<EpubTocEntry>()
        walkNcxPoints(
            container = navMap,
            depth = 0,
            entries = entries,
            hrefToSpine = hrefToSpine,
            base = ncxHref.substringBeforeLast('/', "")
        )
        return entries.ifEmpty { null }
    }

    /** Visits only the navPoint children directly owned by [container]. */
    private fun walkNcxPoints(
        container: DomElement,
        depth: Int,
        entries: MutableList<EpubTocEntry>,
        hrefToSpine: Map<String, Int>,
        base: String
    ) {
        for (child in container.childNodes.asList()) {
            if (child !is DomElement || !child.tagName.equals("navPoint", ignoreCase = true)) continue
            val navLabel = child.directChildElements().firstOrNull {
                it.tagName.equals("navLabel", ignoreCase = true)
            }
            val label = navLabel?.getElementsByTagNameNS("*", "text")?.item(0)
                ?.textContent?.trim().orEmpty()
            val content = child.directChildElements().firstOrNull {
                it.tagName.equals("content", ignoreCase = true)
            }
            val target = resolveResourcePath(base, content?.getAttribute("src").orEmpty())
                ?.substringBefore('#')
            val spineIndex = target?.let { hrefToSpine[it] }
            if (label.isNotEmpty() && spineIndex != null) {
                entries += EpubTocEntry(title = label, spineIndex = spineIndex, depth = depth)
            }
            walkNcxPoints(child, depth + 1, entries, hrefToSpine, base)
        }
    }

    private fun spineIndexLookup(book: ParsedEpub): Map<String, Int> =
        book.spine.withIndex().associate { (index, item) -> item.href to index }
}

private fun org.w3c.dom.NodeList.asList(): List<org.w3c.dom.Node> =
    (0 until length).map { item(it) }

private fun org.w3c.dom.Node.directChildElements(): List<DomElement> =
    childNodes.asList().filterIsInstance<DomElement>()
