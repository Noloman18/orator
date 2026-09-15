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
    fun `different source WAV sample rates are normalized before merge`() = runTest {
        val directory = Files.createTempDirectory("orator-wav-test").toFile()
        try {
            val first = File(directory, "first.wav").also {
                writePcmWav(it, byteArrayOf(1, 2, 3, 4), sampleRate = 22_050)
            }
            val second = File(directory, "second.wav").also {
                writePcmWav(it, byteArrayOf(5, 6, 7, 8), sampleRate = 16_000)
            }

            val output = File(directory, "speech.wav")
            WavConcatenator.concatenate(listOf(first, second), output)

            assertTrue(WavConcatenator.isSupportedWav(output))
            RandomAccessFile(output, "r").use { file ->
                file.seek(24)
                assertEquals(22_050, file.readIntLe())
                file.seek(22)
                assertEquals(1, file.readUnsignedByte() or (file.readUnsignedByte() shl 8))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `eight bit TTS WAV is normalized to sixteen bit PCM`() = runTest {
        val directory = Files.createTempDirectory("orator-wav-test").toFile()
        try {
            val input = File(directory, "speech-8bit.wav").also {
                writePcm8Wav(it, byteArrayOf(0, 128.toByte(), 255.toByte()))
            }
            val output = File(directory, "speech.wav")

            WavConcatenator.concatenate(listOf(input), output)

            RandomAccessFile(output, "r").use { file ->
                file.seek(34)
                assertEquals(16, file.readUnsignedByte() or (file.readUnsignedByte() shl 8))
                file.seek(44)
                val data = ByteArray(6)
                file.readFully(data)
                assertArrayEquals(
                    byteArrayOf(0, 128.toByte(), 0, 0, 0, 127),
                    data
                )
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `floating point TTS WAV is normalized to sixteen bit PCM`() = runTest {
        val directory = Files.createTempDirectory("orator-wav-test").toFile()
        try {
            val input = File(directory, "speech-float.wav").also {
                writeFloatWav(it, floatArrayOf(-1f, 0.5f))
            }
            val output = File(directory, "speech.wav")

            WavConcatenator.concatenate(listOf(input), output)

            RandomAccessFile(output, "r").use { file ->
                file.seek(44)
                val data = ByteArray(4)
                file.readFully(data)
                assertArrayEquals(
                    byteArrayOf(1, 128.toByte(), 255.toByte(), 63),
                    data
                )
            }
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

    private fun writePcm8Wav(file: File, data: ByteArray, sampleRate: Int = 22_050) {
        RandomAccessFile(file, "rw").use { output ->
            output.writeBytes("RIFF")
            output.writeIntLe(36 + data.size)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeIntLe(16)
            output.writeShortLe(1) // PCM
            output.writeShortLe(1) // mono
            output.writeIntLe(sampleRate)
            output.writeIntLe(sampleRate)
            output.writeShortLe(1)
            output.writeShortLe(8)
            output.writeBytes("data")
            output.writeIntLe(data.size)
            output.write(data)
        }
    }

    private fun writeFloatWav(file: File, samples: FloatArray, sampleRate: Int = 22_050) {
        RandomAccessFile(file, "rw").use { output ->
            val dataSize = samples.size * 4
            output.writeBytes("RIFF")
            output.writeIntLe(36 + dataSize)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeIntLe(16)
            output.writeShortLe(3) // IEEE float
            output.writeShortLe(1) // mono
            output.writeIntLe(sampleRate)
            output.writeIntLe(sampleRate * 4)
            output.writeShortLe(4)
            output.writeShortLe(32)
            output.writeBytes("data")
            output.writeIntLe(dataSize)
            samples.forEach { sample -> output.writeIntLe(sample.toBits()) }
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

    private fun RandomAccessFile.readIntLe(): Int =
        readUnsignedByte() or
            (readUnsignedByte() shl 8) or
            (readUnsignedByte() shl 16) or
            (readUnsignedByte() shl 24)
}
