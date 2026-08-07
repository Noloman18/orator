package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController

class SkipSentence @javax.inject.Inject constructor(
    private val narrationController: NarrationController
) {
    fun execute(previous: Boolean, count: Int = 1) {
        require(count > 0)
        narrationController.dispatch(
            when {
                count == 1 && previous -> NarrationCommand.PreviousSentence
                count == 1 -> NarrationCommand.NextSentence
                else -> NarrationCommand.JumpSentences(previous, count)
            }
        )
    }
}
