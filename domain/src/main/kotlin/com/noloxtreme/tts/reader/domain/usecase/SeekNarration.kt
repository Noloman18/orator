package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.DocumentPosition
import com.noloxtreme.tts.reader.domain.NarrationCommand
import com.noloxtreme.tts.reader.domain.NarrationController

class SeekNarration @javax.inject.Inject constructor(
    private val narrationController: NarrationController
) {
    fun execute(position: DocumentPosition) {
        narrationController.dispatch(NarrationCommand.SeekTo(position))
    }
}
