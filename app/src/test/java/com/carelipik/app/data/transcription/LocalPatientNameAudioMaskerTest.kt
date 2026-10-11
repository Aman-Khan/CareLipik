package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalPatientNameAudioMaskerTest {
    @get:Rule val files = TemporaryFolder()

    private fun recording(): java.io.File {
        val bytes = ByteBuffer.allocate(44 + 32000).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + 32000).put("WAVEfmt ".toByteArray())
        bytes.putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(32000)
        repeat(16000) { bytes.putShort(16000) }
        return files.newFile("original.wav").apply { writeBytes(bytes.array()) }
    }

    @Test fun masking_writesValidWaveWithSameDurationAndLeavesOriginalUntouched() {
        val original = recording()
        val before = original.readBytes()
        var scans = 0
        val directory = files.newFolder("masked")
        val masker = LocalPatientNameAudioMasker(directory, PatientNameAudioLocator { _, _, _ ->
            scans++
            if (scans == 1) listOf(AudioMaskWindow(.2, .5)) else emptyList()
        })
        val result = masker.mask(original.path, "Test Person", TranscriptionLanguage.English) as PatientNameMaskingResult.Success
        val masked = PcmWaveAudio.readMono16Khz(java.io.File(result.audioPath))
        assertEquals(16000, masked.size)
        assertEquals(16000 / 32768f, masked[0], .0001f)
        assertTrue(masked.sliceArray(3200 until 8000).all { kotlin.math.abs(it) < .03f })
        assertEquals(2, scans)
        assertArrayEquals(before, original.readBytes())
    }

    @Test fun noNameDetection_createsNoUploadFile() {
        val original = recording()
        val directory = files.newFolder("masked")
        val masker = LocalPatientNameAudioMasker(directory, PatientNameAudioLocator { _, _, _ -> emptyList() })
        assertTrue(masker.mask(original.path, "Test Person", TranscriptionLanguage.English) is PatientNameMaskingResult.Failure)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun nameStillDetectedAfterScrub_blocksFileCreation() {
        val original = recording()
        val directory = files.newFolder("masked")
        val masker = LocalPatientNameAudioMasker(directory, PatientNameAudioLocator { _, _, _ -> listOf(AudioMaskWindow(.2, .5)) })
        assertTrue(masker.mask(original.path, "Test Person", TranscriptionLanguage.English) is PatientNameMaskingResult.Failure)
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}
