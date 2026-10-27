package com.noloxtreme.tts.reader.data

import android.content.Context
import androidx.room.withTransaction
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.TimeProvider
import com.noloxtreme.tts.reader.domain.TranslatedDocumentRepository
import com.noloxtreme.tts.reader.domain.TranslatedParagraph
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataOutputStream
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Singleton
class RoomTranslatedDocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: OratorDatabase,
    private val timeProvider: TimeProvider
) : TranslatedDocumentRepository {
    override suspend fun save(
        source: Document,
        targetLanguageTag: String,
        paragraphs: List<TranslatedParagraph>,
        sectionTitles: Map<Int, String?>
    ): DocumentId = withContext(Dispatchers.IO) {
        val locale = Locale.forLanguageTag(targetLanguageTag)
        require(locale.language.isNotBlank()) { "A translation needs a target language" }
        require(paragraphs.isNotEmpty()) { "A translation needs readable text" }
        require(paragraphs.all { it.sectionIndex >= 0 && it.text.isNotBlank() })
        require(paragraphs.zipWithNext().all { (left, right) -> left.sectionIndex <= right.sectionIndex })

        val targetTag = locale.toLanguageTag()
        val id = DocumentId(UUID.randomUUID().toString())
        val staging = File(context.cacheDir, "translations/${id.value}")
        val destination = File(context.filesDir, "documents/${id.value}")
        var committed = false
        try {
            check(staging.mkdirs()) { "Unable to prepare translated document" }
            val rows = ArrayList<ParagraphEntity>(paragraphs.size)
            val sections = ArrayList<SectionEntity>()
            var offset = 0L
            var currentSection = -1
            var firstParagraph = 0
            var sectionStart = 0L
            fun closeSection() {
                if (currentSection < 0) return
                sections += SectionEntity(
                    documentId = id.value,
                    sectionIndex = currentSection,
                    title = sectionTitles[currentSection]?.trim()?.takeIf(String::isNotEmpty),
                    firstParagraphIndex = firstParagraph,
                    lastParagraphIndex = rows.lastIndex,
                    absoluteStart = sectionStart,
                    absoluteEnd = rows.last().absoluteEnd
                )
            }
            File(staging, "source.txt").bufferedWriter().use { writer ->
                paragraphs.forEachIndexed { index, paragraph ->
                    currentCoroutineContext().ensureActive()
                    if (paragraph.sectionIndex != currentSection) {
                        closeSection()
                        currentSection = paragraph.sectionIndex
                        firstParagraph = index
                        sectionStart = offset
                        sectionTitles[currentSection]?.trim()?.takeIf(String::isNotEmpty)?.let { title ->
                            writer.append(title).append("\n\n")
                        }
                    }
                    val text = paragraph.text.trim()
                    writer.append(text).append("\n\n")
                    rows += ParagraphEntity(
                        documentId = id.value,
                        paragraphIndex = index,
                        sectionIndex = currentSection,
                        text = text,
                        absoluteStart = offset,
                        absoluteEnd = offset + text.length
                    )
                    offset += text.length + 2L
                }
                closeSection()
            }
            val hash = translationHash(source, targetTag, rows, sections)
            val languageName = locale.getDisplayLanguage(Locale.getDefault())
                .takeIf(String::isNotBlank) ?: targetTag
            val title = MetadataNormalizer.normalizeTitle(source.title + " · " + languageName)
            val now = timeProvider.nowEpochMillis()
            currentCoroutineContext().ensureActive()
            // Once publication starts it must finish (or roll back) with its source
            // file intact, even if the screen's coroutine is cancelled at commit.
            withContext(NonCancellable) {
                database.withTransaction {
                    database.documentDao().findBySha256(hash)?.let { existing ->
                        return@withTransaction DocumentId(existing.id)
                    }
                    check(destination.parentFile?.isDirectory == true || destination.parentFile?.mkdirs() == true)
                    check(staging.renameTo(destination)) { "Unable to save translated source" }
                    database.documentDao().insert(
                        DocumentEntity(
                            id = id.value,
                            title = title,
                            originalFileName = MetadataNormalizer.normalizeFileName("$title.txt"),
                            mimeType = "text/plain",
                            sourceExtension = "txt",
                            privateSourcePath = "documents/${id.value}/source.txt",
                            sha256 = hash,
                            languageTag = targetTag,
                            totalCharacterCount = rows.last().absoluteEnd,
                            sectionCount = sections.size,
                            importedAt = now,
                            lastOpenedAt = null,
                            translationSourceId = source.id.value
                        )
                    )
                    rows.chunked(250).forEach { database.contentDao().insertBatch(it) }
                    database.sectionDao().insertAll(sections)
                    database.progressDao().upsert(
                        ReadingProgressEntity(id.value, 0, 0, 0L, now, false)
                    )
                    id
                }.also { savedId -> committed = savedId == id }
            }
        } finally {
            staging.deleteRecursively()
            if (!committed) destination.deleteRecursively()
        }
    }

    /** Length framing keeps different paragraph/chapter boundaries distinct. */
    private fun translationHash(
        source: Document,
        targetTag: String,
        paragraphs: List<ParagraphEntity>,
        sections: List<SectionEntity>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = object : java.io.OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
        }
        DataOutputStream(DigestOutputStream(sink, digest)).use { output ->
            fun field(value: String) {
                val bytes = value.toByteArray(Charsets.UTF_8)
                output.writeInt(bytes.size)
                output.write(bytes)
            }
            field("orator-translation-v1")
            field(source.id.value)
            field(source.sha256)
            field(targetTag)
            sections.forEach { section ->
                output.writeInt(section.sectionIndex)
                field(section.title.orEmpty())
            }
            paragraphs.forEach { paragraph ->
                output.writeInt(paragraph.sectionIndex)
                field(paragraph.text)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
