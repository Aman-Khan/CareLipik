package com.carelipik.app.data.audio

import java.io.File
import java.io.RandomAccessFile

internal data class WaveAudioMetadata(
    val durationMillis: Long,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int
)

internal object WaveAudioInspector {
    private const val REQUIRED_SAMPLE_RATE = 16_000
    private const val REQUIRED_CHANNELS = 1
    private const val REQUIRED_BITS_PER_SAMPLE = 16

    fun inspectCompatiblePcm(file: File): WaveAudioMetadata {
        require(file.isFile && file.length() >= 44) { "The selected WAV file is incomplete." }
        RandomAccessFile(file, "r").use { input ->
            require(input.readAscii(4) == "RIFF") { "Select a valid WAV audio file." }
            input.skipBytes(4)
            require(input.readAscii(4) == "WAVE") { "Select a valid WAV audio file." }

            var audioFormat = 0
            var sampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            var dataSize = 0L
            while (input.filePointer + 8 <= input.length()) {
                val chunkId = input.readAscii(4)
                val chunkSize = input.readLittleEndianUnsignedInt()
                val chunkStart = input.filePointer
                val chunkEnd = chunkStart + chunkSize
                require(chunkEnd <= input.length()) { "The selected WAV file is damaged." }
                when (chunkId) {
                    "fmt " -> {
                        require(chunkSize >= 16) { "The selected WAV format is incomplete." }
                        audioFormat = input.readLittleEndianShort()
                        channels = input.readLittleEndianShort()
                        sampleRate = input.readLittleEndianInt()
                        input.skipBytes(6)
                        bitsPerSample = input.readLittleEndianShort()
                    }
                    "data" -> dataSize = chunkSize
                }
                val paddedEnd = chunkEnd + (chunkSize and 1L)
                input.seek(paddedEnd.coerceAtMost(input.length()))
            }

            require(audioFormat == 1) { "Only uncompressed PCM WAV audio is supported." }
            require(
                sampleRate == REQUIRED_SAMPLE_RATE &&
                    channels == REQUIRED_CHANNELS &&
                    bitsPerSample == REQUIRED_BITS_PER_SAMPLE
            ) {
                "WAV audio must be mono, 16 kHz and 16-bit PCM."
            }
            require(dataSize > 0) { "The selected WAV file contains no audio." }
            val bytesPerSecond = sampleRate.toLong() * channels * (bitsPerSample / 8)
            return WaveAudioMetadata(
                durationMillis = dataSize * 1_000L / bytesPerSecond,
                sampleRate = sampleRate,
                channels = channels,
                bitsPerSample = bitsPerSample
            )
        }
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readLittleEndianInt(): Int =
        readUnsignedByte() or
            (readUnsignedByte() shl 8) or
            (readUnsignedByte() shl 16) or
            (readUnsignedByte() shl 24)

    private fun RandomAccessFile.readLittleEndianUnsignedInt(): Long =
        readLittleEndianInt().toLong() and 0xFFFF_FFFFL

    private fun RandomAccessFile.readLittleEndianShort(): Int =
        readUnsignedByte() or (readUnsignedByte() shl 8)
}
