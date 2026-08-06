package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController

class RestartCompletedDocument @javax.inject.Inject constructor(
    private val narrationController: NarrationController
) {
    fun execute() {
        narrationController.dispatch(NarrationCommand.RestartCompleted)
    }
}
