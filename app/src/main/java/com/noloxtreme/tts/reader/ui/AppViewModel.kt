package com.noloxtreme.tts.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noloxtreme.tts.reader.domain.OratorSettings
import com.noloxtreme.tts.reader.domain.ReviewPromptRepository
import com.noloxtreme.tts.reader.domain.usecase.ObserveSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    observeSettings: ObserveSettings,
    private val reviewPromptRepository: ReviewPromptRepository
) : ViewModel() {
    val settings: StateFlow<OratorSettings> = observeSettings.execute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OratorSettings())

    private val mutableShowReviewPrompt = MutableStateFlow(false)
    val showReviewPrompt: StateFlow<Boolean> = mutableShowReviewPrompt.asStateFlow()
    private var reviewPromptCompletedInSession = false

    fun recordLibraryLanding() {
        if (mutableShowReviewPrompt.value || reviewPromptCompletedInSession) return
        viewModelScope.launch {
            try {
                if (
                    reviewPromptRepository.recordLibraryLanding() &&
                    !reviewPromptCompletedInSession
                ) {
                    mutableShowReviewPrompt.value = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // A storage failure should not prevent the library from opening.
            }
        }
    }

    fun remindForReviewLater() = resolveReviewPrompt {
        reviewPromptRepository.remindLater()
    }

    fun completeReviewPrompt() {
        // Keep the terminal decision in memory immediately while DataStore persists it.
        reviewPromptCompletedInSession = true
        resolveReviewPrompt { reviewPromptRepository.complete() }
    }

    private fun resolveReviewPrompt(action: suspend () -> Unit) {
        mutableShowReviewPrompt.value = false
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The in-memory prompt is dismissed even if preferences cannot be written.
            }
        }
    }
}
