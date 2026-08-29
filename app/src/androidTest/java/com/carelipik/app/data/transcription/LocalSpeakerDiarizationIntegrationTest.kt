package com.carelipik.app.data.transcription

import androidx.test.platform.app.InstrumentationRegistry
import com.carelipik.app.domain.transcription.SpeakerDiarizationResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LocalSpeakerDiarizationIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val audioFile = File(context.filesDir, TEST_AUDIO_FILE)

    @Test
    fun syntheticTwoVoiceAudio_detectsTwoLocalSpeakers() {
        assumeTrue("Push $TEST_AUDIO_FILE into app files before this test", audioFile.isFile)
        val samples = PcmWaveAudio.readMono16Khz(audioFile)

        val result = SherpaOfflineSpeakerDiarizationEngine(context).diarize(
            samples = samples,
            sampleRate = PcmWaveAudio.sampleRate,
            expectedSpeakerCount = 2
        )

        assertTrue("Expected speaker turns but received $result", result is SpeakerDiarizationResult.Success)
        val turns = (result as SpeakerDiarizationResult.Success).turns
        assertEquals(2, turns.map { it.speakerId }.distinct().size)
        assertTrue(turns.size >= 2)
    }

    @Test
    fun whisperWithLocalDiarization_returnsChatSegments() {
        assumeTrue("Push $TEST_AUDIO_FILE into app files before this test", audioFile.isFile)
        val engine = SherpaWhisperTranscriptionEngine(
            context = context,
            diarizationEngine = SherpaOfflineSpeakerDiarizationEngine(context)
        )

        val result = engine.transcribe(audioFile.absolutePath, TranscriptionLanguage.English)

        assertTrue("Expected transcript segments but received $result", result is TranscriptionResult.Success)
        val segments = (result as TranscriptionResult.Success).segments
        assertEquals(2, segments.map { it.speakerId }.distinct().size)
        assertTrue(segments.all { it.transcript.isNotBlank() })
    }

    @Test
    fun medAsrWithLocalDiarization_returnsChatSegments() {
        assumeTrue("Push $TEST_AUDIO_FILE into app files before this test", audioFile.isFile)
        val engine = SherpaMedAsrTranscriptionEngine(
            context = context,
            diarizationEngine = SherpaOfflineSpeakerDiarizationEngine(context)
        )

        val result = engine.transcribe(audioFile.absolutePath, TranscriptionLanguage.English)

        assertTrue("Expected transcript segments but received $result", result is TranscriptionResult.Success)
        val segments = (result as TranscriptionResult.Success).segments
        assertEquals(2, segments.map { it.speakerId }.distinct().size)
        assertTrue(segments.all { it.transcript.isNotBlank() })
    }

    private companion object {
        const val TEST_AUDIO_FILE = "synthetic-two-speaker-consultation.wav"
    }
}
