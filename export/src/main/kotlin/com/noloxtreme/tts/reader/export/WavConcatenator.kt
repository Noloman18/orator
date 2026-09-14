package com.noloxtreme.tts.reader.export

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Streams compatible PCM WAV files into one WAV file without loading audio data
 * into memory. Transformer receives that single asset, avoiding a large
 * sequence of per-sentence asset loaders for long documents.
 */
internal object WavConcatenator {

    suspend fun concatenate(
        wavFiles: List<File>,
        outputFile: File,
        onProgress: suspend (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(wavFiles.isNotEmpty()) { "No WAV segments were supplied" }
        val inputs = wavFiles.map(::inspect)
        val format = inputs.first().format
        inputs.drop(1).forEach { input ->
            require(input.format == format) { "Speech segments use different WAV formats" }
        }
        val dataSize = inputs.sumOf { it.dataSize }
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
                        copy(source, output, input.dataSize)
                    }
                    writtenDataSize += input.dataSize
                    onProgress(((index + 1) * 100 / inputs.size).coerceIn(0, 100))
                }
                writeHeader(output, format, writtenDataSize)
            }
            replace(outputFile, temporary)
            completed = true
        } finally {
            if (!completed) temporary.delete()
        }
    }

    fun isSupportedWav(file: File): Boolean = validationError(file) == null

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
            require(resolvedFormat.channelCount > 0 && resolvedFormat.blockAlign > 0) {
                "Invalid WAV format in ${file.name}"
            }
            require(dataOffset >= 0 && dataSize > 0 && dataSize % resolvedFormat.blockAlign == 0L) {
                "Invalid WAV audio data in ${file.name}"
            }
            return WavInput(file, resolvedFormat, dataOffset, dataSize)
        }
    }

    private fun copy(source: RandomAccessFile, output: RandomAccessFile, bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (remaining > 0) {
            val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) throw IOException("Unexpected end of WAV data")
            output.write(buffer, 0, read)
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
    )

    private data class WavFormat(
        val audioFormat: Int,
        val channelCount: Int,
        val sampleRate: Long,
        val byteRate: Long,
        val blockAlign: Int,
        val bitsPerSample: Int
    )

    private const val RIFF = "RIFF"
    private const val WAVE = "WAVE"
    private const val FORMAT = "fmt "
    private const val DATA = "data"
    private const val HEADER_SIZE = 44
    private const val CHUNK_HEADER_SIZE = 8
    private const val PCM_FORMAT_SIZE = 16
    private const val COPY_BUFFER_SIZE = 64 * 1024
    private const val MAX_WAV_DATA_SIZE = 0xFFFF_FFFFL - (HEADER_SIZE - 8)
    private val SUPPORTED_WAV_FORMATS = setOf(1, 3) // PCM and IEEE float.
}
