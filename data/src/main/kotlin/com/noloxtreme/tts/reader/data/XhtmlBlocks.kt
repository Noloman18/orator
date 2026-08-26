package com.noloxtreme.tts.reader.data

import com.noloxtreme.tts.reader.domain.EpubBlock
import com.noloxtreme.tts.reader.domain.EpubRun
import java.util.Locale
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private val SKIP_TAGS = setOf(
    "script", "style", "head", "title", "meta", "link", "nav", "form",
    "input", "select", "button", "textarea", "svg", "audio", "video",
    "iframe", "object", "embed", "canvas"
)
private val ITALIC_TAGS = setOf("i", "em", "cite", "dfn", "var")
private const val BULLET_MARKER = "\u2022"

/**
 * Converts the body of one EPUB spine document into an ordered list of
 * [EpubBlock] values. Pure JVM: parsing relies only on Jsoup, so every rule
 * below is unit testable without an Android device.
 */
internal object XhtmlBlockExtractor {

    fun extract(
        body: Element,
        basePath: String,
        pageReferences: List<EpubPageReference> = emptyList()
    ): List<EpubBlock> {
        val blocks = ArrayList<EpubBlock>()
        val pending = ArrayList<EpubRun>()
        val pageLabelsByAnchor = pageReferences
            .filter { it.fragment != null }
            .associate { it.fragment!! to it.label }
        pageReferences.firstOrNull { it.fragment == null }?.label?.let { label ->
            blocks += EpubBlock.PageNumber(label)
        }
        walkContainer(body, basePath, pageLabelsByAnchor, blocks, pending)
        flushRuns(pending, blocks) { runs -> EpubBlock.Paragraph(runs) }
        return blocks.filter(::isRenderable)
    }

    private fun isRenderable(block: EpubBlock): Boolean = when (block) {
        is EpubBlock.Image -> true
        is EpubBlock.PageNumber -> true
        EpubBlock.Divider -> true
        is EpubBlock.Heading -> block.runs.hasVisibleText()
        is EpubBlock.Paragraph -> block.runs.hasVisibleText()
        is EpubBlock.Quote -> block.runs.hasVisibleText()
        is EpubBlock.ListItem -> block.runs.hasVisibleText()
    }

    private fun walkContainer(
        container: Element,
        basePath: String,
        pageLabelsByAnchor: Map<String, String>,
        blocks: MutableList<EpubBlock>,
        pending: MutableList<EpubRun>
    ) {
        for (child in container.childNodes()) {
            when (child) {
                is TextNode -> appendRun(pending, child.text(), bold = false, italic = false)
                is Element -> walkBlockElement(
                    child,
                    basePath,
                    pageLabelsByAnchor,
                    blocks,
                    pending
                )
            }
        }
    }

    private fun walkBlockElement(
        element: Element,
        basePath: String,
        pageLabelsByAnchor: Map<String, String>,
        blocks: MutableList<EpubBlock>,
        pending: MutableList<EpubRun>
    ) {
        pageMarkerFor(element, pageLabelsByAnchor)?.let { marker ->
            flushRuns(pending, blocks) { runs -> EpubBlock.Paragraph(runs) }
            addPageMarker(blocks, marker)
        }
        if (isEpubPageBreak(element)) return
        when (element.tagName().lowercase(Locale.ROOT)) {
            in SKIP_TAGS -> Unit
            "br" -> appendRun(pending, " ", bold = false, italic = false)
            "img" -> {
                flushRuns(pending, blocks) { runs -> EpubBlock.Paragraph(runs) }
                addImage(element, basePath, blocks)
            }
            "hr" -> {
                flushRuns(pending, blocks) { runs -> EpubBlock.Paragraph(runs) }
                blocks += EpubBlock.Divider
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flushRuns(pending, blocks) { runs -> EpubBlock.Paragraph(runs) }
                val level = element.tagName().substring(1).toInt()
                emitInline(element, basePath, pageLabelsByAnchor, blocks) { runs ->
                    EpubBlock.Heading(level, runs)
                }
            }
            "p", "pre" -> {
                flushRuns(pending, blocks) { runs -> EpubBlock.Paragraph(runs) }
                emitInline(element, basePath, pageLabelsByAnchor, blocks) { runs ->
                    EpubBlock.Paragraph(runs)
                }
            }
            "blockquote" -> {
                flushRuns(pending, blocks) { runs -> EpubBlock.Paragraph(runs) }
                emitInline(element, basePath, pageLabelsByAnchor, blocks) { runs ->
                    EpubBlock.Quote(runs)
                }
            }
            "ul", "ol" -> {
                flushRuns(pending, blocks) { runs -> EpubBlock.Paragraph(runs) }
                walkList(element, basePath, pageLabelsByAnchor, blocks)
            }
            "table" -> {
                flushRuns(pending, blocks) { runs -> EpubBlock.Paragraph(runs) }
                walkTable(element, basePath, pageLabelsByAnchor, blocks)
            }
            else -> walkContainer(element, basePath, pageLabelsByAnchor, blocks, pending)
        }
    }

    private fun walkList(
        list: Element,
        basePath: String,
        pageLabelsByAnchor: Map<String, String>,
        blocks: MutableList<EpubBlock>
    ) {
        val ordered = list.tagName().equals("ol", ignoreCase = true)
        var marker = 0
        for (item in list.children()) {
            if (!item.tagName().equals("li", ignoreCase = true)) continue
            marker += 1
            val label = if (ordered) "$marker." else BULLET_MARKER
            emitInline(
                item,
                basePath,
                pageLabelsByAnchor,
                blocks,
                skipTags = setOf("ul", "ol")
            ) { runs ->
                EpubBlock.ListItem(ordered = ordered, marker = label, runs = runs)
            }
            for (nested in item.children()) {
                val nestedTag = nested.tagName().lowercase(Locale.ROOT)
                if (nestedTag == "ul" || nestedTag == "ol") {
                    walkList(nested, basePath, pageLabelsByAnchor, blocks)
                }
            }
        }
    }

