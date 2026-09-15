package com.noloxtreme.tts.reader.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExportBatcherTest {

    @Test
    fun `counts whitespace separated words without allocating a split list`() {
        assertEquals(4, AudioExportBatcher.wordCount(" One\n two\tthree   four "))
        assertEquals(0, AudioExportBatcher.wordCount(" \t\n "))
    }

    @Test
    fun `ends a batch at the word target or WAV size cap`() {
        assertFalse(AudioExportBatcher.shouldFinishBatch(9_999, 0L))
        assertTrue(AudioExportBatcher.shouldFinishBatch(10_000, 0L))
        assertTrue(
            AudioExportBatcher.shouldFinishBatch(
                1,
                AudioExportBatcher.MAX_WAV_BYTES_PER_BATCH
            )
        )
    }
}
