package com.noloxtreme.tts.reader.domain

import com.noloxtreme.tts.reader.domain.usecase.DeleteDocument
import com.noloxtreme.tts.reader.domain.usecase.ImportDocument
import com.noloxtreme.tts.reader.domain.usecase.ObserveLibrary
import com.noloxtreme.tts.reader.domain.usecase.ObserveReaderContent
import com.noloxtreme.tts.reader.domain.usecase.ObserveReaderPosition
import com.noloxtreme.tts.reader.domain.usecase.ObserveReadingProgress
import com.noloxtreme.tts.reader.domain.usecase.ObserveSettings
import com.noloxtreme.tts.reader.domain.usecase.OpenDocument
import com.noloxtreme.tts.reader.domain.usecase.PauseNarration
import com.noloxtreme.tts.reader.domain.usecase.RestartCompletedDocument
import com.noloxtreme.tts.reader.domain.usecase.ReaderContent
import com.noloxtreme.tts.reader.domain.usecase.SaveReaderPosition
import com.noloxtreme.tts.reader.domain.usecase.SeekNarration
import com.noloxtreme.tts.reader.domain.usecase.SkipSentence
import com.noloxtreme.tts.reader.domain.usecase.StartOrResumeNarration
import com.noloxtreme.tts.reader.domain.usecase.UpdateReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCaseDelegationTest {
    private val document = Document(
        id = DocumentId("book"),
        title = "Book",
        originalFileName = "book.txt",
        mimeType = "text/plain",
        sha256 = "a".repeat(64),
        languageTag = "en-US",
        totalCharacterCount = 10,
        sectionCount = 1,
        importedAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null
    )
    private val position = DocumentPosition(0, 3, 3)

    @Test
    fun narrationUseCasesDispatchExpectedCommands() {
        val controller = RecordingNarrationController()

        PauseNarration(controller).execute()
        StartOrResumeNarration(controller).execute()
        RestartCompletedDocument(controller).execute()
        SeekNarration(controller).execute(position)
        SkipSentence(controller).execute(previous = true)
        SkipSentence(controller).execute(previous = false)
        SkipSentence(controller).execute(previous = false, count = 5)

        assertEquals(
            listOf(
                NarrationCommand.Pause(),
                NarrationCommand.Play,
                NarrationCommand.RestartCompleted,
                NarrationCommand.SeekTo(position),
                NarrationCommand.PreviousSentence,
                NarrationCommand.NextSentence,
                NarrationCommand.JumpSentences(previous = false, count = 5)
            ),
            controller.commands
        )
        assertThrows(IllegalArgumentException::class.java) {
            SkipSentence(controller).execute(previous = true, count = 0)
        }
    }

    @Test
    fun repositoryUseCasesDelegateAndCombineData() = runTest {
        val documents = RecordingDocumentRepository(document)
        val progress = RecordingProgressRepository(
            ReadingProgress(document.id, position, updatedAtEpochMillis = 4, completed = false)
        )

        assertEquals(listOf(document), ObserveLibrary(documents).execute().first())
        assertEquals(position, ObserveReadingProgress(progress).execute(document.id).first()?.position)
        assertEquals(
            ReaderContent(document, progress.value),
            ObserveReaderContent(documents, progress).execute(document.id).first()
        )

        OpenDocument(documents, object : TimeProvider {
            override fun nowEpochMillis() = 99L
        }).execute(document.id)
        DeleteDocument(documents).execute(document.id)

        assertEquals(99L, documents.lastOpenedAt)
        assertEquals(document.id, documents.deletedId)
    }

    @Test
    fun importUseCaseEmitsImporterStatesAndCancels() = runTest {
        val importer = RecordingImporter()
        val source = ImportSource("handle", "book.txt", "text/plain", 10)

        assertEquals(
            listOf(ImportState.Parsing, ImportState.Success(document.id)),
            ImportDocument(importer).execute(source).toList()
        )
        ImportDocument(importer).cancelActiveImport()

        assertTrue(importer.cancelled)
        assertEquals(source, importer.importedSource)
    }

    @Test
    fun readerSettingsUseCasesObserveAndUpdateAllReaderFields() = runTest {
        val repository = RecordingSettingsRepository()

        assertEquals(OratorSettings(), ObserveSettings(repository).execute().first())
        UpdateReaderSettings(repository).execute(
            readerFontSizeSp = 24,
            lineHeight = LineHeightPreference.SPACIOUS,
            followSpokenText = false,
            theme = ThemePreference.DARK
        )

        assertEquals(
            OratorSettings(
                readerFontSizeSp = 24,
                lineHeight = LineHeightPreference.SPACIOUS,
                followSpokenText = false,
                theme = ThemePreference.DARK
            ),
            repository.value
        )
        assertFalse(repository.value.followSpokenText)
    }

    @Test
    fun readerPositionUseCasesObserveAndSavePositions() = runTest {
        val repository = RecordingReaderPositionRepository()

        assertNull(ObserveReaderPosition(repository).execute(document.id).first())

        SaveReaderPosition(repository).execute(document.id, ReaderPosition(2, 5))

        assertEquals(
            ReaderPosition(2, 5),
            ObserveReaderPosition(repository).execute(document.id).first()
        )
        assertEquals(document.id, repository.savedId)
    }

    private class RecordingNarrationController : NarrationController {
        override val state = MutableStateFlow<NarrationState>(NarrationState.Idle)
        val commands = mutableListOf<NarrationCommand>()

        override fun dispatch(command: NarrationCommand) {
            commands += command
        }
    }

    private class RecordingDocumentRepository(
        private val value: Document
    ) : DocumentRepository {
        private val documents = MutableStateFlow(listOf(value))
        var lastOpenedAt: Long? = null
        var deletedId: DocumentId? = null

        override fun observeLibrary(): Flow<List<Document>> = documents

        override fun observeDocument(id: DocumentId): Flow<Document?> =
            flowOf(documents.value.firstOrNull { it.id == id })

        override suspend fun getDocument(id: DocumentId): Document? =
            documents.value.firstOrNull { it.id == id }

        override suspend fun updateLastOpened(id: DocumentId, epochMillis: Long) {
            lastOpenedAt = epochMillis
        }

        override suspend fun deleteDocument(id: DocumentId) {
            deletedId = id
        }
    }

    private class RecordingProgressRepository(
        val value: ReadingProgress
    ) : ProgressRepository {
        override fun observeProgress(id: DocumentId): Flow<ReadingProgress?> = flowOf(value)

        override suspend fun getProgress(id: DocumentId): ReadingProgress = value

        override suspend fun saveProgress(progress: ReadingProgress) = Unit
    }

    private class RecordingReaderPositionRepository : ReaderPositionRepository {
        private val positions = MutableStateFlow<ReaderPosition?>(null)
        var savedId: DocumentId? = null

        override fun observe(id: DocumentId): Flow<ReaderPosition?> = positions

        override suspend fun save(id: DocumentId, position: ReaderPosition) {
            savedId = id
            positions.value = position
        }
    }

    private class RecordingImporter : DocumentImporter {
        var importedSource: ImportSource? = null
        var cancelled = false

        override fun import(source: ImportSource): Flow<ImportState> {
            importedSource = source
            return flowOf(ImportState.Parsing, ImportState.Success(DocumentId("book")))
        }

        override suspend fun cancelActiveImport() {
            cancelled = true
        }
    }

    private class RecordingSettingsRepository : SettingsRepository {
        private val settings = MutableStateFlow(OratorSettings())
        val value: OratorSettings get() = settings.value

        override fun observeSettings(): Flow<OratorSettings> = settings

        override suspend fun updateSettings(transform: (OratorSettings) -> OratorSettings) {
            settings.value = transform(settings.value)
        }
    }
}
