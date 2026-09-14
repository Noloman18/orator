package com.noloxtreme.tts.reader.export

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WavConcatenatorTest {

    @Test
    fun `concatenation preserves the PCM data from every segment`() = runTest {
        val directory = Files.createTempDirectory("orator-wav-test").toFile()
        try {
            val first = File(directory, "first.wav").also {
                writePcmWav(it, byteArrayOf(1, 2, 3, 4))
            }
            val second = File(directory, "second.wav").also {
                writePcmWav(it, byteArrayOf(5, 6, 7, 8, 9, 10))
            }
            val output = File(directory, "speech.wav")

            WavConcatenator.concatenate(listOf(first, second), output)

            assertTrue(
                "length=${output.length()}, detail=${WavConcatenator.validationError(output)}",
                WavConcatenator.isSupportedWav(output)
            )
            assertEquals(44L + 10, output.length())
            RandomAccessFile(output, "r").use { file ->
                file.seek(44)
                val data = ByteArray(10)
                file.readFully(data)
                assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), data)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `different source WAV formats cannot be merged`() = runTest {
        val directory = Files.createTempDirectory("orator-wav-test").toFile()
        try {
            val first = File(directory, "first.wav").also {
                writePcmWav(it, byteArrayOf(1, 2, 3, 4), sampleRate = 22_050)
            }
            val second = File(directory, "second.wav").also {
                writePcmWav(it, byteArrayOf(5, 6, 7, 8), sampleRate = 16_000)
            }

            val result = runCatching {
                WavConcatenator.concatenate(listOf(first, second), File(directory, "speech.wav"))
            }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `truncated files are not treated as resumable WAV segments`() {
        val directory = Files.createTempDirectory("orator-wav-test").toFile()
        try {
            val truncated = File(directory, "truncated.wav")
            truncated.writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46))

            assertFalse(WavConcatenator.isSupportedWav(truncated))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun writePcmWav(file: File, data: ByteArray, sampleRate: Int = 22_050) {
        RandomAccessFile(file, "rw").use { output ->
            output.writeBytes("RIFF")
            output.writeIntLe(36 + data.size)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeIntLe(16)
            output.writeShortLe(1) // PCM
            output.writeShortLe(1) // mono
            output.writeIntLe(sampleRate)
            output.writeIntLe(sampleRate * 2)
            output.writeShortLe(2)
            output.writeShortLe(16)
            output.writeBytes("data")
            output.writeIntLe(data.size)
            output.write(data)
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
}
