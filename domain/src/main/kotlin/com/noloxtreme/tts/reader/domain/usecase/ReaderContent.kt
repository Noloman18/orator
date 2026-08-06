package com.noloxtreme.tts.reader.domain.usecase

import com.noloxtreme.tts.reader.domain.Document
import com.noloxtreme.tts.reader.domain.ReadingProgress

data class ReaderContent(
    val document: Document?,
    val progress: ReadingProgress?
)
