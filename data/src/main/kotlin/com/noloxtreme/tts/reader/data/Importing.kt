package com.noloxtreme.tts.reader.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentImporter
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.ImportError
import com.noloxtreme.tts.reader.domain.ImportSource
import com.noloxtreme.tts.reader.domain.ImportState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

private const val MAX_SOURCE_BYTES = 100L * 1024L * 1024L
private const val PARAGRAPH_BATCH_SIZE = 250
private val TRANSIENT_DIRECTORY_NAME =
    Regex("""\A(\.import-|\.delete-)[0-9a-fA-F-]{36}\z""")

@Singleton
class SafDocumentImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: OratorDatabase,
    private val timeProvider: com.noloxtreme.tts.reader.domain.TimeProvider,
    private val parserRegistry: BookParserRegistry
) : DocumentImporter {
    private val activeJob = AtomicReference<Job?>(null)

    override fun import(source: ImportSource): Flow<ImportState> = channelFlow {
        val job = currentCoroutineContext()[Job]
        check(activeJob.compareAndSet(null, job)) { "Another import is already running" }
        val documentId = DocumentId(UUID.randomUUID().toString())
        val documentsRoot = context.filesDir.resolve("documents").canonicalFile
        documentsRoot.mkdirs()
        cleanupTransientDirectories(documentsRoot)
        val temporaryDirectory = documentsRoot.resolve(".import-" + documentId.value)
        val binarySource = temporaryDirectory.resolve("source.bin")
        var finalDirectory: File? = null
        try {
            temporaryDirectory.mkdirs()
            copySource(source, binarySource) { copied ->
                send(ImportState.Copying(copied, source.reportedSizeBytes))
            }
            val sha256 = sha256(binarySource)
            val existing = database.documentDao().findBySha256(sha256)
            if (existing != null) {
                temporaryDirectory.deleteRecursively()
                send(ImportState.ExistingDocument(DocumentId(existing.id)))
                return@channelFlow
            }
            val parser = parserRegistry.resolve(source, binarySource)
            val extension = parser.extension
            val sourceFile = temporaryDirectory.resolve("source." + extension)
            check(binarySource.renameTo(sourceFile)) { "Unable to name imported source" }
            check(temporaryDirectory.renameTo(documentsRoot.resolve(documentId.value))) {
                "Unable to finalize imported source"
            }
            finalDirectory = documentsRoot.resolve(documentId.value)
            send(ImportState.Parsing)
            val finalizedSource = finalDirectory.resolve("source." + extension)
            val metadata = parser.readMetadata(finalizedSource, source.displayName)
            send(ImportState.Saving)
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    database.documentDao().insert(
                        DocumentEntity(
                            id = documentId.value,
                            title = metadata.title,
                            originalFileName = MetadataNormalizer.normalizeFileName(source.displayName),
                            mimeType = metadata.mimeType,
                            sourceExtension = extension,
                            privateSourcePath = "documents/" + documentId.value + "/source." + extension,
                            sha256 = sha256,
                            languageTag = metadata.languageTag,
                            totalCharacterCount = 0L,
                            sectionCount = 0,
                            importedAt = timeProvider.nowEpochMillis(),
                            lastOpenedAt = null
                        )
                    )
                    val paragraphs = ArrayList<ParagraphEntity>(PARAGRAPH_BATCH_SIZE)
                    val sections = ArrayList<SectionEntity>()
                    var paragraphIndex = 0
                    var absoluteOffset = 0L
                    var lastParagraph: ParagraphEntity? = null
                    var openSectionIndex = -1
                    var openSectionTitle: String? = null
                    var openSectionFirstParagraph = 0
                    var openSectionStart = 0L
                    parser.forEachBlock(finalizedSource) { block ->
                        val text = normalizeParagraph(block.text)
                        if (text.isBlank()) return@forEachBlock
                        if (block.sectionIndex != openSectionIndex) {
                            closeSection(
                                sections,
                                documentId,
                                openSectionIndex,
                                openSectionTitle,
                                openSectionFirstParagraph,
                                paragraphIndex - 1,
                                openSectionStart,
                                lastParagraph?.absoluteEnd ?: 0L
                            )
                            openSectionIndex = block.sectionIndex
                            openSectionTitle = block.sectionTitle
                            openSectionFirstParagraph = paragraphIndex
                            openSectionStart = absoluteOffset
                        }
                        val paragraph = ParagraphEntity(
                            documentId = documentId.value,
                            paragraphIndex = paragraphIndex,
                            sectionIndex = block.sectionIndex,
                            text = text,
                            absoluteStart = absoluteOffset,
                            absoluteEnd = absoluteOffset + text.length
                        )
                        paragraphs += paragraph
                        lastParagraph = paragraph
                        paragraphIndex += 1
                        absoluteOffset = paragraph.absoluteEnd + 2L
                        if (paragraphs.size >= PARAGRAPH_BATCH_SIZE) {
                            database.contentDao().insertBatch(paragraphs.toList())
                            paragraphs.clear()
                        }
                    }
                    closeSection(
                        sections,
                        documentId,
                        openSectionIndex,
                        openSectionTitle,
                        openSectionFirstParagraph,
                        paragraphIndex - 1,
                        openSectionStart,
                        lastParagraph?.absoluteEnd ?: 0L
                    )
                    if (paragraphs.isNotEmpty()) {
                        database.contentDao().insertBatch(paragraphs)
                    }
                    val finalParagraph = lastParagraph
                        ?: throw ImportException(ImportError.NO_READABLE_TEXT)
                    database.sectionDao().insertAll(sections)
                    database.documentDao().finalizeDocument(
                        id = documentId.value,
                        title = metadata.title,
                        languageTag = metadata.languageTag,
                        totalCharacterCount = finalParagraph.absoluteEnd,
                        sectionCount = sections.size
                    )
                    database.progressDao().upsert(
                        ReadingProgressEntity(
                            documentId = documentId.value,
                            paragraphIndex = 0,
                            offsetInParagraph = 0,
                            absoluteOffset = 0,
                            updatedAt = timeProvider.nowEpochMillis(),
                            isCompleted = false
                        )
                    )
                }
            }
            send(ImportState.Success(documentId))
        } catch (cancelled: CancellationException) {
            cleanupImport(temporaryDirectory, finalDirectory)
            throw cancelled
        } catch (error: ImportException) {
            cleanupImport(temporaryDirectory, finalDirectory)
            send(ImportState.Failure(error.error))
        } catch (error: Throwable) {
            cleanupImport(temporaryDirectory, finalDirectory)
            val mapped = when (error) {
                is SecurityException -> ImportError.SOURCE_UNREADABLE
                is CharacterCodingException -> ImportError.UNSUPPORTED_ENCODING
                is IOException -> ImportError.SOURCE_UNREADABLE
                else -> ImportError.DATABASE_ERROR
            }
            send(ImportState.Failure(mapped))
        } finally {
            activeJob.compareAndSet(job, null)
        }
    }

    override suspend fun cancelActiveImport() {
        activeJob.get()?.cancel()
    }

    private suspend fun copySource(
        source: ImportSource,
        destination: File,
        onProgress: suspend (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        if ((source.reportedSizeBytes ?: 0L) > MAX_SOURCE_BYTES) {
            throw ImportException(ImportError.FILE_TOO_LARGE)
        }
        val input = context.contentResolver.openInputStream(Uri.parse(source.opaqueHandle))
            ?: throw ImportException(ImportError.SOURCE_UNREADABLE)
        input.use { inputStream ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    ensureActive()
                    val read = inputStream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_SOURCE_BYTES) {
                        throw ImportException(ImportError.FILE_TOO_LARGE)
                    }
                    output.write(buffer, 0, read)
                    onProgress(total)
                }
            }
        }
    }

    private fun cleanupTransientDirectories(root: File) {
        val canonicalRoot = root.canonicalFile
        canonicalRoot.listFiles()?.forEach { entry ->
            val canonicalEntry = entry.canonicalFile
            if (canonicalEntry.parentFile != canonicalRoot) return@forEach
            if (TRANSIENT_DIRECTORY_NAME.matches(canonicalEntry.name)) {
                canonicalEntry.deleteRecursively()
            }
        }
    }

    private fun cleanupImport(temporary: File, finalized: File?) {
        temporary.deleteRecursively()
        finalized?.deleteRecursively()
    }
}