    private fun walkTable(
        table: Element,
        basePath: String,
        pageLabelsByAnchor: Map<String, String>,
        blocks: MutableList<EpubBlock>
    ) {
        for (row in table.select("tr")) {
            for (cell in row.select("th,td")) {
                emitInline(cell, basePath, pageLabelsByAnchor, blocks) { runs ->
                    EpubBlock.Paragraph(runs)
                }
            }
        }
    }

    /**
     * Collects the inline content of [element] into styled runs and emits one
     * block per contiguous text segment. Embedded `<img>` elements split the
     * surrounding block: the text before and after each image each become
     * their own block, with the image between them.
     */
    private fun emitInline(
        element: Element,
        basePath: String,
        pageLabelsByAnchor: Map<String, String>,
        blocks: MutableList<EpubBlock>,
        skipTags: Set<String> = emptySet(),
        toBlock: (List<EpubRun>) -> EpubBlock
    ) {
        val runs = ArrayList<EpubRun>()

        fun collect(node: Node, bold: Boolean, italic: Boolean) {
            when (node) {
                is TextNode -> appendRun(runs, node.text(), bold, italic)
                is Element -> {
                    pageMarkerFor(node, pageLabelsByAnchor)?.let { marker ->
                        flushRuns(runs, blocks, toBlock)
                        addPageMarker(blocks, marker)
                    }
                    if (isEpubPageBreak(node)) return
                    val tag = node.tagName().lowercase(Locale.ROOT)
                    when {
                        tag in SKIP_TAGS || tag in skipTags -> Unit
                        tag == "img" -> {
                            flushRuns(runs, blocks, toBlock)
                            addImage(node, basePath, blocks)
                        }
                        tag == "br" -> appendRun(runs, " ", bold, italic)
                        else -> {
                            val childBold = bold || tag == "b" || tag == "strong"
                            val childItalic = italic || tag in ITALIC_TAGS
                            for (child in node.childNodes()) {
                                collect(child, childBold, childItalic)
                            }
                        }
                    }
                }
            }
        }

        for (child in element.childNodes()) {
            collect(child, bold = false, italic = false)
        }
        flushRuns(runs, blocks, toBlock)
    }

    private fun pageMarkerFor(
        element: Element,
        pageLabelsByAnchor: Map<String, String>
    ): EpubBlock.PageNumber? {
        val anchor = element.id().trim().ifBlank { element.attr("name").trim() }
        val label = pageLabelsByAnchor[anchor]
            ?: if (isEpubPageBreak(element)) {
                element.text().trim()
                    .ifBlank { element.attr("aria-label").trim() }
                    .ifBlank { element.attr("title").trim() }
                    .ifBlank { element.attr("data-page").trim() }
            } else {
                ""
            }
        return label.takeIf { it.isNotBlank() }?.let(EpubBlock::PageNumber)
    }

    private fun addPageMarker(
        blocks: MutableList<EpubBlock>,
        marker: EpubBlock.PageNumber
    ) {
        if (blocks.lastOrNull() != marker) blocks += marker
    }

    private fun addImage(element: Element, basePath: String, blocks: MutableList<EpubBlock>) {
        resolveResourcePath(basePath, element.attr("src"))?.let { path ->
            blocks += EpubBlock.Image(
                resourcePath = path,
                contentDescription = element.attr("alt").trim().ifEmpty { null }
            )
        }
    }
}

/** EPUB page-break semantics are visual metadata, not narration text. */
internal fun isEpubPageBreak(element: Element): Boolean {
    val tokens = sequenceOf(
        element.attr("epub:type"),
        element.attr("role"),
        element.classNames().joinToString(" ")
    ).flatMap { value ->
        value.split(Regex("""[\s,]+"""))
            .asSequence()
            .filter { it.isNotBlank() }
    }
    return tokens.any { token ->
        token.equals("pagebreak", ignoreCase = true) ||
            token.equals("page-break", ignoreCase = true) ||
            token.equals("doc-pagebreak", ignoreCase = true) ||
            token.equals("doc-page-break", ignoreCase = true)
    }
}

private fun appendRun(runs: MutableList<EpubRun>, text: String, bold: Boolean, italic: Boolean) {
    val collapsed = text.replace(Regex("""\s+"""), " ")
    if (collapsed.isEmpty()) return
    val last = runs.lastOrNull()
    if (last != null && last.bold == bold && last.italic == italic) {
        runs[runs.size - 1] = last.copy(text = last.text + collapsed)
    } else {
        runs += EpubRun(text = collapsed, bold = bold, italic = italic)
    }
}

private fun flushRuns(
    runs: MutableList<EpubRun>,
    blocks: MutableList<EpubBlock>,
    toBlock: (List<EpubRun>) -> EpubBlock
) {
    if (!runs.hasVisibleText()) {
        runs.clear()
        return
    }
    blocks += toBlock(runs.toList())
    runs.clear()
}

private fun List<EpubRun>.hasVisibleText(): Boolean = any { it.text.isNotBlank() }

/**
 * Resolves a relative resource href against the containing document's zip
 * path. Remote and inline schemes are rejected; traversal that escapes the
 * archive root fails resolution through [safeZipPathOrNull].
 */
internal fun resolveResourcePath(basePath: String, href: String): String? {
    val trimmed = href.trim()
    if (trimmed.isEmpty() || SCHEME_REGEX.containsMatchIn(trimmed)) return null
    return safeZipPathOrNull(basePath, trimmed)
}

private val SCHEME_REGEX = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*:""")
