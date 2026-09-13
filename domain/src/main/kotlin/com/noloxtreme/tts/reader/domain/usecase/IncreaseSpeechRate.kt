package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController

/** Cycles the speech rate from normal speed through 2×, then back to normal. */
class IncreaseSpeechRate @javax.inject.Inject constructor(
    private val narrationController: NarrationController
) {
    fun execute() = narrationController.dispatch(NarrationCommand.IncreaseSpeechRate)
}
