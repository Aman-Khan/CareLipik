package com.carelipik.app

import androidx.test.platform.app.InstrumentationRegistry
import com.carelipik.app.data.transcription.LocalPatientNameAudioMasker
import com.carelipik.app.data.transcription.PcmWaveAudio
import com.carelipik.app.data.transcription.EnglishKeywordPatientNameLocator
import com.carelipik.app.domain.transcription.PatientNameMaskingResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class PatientNameAudioMaskingTest {
    @Test fun bundledScanner_doesNotDetectNameInSilence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locator = EnglishKeywordPatientNameLocator(context)
        assertTrue(locator.locate(FloatArray(16000 * 3), "Alice", TranscriptionLanguage.English).isEmpty())
    }

    /** Optional locally generated synthetic speech; never use a consultation recording. */
    @Test fun syntheticSpeech_masksNameAndPreservesOriginal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.filesDir, "name-masking-synthetic.wav")
        if (!fixture.isFile) {
            val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
            if (testAssets.list("")?.contains("name-masking-synthetic.wav") == true) {
                val bytes = testAssets.open("name-masking-synthetic.wav").use { it.readBytes() }
                fixture.parentFile!!.mkdirs()
                fixture.writeBytes(bytes)
            }
        }
        assumeTrue("Provision synthetic speech fixture for positive scanner verification", fixture.isFile)
        val original = fixture.readBytes()
        val samples = PcmWaveAudio.readMono16Khz(fixture)
        val englishStarted = android.os.SystemClock.elapsedRealtime()
        val result = LocalPatientNameAudioMasker(context).mask(fixture.path, "Alice", TranscriptionLanguage.English)
        val englishMs = android.os.SystemClock.elapsedRealtime() - englishStarted
        android.util.Log.i("NameMaskBenchmark", "English KWS full masking=${englishMs}ms; audio=${samples.size / 16000.0}s")
        assertTrue("Synthetic name must be detected and scrubbed: $result", result is PatientNameMaskingResult.Success)
        result as PatientNameMaskingResult.Success
        val output = File(result.audioPath)
        try {
            assertArrayEquals(original, fixture.readBytes())
            assertEquals(PcmWaveAudio.readMono16Khz(fixture).size, PcmWaveAudio.readMono16Khz(output).size)
            assertFalse(original.contentEquals(output.readBytes()))
            assertTrue(result.windows.isNotEmpty())
        } finally {
            output.delete()
        }
    }
}
