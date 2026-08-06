package com.noloxtreme.tts.reader.data

import java.io.File
import org.jsoup.Jsoup

private val MARKDOWN_HEADING = Regex("""^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$""")
private val MARKDOWN_LIST_PREFIX = Regex("""^\s{0,3}(?:[-+*]|\d+[.)])\s+""")
private val MARKDOWN_THEMATIC_BREAK = Regex("""^\s{0,3}(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$""")
private val MARKDOWN_REFERENCE = Regex("""^\s{0,3}\[[^]]+]:\s+\S+.*$""")

internal class MarkdownParser : BookParser {
    override val extension: String = "md"
    override val supportedExtensions: Set<String> = setOf("md", "markdown")
    override val supportedMimeTypes: Set<String> = setOf("text/markdown", "text/x-markdown")

    override fun readMetadata(file: File, displayName: String): ParsedMetadata {
        var firstHeading: String? = null
        decodedTextReader(file).useLines { lines ->
            for (line in lines) {
                val heading = MARKDOWN_HEADING.matchEntire(line)?.groupValues?.get(1)
                    ?.let(::stripInlineMarkdown)
                    ?.takeIf { it.isNotBlank() }
                if (heading != null) {
                    firstHeading = heading
                    break
                }
            }
        }
        return ParsedMetadata(
            title = MetadataNormalizer.normalizeTitle(
                firstHeading ?: displayName.substringBeforeLast('.', displayName)
            ),
            mimeType = "text/markdown",
            languageTag = null
        )
    }

    override suspend fun forEachBlock(file: File, consumer: suspend (ParsedBlock) -> Unit) {
        var sectionIndex = -1
        var sectionTitle: String? = null
        var inFence = false
        val paragraph = StringBuilder()

        suspend fun emit(value: String) {
            val readable = stripInlineMarkdown(value)
            if (readable.isBlank()) return
            if (sectionIndex < 0) sectionIndex = 0
            consumer(ParsedBlock(readable, sectionIndex, sectionTitle))
        }

        suspend fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                emit(paragraph.toString())
                paragraph.clear()
            }
        }

        decodedTextReader(file).useLines { lines ->
            for (original in lines) {
                val trimmed = original.removePrefix("\uFEFF").trim()
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    flushParagraph()
                    inFence = !inFence
                    continue
                }
                if (inFence) continue
                if (trimmed.isBlank()) {
                    flushParagraph()
                    continue
                }
                val heading = MARKDOWN_HEADING.matchEntire(original)?.groupValues?.get(1)
                    ?.let(::stripInlineMarkdown)
                    ?.takeIf { it.isNotBlank() }
                if (heading != null) {
                    flushParagraph()
                    sectionIndex += 1
                    sectionTitle = heading
                    emit(heading)
                    continue
                }
                if (MARKDOWN_THEMATIC_BREAK.matches(original) || MARKDOWN_REFERENCE.matches(original)) {
                    flushParagraph()
                    continue
                }
                val listItem = MARKDOWN_LIST_PREFIX.replaceFirst(original, "")
                    .takeIf { it != original }
                if (listItem != null) {
                    flushParagraph()
                    emit(listItem.removePrefix("[ ] ").removePrefix("[x] ").removePrefix("[X] "))
                    continue
                }
                val readableLine = original.trimStart().trimStart('>').trimStart()
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(readableLine)
            }
            flushParagraph()
        }
    }
}

private fun stripInlineMarkdown(value: String): String {
    val withoutSyntax = value
        .replace(Regex("""!\[([^]]*)]\([^)]*\)"""), "$1")
        .replace(Regex("""\[([^]]+)]\([^)]*\)"""), "$1")
        .replace(Regex("""\[([^]]+)]\[[^]]*]"""), "$1")
        .replace(Regex("""`+([^`]*)`+"""), "$1")
        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        .replace(Regex("""__([^_]+)__"""), "$1")
        .replace(Regex("""(?<!\*)\*([^*]+)\*(?!\*)"""), "$1")
        .replace(Regex("""(?<!_)_([^_]+)_(?!_)"""), "$1")
        .replace(Regex("""~~([^~]+)~~"""), "$1")
        .replace(Regex("""\\([\\`*_{}\[\]()#+\-.!>])"""), "$1")
    return Jsoup.parseBodyFragment(withoutSyntax).text().trim()
}
