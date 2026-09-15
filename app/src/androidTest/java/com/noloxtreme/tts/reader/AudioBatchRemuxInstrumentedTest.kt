package com.noloxtreme.tts.reader

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.noloxtreme.tts.reader.export.M4aAssembler
import com.noloxtreme.tts.reader.export.M4aRemuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the same encode-each-batch then remux flow used for long exports
 * against the device's actual AAC encoder and MP4 muxer.
 */
@RunWith(AndroidJUnit4::class)
class AudioBatchRemuxInstrumentedTest {

    @Test
    fun separatelyEncodedAudioBatchesProduceOnePlayableAacTrack() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "remux-test-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        try {
            val assembler = M4aAssembler(context)
            val parts = (0 until BATCH_COUNT).map { batchIndex ->
                val wav = File(directory, "batch-$batchIndex.wav")
                writePcm16Wav(wav, frequencyHz = 220.0 + batchIndex * 55.0)
                File(directory, "batch-$batchIndex.m4a").also { part ->
                    assembler.assemble(listOf(wav), part).getOrThrow()
                }
            }
            val output = File(directory, "export.m4a")

            M4aRemuxer().remux(parts, output).getOrThrow()

            assertAacSamplesAreStrictlyIncreasing(output)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun assertAacSamplesAreStrictlyIncreasing(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val audioTrack = (0 until extractor.trackCount).first { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }
            val format = extractor.getTrackFormat(audioTrack)
            assertEquals(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                format.getString(MediaFormat.KEY_MIME)
            )
            assertTrue(
                "The remuxed track must retain every batch's duration",
                format.getLong(MediaFormat.KEY_DURATION) >=
                    BATCH_COUNT * DURATION_SECONDS * MICROS_PER_SECOND * 9 / 10
            )
            extractor.selectTrack(audioTrack)
            val buffer = ByteBuffer.allocate(SAMPLE_BUFFER_SIZE)
            var sampleCount = 0
            var previousTimestampUs = -1L
            while (true) {
                buffer.clear()
                if (extractor.readSampleData(buffer, 0) < 0) break
                val timestampUs = extractor.sampleTime
                assertTrue("AAC timestamps must increase", timestampUs > previousTimestampUs)
                previousTimestampUs = timestampUs
                sampleCount++
                extractor.advance()
            }
            assertTrue("Expected samples from every encoded batch", sampleCount > BATCH_COUNT)
        } finally {
            extractor.release()
        }
    }

    private fun writePcm16Wav(file: File, frequencyHz: Double) {
        val dataSize = SAMPLE_RATE * DURATION_SECONDS * PCM_16_BYTES
        RandomAccessFile(file, "rw").use { output ->
            output.writeBytes("RIFF")
            output.writeIntLe(36 + dataSize)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeIntLe(16)
            output.writeShortLe(1) // PCM
            output.writeShortLe(1) // mono
            output.writeIntLe(SAMPLE_RATE)
            output.writeIntLe(SAMPLE_RATE * PCM_16_BYTES)
            output.writeShortLe(PCM_16_BYTES)
            output.writeShortLe(16)
            output.writeBytes("data")
            output.writeIntLe(dataSize)
            repeat(SAMPLE_RATE * DURATION_SECONDS) { sampleIndex ->
                val phase = 2.0 * Math.PI * frequencyHz * sampleIndex / SAMPLE_RATE
                output.writeShortLe((kotlin.math.sin(phase) * Short.MAX_VALUE).toInt())
            }
        }
    }

    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(value and 0xFF)
        write(value ushr 8 and 0xFF)
    }

    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(value and 0xFF)
        write(value ushr 8 and 0xFF)
        write(value ushr 16 and 0xFF)
        write(value ushr 24 and 0xFF)
    }

    private companion object {
        const val BATCH_COUNT = 3
        const val SAMPLE_RATE = 22_050
        const val DURATION_SECONDS = 1
        const val PCM_16_BYTES = 2
        const val SAMPLE_BUFFER_SIZE = 64 * 1024
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
