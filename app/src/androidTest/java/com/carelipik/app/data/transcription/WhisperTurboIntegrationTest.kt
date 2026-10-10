package com.carelipik.app.data.transcription

import androidx.test.platform.app.InstrumentationRegistry
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperTurboIntegrationTest {
    @Test
    fun bundledTurboModel_transcribesSyntheticEnglishAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val audio = File(context.cacheDir, "synthetic-whisper-turbo-test.wav")
        instrumentation.context.assets.open("jfk.wav").use { input ->
            audio.outputStream().use(input::copyTo)
        }
        try {
            val result = SherpaWhisperTranscriptionEngine(
                context = context,
                variant = WhisperModelVariant.Turbo
            ).transcribe(audio.absolutePath, TranscriptionLanguage.English)

            assertTrue("Expected Turbo transcript but received $result", result is TranscriptionResult.Success)
            assertTrue((result as TranscriptionResult.Success).transcript.isNotBlank())
        } finally {
            audio.delete()
        }
    }
}