data class ParsedMetadata(
    val title: String,
    val mimeType: String,
    val languageTag: String?
)

data class ParsedBlock(
    val text: String,
    val sectionIndex: Int,
    val sectionTitle: String?
)

interface BookParser {
    val extension: String
    fun accepts(source: ImportSource): Boolean
    fun validate(file: File) = Unit
    fun readMetadata(file: File, displayName: String): ParsedMetadata
    suspend fun forEachBlock(file: File, consumer: suspend (ParsedBlock) -> Unit)
}

class BookParserRegistry(
    private val parsers: Set<BookParser>
) {
    fun resolve(source: ImportSource, file: File): BookParser {
        val matches = parsers.filter { it.accepts(source) }
        if (matches.size != 1) throw ImportException(ImportError.UNSUPPORTED_FORMAT)
        return matches.single().also { it.validate(file) }
    }
}

internal class TxtParser : BookParser {
    override val extension: String = "txt"

    override fun accepts(source: ImportSource): Boolean {
        val name = source.displayName.lowercase(Locale.ROOT)
        return source.mimeType.equals("text/plain", true) ||
            name.endsWith(".txt") ||
            (source.mimeType.equals("application/octet-stream", true) && !name.endsWith(".epub"))
    }

    override fun readMetadata(file: File, displayName: String): ParsedMetadata =
        ParsedMetadata(
            title = MetadataNormalizer.normalizeTitle(
                displayName.substringBeforeLast('.', displayName)
            ),
            mimeType = "text/plain",
            languageTag = null
        )

