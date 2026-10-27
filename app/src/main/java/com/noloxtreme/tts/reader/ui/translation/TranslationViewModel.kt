package com.noloxtreme.tts.reader.ui.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noloxtreme.tts.reader.domain.ContentRepository
import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.DocumentRepository
import com.noloxtreme.tts.reader.domain.TranslatedDocumentRepository
import com.noloxtreme.tts.reader.domain.TranslatedParagraph
import com.noloxtreme.tts.reader.playback.SpeechEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class TranslationViewModel @Inject constructor(
    private val speechEngine: SpeechEngine,
    private val runtime: TranslationRuntime,
    private val contentRepository: ContentRepository,
    private val documentRepository: DocumentRepository,
    private val translatedDocuments: TranslatedDocumentRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(TranslationUiState())
    val state: StateFlow<TranslationUiState> = mutableState.asStateFlow()
    private var document: Document? = null
    private var checkJob: Job? = null
    private var modelCheckJob: Job? = null
    private var translationJob: Job? = null
    private var downloadedCodes = emptySet<String>()

    fun prepare(document: Document) {
        if (this.document?.id == document.id) return
        checkJob?.cancel()
        modelCheckJob?.cancel()
        cancel()
        this.document = document
        mutableState.value = TranslationUiState()
        refresh()
    }

    /** Called after returning from Android voice settings as well as on reader resume. */
    fun refresh() = refresh(preservedError = null)

    private fun refresh(preservedError: TranslationError?) {
        if (mutableState.value.busy || document == null) return
        checkJob?.cancel()
        modelCheckJob?.cancel()
        mutableState.update { it.copy(checking = true, checkingModels = false, error = preservedError) }
        checkJob = viewModelScope.launch {
            try {
                val voices = speechEngine.availableOfflineVoices()
                val languages = speakableTranslationLanguages(voices, runtime.supportedLanguageCodes)
                val unavailable = when {
                    voices.isEmpty() -> TranslationUnavailableReason.ENGINE_UNAVAILABLE
                    languages.isEmpty() -> TranslationUnavailableReason.NO_SUPPORTED_VOICES
                    languages.size < 2 -> TranslationUnavailableReason.NEED_ANOTHER_VOICE
                    else -> null
                }
                if (unavailable == null) downloadedCodes = runtime.downloadedLanguageCodes()
                mutableState.update { current ->
                    val source = current.sourceCode?.takeIf { code -> languages.any { it.code == code } }
                        ?: canonicalTranslationLanguage(document?.languageTag)
                            ?.takeIf { code -> languages.any { it.code == code } }
                    val target = current.targetCode?.takeIf { code -> code != source && languages.any { it.code == code } }
                    current.copy(
                        checking = false,
                        unavailable = unavailable,
                        languages = languages,
                        sourceCode = source,
                        targetCode = target,
                        modelsNeeded = source == null || target == null ||
                            missingTranslationModels(source, target, downloadedCodes).isNotEmpty()
                    )
                }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                mutableState.update { it.copy(checking = false, unavailable = TranslationUnavailableReason.TRANSLATION_UNAVAILABLE) }
            } catch (_: Exception) {
                mutableState.update { it.copy(checking = false, unavailable = TranslationUnavailableReason.TRANSLATION_UNAVAILABLE) }
            } catch (_: LinkageError) {
                mutableState.update { it.copy(checking = false, unavailable = TranslationUnavailableReason.TRANSLATION_UNAVAILABLE) }
            }
        }
    }

    fun selectSource(code: String) {
        if (mutableState.value.busy || mutableState.value.languages.none { it.code == code }) return
        mutableState.update {
            it.copy(sourceCode = code, targetCode = it.targetCode?.takeUnless { target -> target == code }, error = null,
                phase = TranslationPhase.IDLE, translatedDocumentId = null)
        }
        checkModels()
    }

    fun selectTarget(code: String) {
        if (mutableState.value.busy || code == mutableState.value.sourceCode ||
            mutableState.value.languages.none { it.code == code }
        ) return
        mutableState.update { it.copy(targetCode = code, error = null, phase = TranslationPhase.IDLE, translatedDocumentId = null) }
        checkModels()
    }

    private fun checkModels() {
        modelCheckJob?.cancel()
        mutableState.update { it.copy(checkingModels = false, modelsNeeded = true) }
        val source = mutableState.value.sourceCode ?: return
        val target = mutableState.value.targetCode ?: return
        mutableState.update { it.copy(checkingModels = true) }
        modelCheckJob = viewModelScope.launch {
            try {
                downloadedCodes = runtime.downloadedLanguageCodes()
                mutableState.update { it.copy(
                    checkingModels = false,
                    modelsNeeded = missingTranslationModels(source, target, downloadedCodes).isNotEmpty()
                ) }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                mutableState.update { it.copy(checkingModels = false, error = TranslationError.DOWNLOAD_FAILED) }
            } catch (_: Exception) {
                mutableState.update { it.copy(checkingModels = false, error = TranslationError.DOWNLOAD_FAILED) }
            } catch (_: LinkageError) {
                mutableState.update { it.copy(checkingModels = false, unavailable = TranslationUnavailableReason.TRANSLATION_UNAVAILABLE) }
            }
        }
    }

    fun translate(allowMobileData: Boolean = false) {
        val snapshot = mutableState.value
        val sourceDocument = document ?: return
        val source = snapshot.sourceCode ?: return
        val target = snapshot.targetCode ?: return
        if (!snapshot.canTranslate) return
        mutableState.update { it.copy(phase = TranslationPhase.TRANSLATING, progress = 0f, error = null) }
        translationJob = viewModelScope.launch {
            var phase = TranslationPhase.TRANSLATING
            try {
                if (documentRepository.getDocument(sourceDocument.id) == null) {
                    throw TranslationFailure(TranslationError.DOCUMENT_MISSING)
                }
                // Voice data can be removed while the sheet is open. Recheck both ends before
                // spending data or time, without changing the engine's current speaking voice.
                val available = speakableTranslationLanguages(speechEngine.availableOfflineVoices(), runtime.supportedLanguageCodes)
                if (available.none { it.code == source } || available.none { it.code == target }) {
                    throw TranslationFailure(TranslationError.VOICE_UNAVAILABLE)
                }
                downloadedCodes = runtime.downloadedLanguageCodes()
                val missing = missingTranslationModels(source, target, downloadedCodes)
                runtime.preflight(missing, allowMobileData, sourceDocument.totalCharacterCount)?.let { throw TranslationFailure(it) }
                val paragraphs = contentRepository.allParagraphs(sourceDocument.id)
                if (paragraphs.isEmpty()) throw TranslationFailure(TranslationError.DOCUMENT_MISSING)
                val titles = paragraphs.map { it.sectionIndex }.distinct().associateWith { index ->
                    contentRepository.section(sourceDocument.id, index)?.title
                }
                phase = if (missing.isEmpty()) TranslationPhase.TRANSLATING else TranslationPhase.DOWNLOADING
                mutableState.update { it.copy(phase = phase, modelsNeeded = missing.isNotEmpty()) }
                val translated = ArrayList<TranslatedParagraph>(paragraphs.size)
                val translatedTitles = linkedMapOf<Int, String?>()
                withContext(Dispatchers.Default) {
                    runtime.session(source, target).use { session ->
                        session.prepare(allowMobileData)
                        phase = TranslationPhase.TRANSLATING
                        mutableState.update { it.copy(phase = phase, modelsNeeded = false) }
                        val totalCharacters = paragraphs.sumOf { it.text.length.toLong() } +
                            titles.values.sumOf { it?.length?.toLong() ?: 0L }
                        var completedCharacters = 0L
                        suspend fun translateText(text: String): String {
                            val pieces = translationChunks(text)
                            val output = ArrayList<String>(pieces.size)
                            for (piece in pieces) {
                                ensureActive()
                                if (piece.isNotBlank()) {
                                    val result = session.translate(piece).trim()
                                    if (result.isEmpty()) throw TranslationFailure(TranslationError.TRANSLATION_FAILED)
                                    output += result
                                }
                                completedCharacters += piece.length
                                mutableState.update { it.copy(progress = (completedCharacters.toFloat() / totalCharacters.coerceAtLeast(1)).coerceIn(0f, 1f)) }
                            }
                            return joinTranslatedChunks(output, target)
                        }
                        for ((index, title) in titles) {
                            translatedTitles[index] = title?.takeIf { it.isNotBlank() }?.let { translateText(it) }
                        }
                        for (paragraph in paragraphs) {
                            translated += TranslatedParagraph(paragraph.sectionIndex, translateText(paragraph.text))
                        }
                    }
                }
                // Do not publish a translated copy whose target voice disappeared mid-operation.
                val finalVoices = speakableTranslationLanguages(speechEngine.availableOfflineVoices(), runtime.supportedLanguageCodes)
                if (finalVoices.none { it.code == target }) throw TranslationFailure(TranslationError.VOICE_UNAVAILABLE)
                phase = TranslationPhase.SAVING
                mutableState.update { it.copy(phase = phase, progress = 1f) }
                val documentId = translatedDocuments.save(sourceDocument, target, translated, translatedTitles)
                mutableState.update { it.copy(phase = TranslationPhase.SUCCESS, translatedDocumentId = documentId) }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                mutableState.update { it.copy(phase = TranslationPhase.IDLE, error = errorForPhase(phase)) }
            } catch (failure: TranslationFailure) {
                mutableState.update { it.copy(phase = TranslationPhase.IDLE, error = failure.reason) }
                if (failure.reason == TranslationError.VOICE_UNAVAILABLE) {
                    refresh(preservedError = failure.reason)
                }
            } catch (_: Exception) {
                mutableState.update { it.copy(phase = TranslationPhase.IDLE, error = errorForPhase(phase)) }
            } catch (_: LinkageError) {
                mutableState.update { it.copy(phase = TranslationPhase.IDLE, unavailable = TranslationUnavailableReason.TRANSLATION_UNAVAILABLE) }
            }
        }
    }

    fun cancel() {
        translationJob?.cancel()
        mutableState.update { it.copy(phase = TranslationPhase.IDLE, progress = 0f, error = null) }
    }

    fun clearResult() {
        mutableState.update { it.copy(phase = TranslationPhase.IDLE, progress = 0f, error = null, translatedDocumentId = null) }
    }
}

private fun errorForPhase(phase: TranslationPhase): TranslationError = when (phase) {
    TranslationPhase.DOWNLOADING -> TranslationError.DOWNLOAD_FAILED
    TranslationPhase.SAVING -> TranslationError.SAVE_FAILED
    else -> TranslationError.TRANSLATION_FAILED
}
