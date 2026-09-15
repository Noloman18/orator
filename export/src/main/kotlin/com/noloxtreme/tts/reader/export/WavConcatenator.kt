package com.noloxtreme.tts.reader.export

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Streams TTS WAV files into one canonical 22.05 kHz mono 16-bit PCM WAV
 * without loading audio data into memory. Android TTS engines are allowed to
 * produce different PCM formats, sample rates and channel counts; every batch
 * must be identical so its encoded AAC samples can be remuxed into one file.
 */
internal object WavConcatenator {

    suspend fun concatenate(
        wavFiles: List<File>,
        outputFile: File,
        onProgress: suspend (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(wavFiles.isNotEmpty()) { "No WAV segments were supplied" }
        val inputs = wavFiles.map(::inspect)
        val outputFormat = WavFormat.exportPcm16()
        val dataSize = inputs.sumOf { it.normalizedFrameCount * outputFormat.blockAlign }
        require(dataSize <= MAX_WAV_DATA_SIZE) { "The synthesized audio is too long to package" }

        outputFile.parentFile?.mkdirs()
        val temporary = File(outputFile.parentFile, "${outputFile.name}.part")
        temporary.delete()
        var completed = false
        try {
            RandomAccessFile(temporary, "rw").use { output ->
                output.setLength(HEADER_SIZE.toLong())
                output.seek(HEADER_SIZE.toLong())
                var writtenDataSize = 0L
                inputs.forEachIndexed { index, input ->
                    RandomAccessFile(input.file, "r").use { source ->
                        source.seek(input.dataOffset)
                        copy(source, output, input, outputFormat)
                    }
                    writtenDataSize += input.normalizedFrameCount * outputFormat.blockAlign
                    onProgress(((index + 1) * 100 / inputs.size).coerceIn(0, 100))
                }
                writeHeader(output, outputFormat, writtenDataSize)
            }
            replace(outputFile, temporary)
            completed = true
        } finally {
            if (!completed) temporary.delete()
        }
    }

    fun isSupportedWav(file: File): Boolean = validationError(file) == null

    /** Size of this source after normalization to Media3-compatible 16-bit PCM. */
    fun normalizedPcm16DataSize(file: File): Long {
        val input = inspect(file)
        return input.normalizedFrameCount * WavFormat.exportPcm16().blockAlign
    }

    /** Returns a user-safe reason when [file] cannot be used as a source WAV. */
    fun validationError(file: File): String? = runCatching { inspect(file) }
        .exceptionOrNull()
        ?.message

    private fun inspect(file: File): WavInput {
        require(file.isFile && file.length() >= HEADER_SIZE) { "Invalid WAV segment: ${file.name}" }
        RandomAccessFile(file, "r").use { input ->
            require(input.readTag() == RIFF) { "Invalid WAV header in ${file.name}" }
            input.readUnsignedIntLe()
            require(input.readTag() == WAVE) { "Invalid WAV header in ${file.name}" }

            var format: WavFormat? = null
            var dataOffset = -1L
            var dataSize = -1L
            while (input.filePointer + CHUNK_HEADER_SIZE <= input.length()) {
                val chunkType = input.readTag()
                val chunkSize = input.readUnsignedIntLe()
                val chunkStart = input.filePointer
                val chunkEnd = chunkStart + chunkSize
                require(chunkEnd <= input.length()) { "Truncated WAV segment: ${file.name}" }
                when (chunkType) {
                    FORMAT -> {
                        require(chunkSize >= PCM_FORMAT_SIZE) { "Invalid WAV format in ${file.name}" }
                        format = WavFormat(
                            audioFormat = input.readUnsignedShortLe(),
                            channelCount = input.readUnsignedShortLe(),
                            sampleRate = input.readUnsignedIntLe(),
                            byteRate = input.readUnsignedIntLe(),
                            blockAlign = input.readUnsignedShortLe(),
                            bitsPerSample = input.readUnsignedShortLe()
                        )
                    }
                    DATA -> {
                        dataOffset = chunkStart
                        dataSize = chunkSize
                    }
                }
                input.seek(chunkEnd + chunkSize % 2)
            }

            val resolvedFormat = requireNotNull(format) { "Missing WAV format in ${file.name}" }
            require(resolvedFormat.audioFormat in SUPPORTED_WAV_FORMATS) {
                "Unsupported WAV format in ${file.name}"
            }
            require(resolvedFormat.isConvertibleToPcm16()) {
                "Unsupported PCM encoding in ${file.name}"
            }
            require(
                resolvedFormat.sampleRate > 0L &&
                    resolvedFormat.channelCount in 1..2 &&
                    resolvedFormat.blockAlign > 0
            ) {
                "Invalid WAV format in ${file.name}"
            }
            require(resolvedFormat.blockAlign == resolvedFormat.bytesPerFrame) {
                "Invalid WAV block alignment in ${file.name}"
            }
            require(resolvedFormat.byteRate == resolvedFormat.sampleRate * resolvedFormat.blockAlign) {
                "Invalid WAV byte rate in ${file.name}"
            }
            require(dataOffset >= 0 && dataSize > 0 && dataSize % resolvedFormat.blockAlign == 0L) {
                "Invalid WAV audio data in ${file.name}"
            }
            return WavInput(file, resolvedFormat, dataOffset, dataSize)
        }
    }

    private fun copy(
        source: RandomAccessFile,
        output: RandomAccessFile,
        input: WavInput,
        outputFormat: WavFormat
    ) {
        var remaining = input.dataSize
        val inputBytesPerFrame = input.format.blockAlign
        val inputBufferSize = COPY_BUFFER_SIZE - (COPY_BUFFER_SIZE % inputBytesPerFrame)
        val inputBuffer = ByteArray(inputBufferSize)
        val maxOutputFrames = ((inputBufferSize / inputBytesPerFrame).toLong() *
            outputFormat.sampleRate + input.format.sampleRate - 1) / input.format.sampleRate
        val outputBuffer = ByteArray((maxOutputFrames * outputFormat.blockAlign).toInt())
        var resampleRemainder = 0L
        while (remaining > 0) {
            val requested = minOf(inputBuffer.size.toLong(), remaining).toInt()
            val read = source.read(inputBuffer, 0, requested)
            if (read <= 0) throw IOException("Unexpected end of WAV data")
            require(read % inputBytesPerFrame == 0) { "Incomplete WAV frame" }
            var inputOffset = 0
            var outputOffset = 0
            repeat(read / inputBytesPerFrame) {
                var mixedSample = 0
                repeat(input.format.channelCount) {
                    mixedSample += input.format.readSample(inputBuffer, inputOffset)
                    inputOffset += input.format.bytesPerSample
                }
                val monoSample = mixedSample / input.format.channelCount
                resampleRemainder += outputFormat.sampleRate
                while (resampleRemainder >= input.format.sampleRate) {
                    outputBuffer[outputOffset++] = (monoSample and 0xFF).toByte()
                    outputBuffer[outputOffset++] = (monoSample ushr 8 and 0xFF).toByte()
                    resampleRemainder -= input.format.sampleRate
                }
            }
            output.write(outputBuffer, 0, outputOffset)
            remaining -= read
        }
    }

    private fun writeHeader(output: RandomAccessFile, format: WavFormat, dataSize: Long) {
        output.seek(0)
        output.writeTag(RIFF)
        output.writeUnsignedIntLe(dataSize + HEADER_SIZE - 8)
        output.writeTag(WAVE)
        output.writeTag(FORMAT)
        output.writeUnsignedIntLe(PCM_FORMAT_SIZE.toLong())
        output.writeUnsignedShortLe(format.audioFormat)
        output.writeUnsignedShortLe(format.channelCount)
        output.writeUnsignedIntLe(format.sampleRate)
        output.writeUnsignedIntLe(format.byteRate)
        output.writeUnsignedShortLe(format.blockAlign)
        output.writeUnsignedShortLe(format.bitsPerSample)
        output.writeTag(DATA)
        output.writeUnsignedIntLe(dataSize)
    }

    private fun replace(outputFile: File, temporary: File) {
        if (outputFile.exists() && !outputFile.delete()) {
            throw IOException("Could not replace ${outputFile.name}")
        }
        if (!temporary.renameTo(outputFile)) {
            temporary.copyTo(outputFile, overwrite = true)
            temporary.delete()
        }
    }

    private fun RandomAccessFile.readTag(): String {
        val bytes = ByteArray(4)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.writeTag(value: String) = write(value.toByteArray(Charsets.US_ASCII))

    private fun RandomAccessFile.readUnsignedShortLe(): Int =
        readUnsignedByte() or (readUnsignedByte() shl 8)

    private fun RandomAccessFile.readUnsignedIntLe(): Long =
        readUnsignedByte().toLong() or
            (readUnsignedByte().toLong() shl 8) or
            (readUnsignedByte().toLong() shl 16) or
            (readUnsignedByte().toLong() shl 24)

    private fun RandomAccessFile.writeUnsignedShortLe(value: Int) {
        write(value and 0xFF)
        write(value ushr 8 and 0xFF)
    }

    private fun RandomAccessFile.writeUnsignedIntLe(value: Long) {
        write((value and 0xFF).toInt())
        write((value ushr 8 and 0xFF).toInt())
        write((value ushr 16 and 0xFF).toInt())
        write((value ushr 24 and 0xFF).toInt())
    }

    private data class WavInput(
        val file: File,
        val format: WavFormat,
        val dataOffset: Long,
        val dataSize: Long
    ) {
        val frameCount: Long = dataSize / format.blockAlign
        val normalizedFrameCount: Long = frameCount * EXPORT_SAMPLE_RATE / format.sampleRate
    }

    private data class WavFormat(
        val audioFormat: Int,
        val channelCount: Int,
        val sampleRate: Long,
        val byteRate: Long,
        val blockAlign: Int,
        val bitsPerSample: Int
    ) {
        val bytesPerSample: Int get() = bitsPerSample / 8
        val bytesPerFrame: Int get() = channelCount * bytesPerSample

        fun isConvertibleToPcm16(): Boolean = when (audioFormat) {
            PCM -> bitsPerSample == 8 || bitsPerSample == 16
            IEEE_FLOAT -> bitsPerSample == 32
            else -> false
        }

        fun readSample(data: ByteArray, offset: Int): Int = when (audioFormat) {
            PCM -> when (bitsPerSample) {
                8 -> ((data[offset].toInt() and 0xFF) - 128) shl 8
                16 -> (data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)
                else -> error("Unsupported PCM encoding")
            }
            IEEE_FLOAT -> {
                val bits = (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    ((data[offset + 2].toInt() and 0xFF) shl 16) or
                    (data[offset + 3].toInt() shl 24)
                (Float.fromBits(bits).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
            }
            else -> error("Unsupported WAV encoding")
        }

        companion object {
            fun exportPcm16(): WavFormat = WavFormat(
                audioFormat = PCM,
                channelCount = EXPORT_CHANNEL_COUNT,
                sampleRate = EXPORT_SAMPLE_RATE,
                byteRate = EXPORT_SAMPLE_RATE * PCM_16_BITS / 8,
                blockAlign = EXPORT_CHANNEL_COUNT * PCM_16_BITS / 8,
                bitsPerSample = PCM_16_BITS
            )
        }
    }

    private const val RIFF = "RIFF"
    private const val WAVE = "WAVE"
    private const val FORMAT = "fmt "
    private const val DATA = "data"
    private const val HEADER_SIZE = 44
    private const val CHUNK_HEADER_SIZE = 8
    private const val PCM_FORMAT_SIZE = 16
    private const val COPY_BUFFER_SIZE = 64 * 1024
    private const val MAX_WAV_DATA_SIZE = 0xFFFF_FFFFL - (HEADER_SIZE - 8)
    private const val PCM = 1
    private const val IEEE_FLOAT = 3
    private const val PCM_16_BITS = 16
    private const val EXPORT_SAMPLE_RATE = 22_050L
    private const val EXPORT_CHANNEL_COUNT = 1
    private val SUPPORTED_WAV_FORMATS = setOf(PCM, IEEE_FLOAT)
}
