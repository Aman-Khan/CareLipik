package com.carelipik.app.data.transcription

import androidx.test.platform.app.InstrumentationRegistry
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaWhisperTranscriptionEngineTest {
    @Test
    fun publicSpeechSample_isTranscribedOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val recording = File(targetContext.cacheDir, "public-jfk-test.wav")
        instrumentation.context.assets.open("jfk.wav").use { input ->
            recording.outputStream().use(input::copyTo)
        }

        try {
            val result = SherpaWhisperTranscriptionEngine(targetContext).transcribe(
                recording.absolutePath,
                TranscriptionLanguage.English
            )

            assertTrue(
                "Expected a real transcript but received: $result",
                result is TranscriptionResult.Success &&
                    result.transcript.contains("country", ignoreCase = true)
            )
        } finally {
            recording.delete()
        }
    }

    @Test
    fun publicSpeechSample_isTranscribedByMedicalEnglishEngine() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val recording = File(targetContext.cacheDir, "public-jfk-medasr-test.wav")
        instrumentation.context.assets.open("jfk.wav").use { input ->
            recording.outputStream().use(input::copyTo)
        }

        try {
            val result = SherpaMedAsrTranscriptionEngine(targetContext).transcribe(
                recording.absolutePath,
                TranscriptionLanguage.English
            )

            assertTrue(
                "Expected a real transcript but received: $result",
                result is TranscriptionResult.Success && result.transcript.isNotBlank()
            )
        } finally {
            recording.delete()
        }
    }
}
