package com.noloxtreme.tts.reader.ui.learning

import androidx.lifecycle.ViewModelStore
import androidx.paging.PagingData
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentId
import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.Paragraph
import com.noloxtreme.tts.reader.domain.Section
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageLearningViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val original = Document(DocumentId("original"), "Book", "book.txt", "text/plain", "hash", "en", 3000, 1, 0, null)
    private val translated = original.copy(id = DocumentId("translated"), languageTag = "fr", translationSourceId = original.id)
    private val documents = FakeDocuments(original)
    private val content = FakeContent()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private fun TestScope.model(): LanguageLearningViewModel {
        val model = LanguageLearningViewModel(documents, content)
        store.put("learning", model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.passage.collect() }
        return model
    }

    @Test
    fun `checks original availability without reading any paragraphs until enabled`() = runTest {
        val model = model()
        model.prepare(translated)
        runCurrent()
        assertEquals(LearningSource.Available(original), model.source.value)
        assertEquals(LearningPassageState.Idle, model.passage.value)
        assertTrue(content.requests.isEmpty())
    }

    @Test
    fun `ordinary books never offer a source or fetch a pair`() = runTest {
        val model = model()
        model.prepare(original)
        model.showParagraph(0)
        runCurrent()
        assertEquals(LearningSource.None, model.source.value)
        assertEquals(LearningPassageState.Idle, model.passage.value)
        assertTrue(content.requests.isEmpty())
    }

    @Test
    fun `loads exactly the current paragraph pair and reuses it for word updates`() = runTest {
        val model = model()
        model.prepare(translated)
        model.showParagraph(7)
        runCurrent()
        val ready = model.passage.value as LearningPassageState.Ready
        assertEquals(7, ready.passage.original.paragraphIndex)
        assertEquals(7, ready.passage.translated.paragraphIndex)
        assertEquals("en", ready.passage.originalLanguage)
        assertEquals("fr", ready.passage.translatedLanguage)
        repeat(30) { model.showParagraph(7); runCurrent() }
        assertEquals(listOf(translated.id to 7, original.id to 7), content.requests)
    }

    @Test
    fun `deleting the original invalidates an open view without modifying the translation`() = runTest {
        val model = model()
        model.prepare(translated)
        model.showParagraph(1)
        runCurrent()
        documents.source.value = null
        runCurrent()
        assertEquals(LearningSource.Missing, model.source.value)
        assertEquals(LearningPassageState.OriginalMissing, model.passage.value)
        assertEquals(2, content.requests.size)
    }

    @Test
    fun `missing or mismatched paragraphs have an explicit fallback`() = runTest {
        val model = model()
        content.missing = original.id
        model.prepare(translated)
        model.showParagraph(1)
        runCurrent()
        assertEquals(LearningPassageState.PassageMissing, model.passage.value)
        content.missing = null
        content.mismatchedSection = true
        model.showParagraph(2)
        runCurrent()
        assertEquals(LearningPassageState.PassageMissing, model.passage.value)
    }

    @Test
    fun `a seek cancels a slow old load so an obsolete pair cannot replace the new one`() = runTest {
        val model = model()
        val gate = CompletableDeferred<Unit>()
        content.gate = 1 to gate
        model.prepare(translated)
        model.showParagraph(1)
        runCurrent()
        assertEquals(LearningPassageState.Loading, model.passage.value)
        model.showParagraph(8)
        runCurrent()
        assertEquals(8, (model.passage.value as LearningPassageState.Ready).passage.original.paragraphIndex)
        gate.complete(Unit)
        runCurrent()
        assertEquals(8, (model.passage.value as LearningPassageState.Ready).passage.original.paragraphIndex)
        assertTrue(original.id to 1 !in content.requests)
    }

    @Test
    fun `leaving learning mode cancels loads and releases the passage`() = runTest {
        val model = model()
        val gate = CompletableDeferred<Unit>()
        content.gate = 1 to gate
        model.prepare(translated)
        model.showParagraph(1)
        runCurrent()
        model.showParagraph(null)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(LearningPassageState.Idle, model.passage.value)
        assertEquals(listOf(translated.id to 1), content.requests)
    }

    @Test
    fun `failed paragraph reads can be retried in place`() = runTest {
        val model = model()
        content.fail = true
        model.prepare(translated)
        model.showParagraph(4)
        runCurrent()
        assertEquals(LearningPassageState.Failed, model.passage.value)
        content.fail = false
        model.retry()
        runCurrent()
        assertEquals(4, (model.passage.value as LearningPassageState.Ready).passage.original.paragraphIndex)
    }

    @Test
    fun `switching back to an ordinary book clears the original and pair`() = runTest {
        val model = model()
        model.prepare(translated)
        model.showParagraph(1)
        runCurrent()
        model.prepare(original)
        runCurrent()
        assertEquals(LearningSource.None, model.source.value)
        assertEquals(LearningPassageState.Idle, model.passage.value)
    }

    private class FakeDocuments(original: Document) : DocumentRepository {
        val source = MutableStateFlow<Document?>(original)
        override fun observeDocument(id: DocumentId): Flow<Document?> = source
        override fun observeLibrary() = flowOf(emptyList<Document>())
        override suspend fun getDocument(id: DocumentId) = source.value
        override suspend fun updateLastOpened(id: DocumentId, epochMillis: Long) = error("Learning must not change reading history")
        override suspend fun deleteDocument(id: DocumentId) = error("Learning must not delete documents")
    }

    private class FakeContent : ContentRepository {
        val requests = mutableListOf<Pair<DocumentId, Int>>()
        var gate: Pair<Int, CompletableDeferred<Unit>>? = null
        var missing: DocumentId? = null
        var mismatchedSection = false
        var fail = false
        override suspend fun paragraph(id: DocumentId, index: Int): Paragraph? {
            requests += id to index
            gate?.takeIf { it.first == index }?.second?.await()
            if (fail) error("Unavailable")
            if (id == missing) return null
            val text = "Paragraph $index in ${id.value}"
            return Paragraph(id, index, if (mismatchedSection && id.value == "original") 1 else 0, text, 0, text.length.toLong())
        }
        override fun pagedParagraphs(id: DocumentId, initialParagraphIndex: Int): Flow<PagingData<Paragraph>> = error("No paging needed")
        override suspend fun allParagraphs(id: DocumentId): List<Paragraph> = error("Never load the whole book")
        override suspend fun paragraphContaining(id: DocumentId, absoluteOffset: Long): Paragraph? = error("Use paragraph correspondence")
        override suspend fun section(id: DocumentId, index: Int): Section? = error("No section load needed")
        override suspend fun sentenceBefore(id: DocumentId, position: DocumentPosition) = error("Never seek the source")
        override suspend fun sentenceAfter(id: DocumentId, position: DocumentPosition) = error("Never seek the source")
    }
}
