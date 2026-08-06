package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController

class SkipSentence @javax.inject.Inject constructor(
    private val narrationController: NarrationController
) {
    fun execute(previous: Boolean) {
        narrationController.dispatch(
            if (previous) NarrationCommand.PreviousSentence else NarrationCommand.NextSentence
        )
    }
}
