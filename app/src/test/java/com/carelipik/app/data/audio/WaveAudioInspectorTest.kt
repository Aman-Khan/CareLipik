package com.carelipik.app.data.audio

import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WaveAudioInspectorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun inspectCompatiblePcm_returnsDurationAndFormat() {
        val file = testWaveFile(sampleRate = 16_000, sampleCount = 32_000)

        val metadata = WaveAudioInspector.inspectCompatiblePcm(file)

        assertEquals(2_000L, metadata.durationMillis)
        assertEquals(16_000, metadata.sampleRate)
        assertEquals(1, metadata.channels)
        assertEquals(16, metadata.bitsPerSample)
    }

    @Test(expected = IllegalArgumentException::class)
    fun inspectCompatiblePcm_rejectsUnsupportedSampleRate() {
        val file = testWaveFile(sampleRate = 24_000, sampleCount = 24_000)

        WaveAudioInspector.inspectCompatiblePcm(file)
    }

    private fun testWaveFile(sampleRate: Int, sampleCount: Int): File {
        val file = temporaryFolder.newFile("test-$sampleRate.wav")
        val dataSize = sampleCount * 2
        DataOutputStream(file.outputStream()).use { output ->
            output.writeBytes("RIFF")
            output.writeLittleEndianInt(dataSize + 36)
            output.writeBytes("WAVEfmt ")
            output.writeLittleEndianInt(16)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianInt(sampleRate)
            output.writeLittleEndianInt(sampleRate * 2)
            output.writeLittleEndianShort(2)
            output.writeLittleEndianShort(16)
            output.writeBytes("data")
            output.writeLittleEndianInt(dataSize)
            repeat(sampleCount) { output.writeLittleEndianShort(0) }
        }
        return file
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        writeByte(value)
        writeByte(value shr 8)
        writeByte(value shr 16)
        writeByte(value shr 24)
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Int) {
        writeByte(value)
        writeByte(value shr 8)
    }
}
