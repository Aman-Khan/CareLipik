package com.carelipik.app.data.transcription

import java.io.File
import java.io.RandomAccessFile

object PcmWaveAudio {
    const val sampleRate = 16_000

    fun readMono16Khz(file: File): FloatArray {
        require(file.isFile) { "The recording file is unavailable." }
        RandomAccessFile(file, "r").use { input ->
            require(input.length() >= 44) { "The recording file is incomplete." }
            require(input.readAscii(4) == "RIFF") { "Unsupported recording format." }
            input.skipBytes(4)
            require(input.readAscii(4) == "WAVE") { "Unsupported recording format." }

            var waveSampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            var dataOffset = -1L
            var dataSize = 0
            while (input.filePointer + 8 <= input.length()) {
                val chunkId = input.readAscii(4)
                val chunkSize = input.readLittleEndianInt()
                require(chunkSize >= 0) { "Invalid recording data." }
                when (chunkId) {
                    "fmt " -> {
                        val format = input.readLittleEndianShort()
                        channels = input.readLittleEndianShort()
                        waveSampleRate = input.readLittleEndianInt()
                        input.skipBytes(6)
                        bitsPerSample = input.readLittleEndianShort()
                        input.seek(input.filePointer + (chunkSize - 16).coerceAtLeast(0))
                        require(format == 1) { "Only PCM recordings are supported." }
                    }
                    "data" -> {
                        dataOffset = input.filePointer
                        dataSize = chunkSize
                        break
                    }
                    else -> input.seek(input.filePointer + chunkSize)
                }
            }
            require(waveSampleRate == sampleRate && channels == 1 && bitsPerSample == 16) {
                "Recording must be mono 16 kHz PCM audio."
            }
            require(dataOffset >= 0) { "Recording audio data is missing." }
            input.seek(dataOffset)
            val sampleCount = (dataSize / 2).coerceAtMost((input.length() - dataOffset).toInt() / 2)
            return FloatArray(sampleCount) {
                input.readLittleEndianShortSigned() / 32768f
            }
        }
    }

    fun chunks(samples: FloatArray, maxSamples: Int): Sequence<FloatArray> = sequence {
        var offset = 0
        while (offset < samples.size) {
            val end = (offset + maxSamples).coerceAtMost(samples.size)
            yield(samples.copyOfRange(offset, end))
            offset = end
        }
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readLittleEndianInt(): Int {
        return readUnsignedByte() or
            (readUnsignedByte() shl 8) or
            (readUnsignedByte() shl 16) or
            (readUnsignedByte() shl 24)
    }

    private fun RandomAccessFile.readLittleEndianShort(): Int {
        return readUnsignedByte() or (readUnsignedByte() shl 8)
    }

    private fun RandomAccessFile.readLittleEndianShortSigned(): Int {
        val value = readLittleEndianShort()
        return if (value >= 0x8000) value - 0x10000 else value
    }
}