    override suspend fun forEachBlock(file: File, consumer: suspend (ParsedBlock) -> Unit) {
        val charset = detectCharset(file)
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        InputStreamReader(file.inputStream().buffered(), decoder).useLines { lines ->
            val paragraph = StringBuilder()
            for (original in lines) {
                val line = original.removePrefix("\uFEFF").trim()
                    .replace(Regex("""\s+"""), " ")
                if (line.isBlank()) {
                    if (paragraph.isNotEmpty()) {
                        consumer(ParsedBlock(paragraph.toString(), 0, null))
                        paragraph.clear()
                    }
                } else {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line)
                }
            }
            if (paragraph.isNotEmpty()) {
                consumer(ParsedBlock(paragraph.toString(), 0, null))
            }
        }
    }

    private fun detectCharset(file: File): Charset {
        val prefix = file.inputStream().use { input ->
            ByteArray(3).also { buffer ->
                var offset = 0
                while (offset < buffer.size) {
                    val read = input.read(buffer, offset, buffer.size - offset)
                    if (read < 0) break
                    offset += read
                }
            }
        }
        return when {
            prefix.size >= 3 &&
                prefix[0] == 0xEF.toByte() &&
                prefix[1] == 0xBB.toByte() &&
                prefix[2] == 0xBF.toByte() -> Charsets.UTF_8
            prefix.size >= 2 &&
                prefix[0] == 0xFF.toByte() &&
                prefix[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            prefix.size >= 2 &&
                prefix[0] == 0xFE.toByte() &&
                prefix[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
    }
}

internal class ImportException(val error: ImportError) : IOException()

private fun normalizeParagraph(value: String): String =
    value.trim().replace(Regex("""\s+"""), " ")

private fun closeSection(
    sections: MutableList<SectionEntity>,
    documentId: DocumentId,
    sectionIndex: Int,
    title: String?,
    firstParagraphIndex: Int,
    lastParagraphIndex: Int,
    absoluteStart: Long,
    absoluteEnd: Long
) {
    if (sectionIndex < 0) return
    sections += SectionEntity(
        documentId = documentId.value,
        sectionIndex = sectionIndex,
        title = title,
        firstParagraphIndex = firstParagraphIndex,
        lastParagraphIndex = lastParagraphIndex,
        absoluteStart = absoluteStart,
        absoluteEnd = absoluteEnd
    )
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
