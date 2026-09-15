package com.noloxtreme.tts.reader.export

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Joins AAC tracks from per-batch M4A files without decoding or re-encoding
 * them. M4A files cannot be concatenated as bytes because each contains its
 * own MP4 container metadata; this writes all AAC samples into one new MP4
 * container instead.
 */
@Singleton
class M4aRemuxer @Inject constructor() {

    suspend fun remux(
        sourceFiles: List<File>,
        outputFile: File,
        onProgress: suspend (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(sourceFiles.isNotEmpty()) { "No encoded audio batches were supplied" }
            sourceFiles.forEach { file ->
                require(file.isFile && file.length() > 0L) { "Missing encoded audio batch: ${file.name}" }
            }

            outputFile.parentFile?.mkdirs()
            val temporary = File(outputFile.parentFile, "${outputFile.name}.part")
            temporary.delete()

            var completed = false
            var muxer: MediaMuxer? = null
            var muxerStarted = false
            try {
                var outputTrack = -1
                var expectedFormat: AudioFormatSignature? = null
                var timestampOffsetUs = 0L
                var lastOutputTimestampUs = -1L

                sourceFiles.forEachIndexed { batchIndex, sourceFile ->
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(sourceFile.absolutePath)
                        val inputTrack = extractor.findAudioTrack()
                        val inputFormat = extractor.getTrackFormat(inputTrack)
                        val signature = AudioFormatSignature.from(inputFormat)
                        if (expectedFormat == null) {
                            expectedFormat = signature
                            muxer = MediaMuxer(
                                temporary.absolutePath,
                                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                            )
                            outputTrack = requireNotNull(muxer).addTrack(inputFormat)
                            requireNotNull(muxer).start()
                            muxerStarted = true
                        } else {
                            require(expectedFormat == signature) {
                                "Encoded audio batches use different AAC formats"
                            }
                        }

                        extractor.selectTrack(inputTrack)
                        val buffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE_BYTES)
                        val bufferInfo = MediaCodec.BufferInfo()
                        val fallbackSampleDurationUs = aacFrameDurationUs(inputFormat)
                        var firstSourceTimestampUs = -1L
                        var previousSourceTimestampUs = -1L
                        var lastSourceTimestampUs = -1L
                        var lastSampleDurationUs = fallbackSampleDurationUs
                        while (true) {
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) break
                            require(size < MAX_SAMPLE_SIZE_BYTES) {
                                "Encoded audio sample exceeds the supported batch size"
                            }
                            // Samsung's extractor can report the same timestamp for
                            // consecutive AAC samples. Treat an unset, repeated, or
                            // backwards timestamp as missing: AAC-LC frames have a
                            // stable cadence, and their duration is determined by the
                            // sample rate rather than by the container timestamp.
                            val extractedTimestampUs = extractor.sampleTime
                            val sourceTimestampUs = if (
                                extractedTimestampUs >= 0L &&
                                    (previousSourceTimestampUs < 0L ||
                                        extractedTimestampUs > previousSourceTimestampUs)
                            ) {
                                extractedTimestampUs
                            } else if (previousSourceTimestampUs >= 0L) {
                                    previousSourceTimestampUs + lastSampleDurationUs
                            } else {
                                0L
                            }
                            if (firstSourceTimestampUs < 0L) {
                                firstSourceTimestampUs = sourceTimestampUs
                            }
                            val outputTimestampUs = timestampOffsetUs +
                                (sourceTimestampUs - firstSourceTimestampUs)
                            require(outputTimestampUs > lastOutputTimestampUs) {
                                "Encoded audio batches have overlapping timestamps"
                            }
                            buffer.position(0)
                            buffer.limit(size)
                            bufferInfo.set(
                                0,
                                size,
                                outputTimestampUs,
                                extractor.sampleFlags
                            )
                            requireNotNull(muxer).writeSampleData(outputTrack, buffer, bufferInfo)
                            if (previousSourceTimestampUs >= 0L) {
                                lastSampleDurationUs = (sourceTimestampUs - previousSourceTimestampUs)
                                    .coerceAtLeast(1L)
                            }
                            previousSourceTimestampUs = sourceTimestampUs
                            lastSourceTimestampUs = sourceTimestampUs
                            lastOutputTimestampUs = outputTimestampUs
                            extractor.advance()
                        }

                        require(lastSourceTimestampUs >= 0L) {
                            "Encoded audio batch contains no AAC samples"
                        }
                        timestampOffsetUs = lastOutputTimestampUs + lastSampleDurationUs
                    } finally {
                        extractor.release()
                    }
                    onProgress(((batchIndex + 1) * 100 / sourceFiles.size).coerceIn(0, 100))
                }

                if (muxerStarted) {
                    requireNotNull(muxer).stop()
                    muxerStarted = false
                }
                muxer?.release()
                muxer = null
                replace(outputFile, temporary)
                completed = true
                Result.success(Unit)
            } finally {
                if (muxerStarted) runCatching { muxer?.stop() }
                muxer?.release()
                if (!completed) temporary.delete()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun MediaExtractor.findAudioTrack(): Int =
        (0 until trackCount).firstOrNull { index ->
            getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IOException("Encoded audio batch has no audio track")

    private fun aacFrameDurationUs(format: MediaFormat): Long {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        require(sampleRate > 0) { "Encoded audio batch has an invalid sample rate" }
        // AAC-LC has 1,024 PCM samples per access unit.
        return (AAC_SAMPLES_PER_FRAME * MICROS_PER_SECOND / sampleRate).coerceAtLeast(1L)
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

    private data class AudioFormatSignature(
        val mimeType: String,
        val sampleRate: Int,
        val channelCount: Int,
        val codecSpecificData: List<List<Byte>>
    ) {
        companion object {
            fun from(format: MediaFormat): AudioFormatSignature {
                return AudioFormatSignature(
                    mimeType = requireNotNull(format.getString(MediaFormat.KEY_MIME)),
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    codecSpecificData = buildList {
                        var index = 0
                        while (format.containsKey("csd-$index")) {
                            add(requireNotNull(format.byteArrayOrNull("csd-$index")).toList())
                            index++
                        }
                    }
                )
            }
        }
    }

    private companion object {
        const val MAX_SAMPLE_SIZE_BYTES = 1 * 1024 * 1024
        const val AAC_SAMPLES_PER_FRAME = 1_024L
        const val MICROS_PER_SECOND = 1_000_000L

        fun MediaFormat.byteArrayOrNull(key: String): ByteArray? {
            if (!containsKey(key)) return null
            val duplicate = requireNotNull(getByteBuffer(key)).duplicate()
            val bytes = ByteArray(duplicate.remaining())
            duplicate.get(bytes)
            return bytes
        }
    }
}
