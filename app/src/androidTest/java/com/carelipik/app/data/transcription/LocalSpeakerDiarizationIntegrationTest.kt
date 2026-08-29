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
    fun longAlternatingConsultation_preservesKnownSpeakerChanges() {
        val longAudioFile = File(context.filesDir, LONG_TEST_AUDIO_FILE)
        assumeTrue("Push $LONG_TEST_AUDIO_FILE into app files before this test", longAudioFile.isFile)
        val samples = PcmWaveAudio.readMono16Khz(longAudioFile)

        val result = SherpaOfflineSpeakerDiarizationEngine(context).diarize(
            samples = samples,
            sampleRate = PcmWaveAudio.sampleRate,
            expectedSpeakerCount = 2
        )

        assertTrue("Expected speaker turns but received $result", result is SpeakerDiarizationResult.Success)
        val turns = (result as SpeakerDiarizationResult.Success).turns
        val audioDuration = samples.size / PcmWaveAudio.sampleRate.toFloat()
        val intervals = buildList {
            var start = 0f
            EXPECTED_TURN_BOUNDARIES_SECONDS.forEach { boundary ->
                add(start to boundary)
                start = boundary
            }
            add(start to audioDuration)
        }
        val assignedSpeakers = intervals.map { (start, end) ->
            turns.groupBy { it.speakerId }
                .mapValues { (_, speakerTurns) ->
                    speakerTurns.sumOf { turn ->
                        maxOf(0f, minOf(end, turn.endSeconds) - maxOf(start, turn.startSeconds))
                            .toDouble()
                    }
                }
                .maxByOrNull { it.value }
                ?.takeIf { it.value > 0.05 }
                ?.key
        }
        val firstSpeaker = assignedSpeakers.first()
        val secondSpeaker = assignedSpeakers.getOrNull(1)
        assertTrue(
            "The first two known turns must be assigned to different speakers: " +
                "$assignedSpeakers; raw turns=$turns",
            firstSpeaker != null && secondSpeaker != null && firstSpeaker != secondSpeaker
        )
        val correctTurns = assignedSpeakers.withIndex().count { (index, speaker) ->
            speaker == if (index % 2 == 0) firstSpeaker else secondSpeaker
        }
        assertTrue(
            "Expected at least $MIN_CORRECT_TURNS of ${intervals.size} alternating turns, " +
                "but received $correctTurns: $assignedSpeakers; raw turns=$turns",
            correctTurns >= MIN_CORRECT_TURNS
        )
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
        const val LONG_TEST_AUDIO_FILE = "english-long-respiratory-history.wav"
        const val MIN_CORRECT_TURNS = 14
        val EXPECTED_TURN_BOUNDARIES_SECONDS = listOf(
            3.9196f,
            11.1011f,
            15.4873f,
            23.2390f,
            29.5335f,
            38.8886f,
            45.3060f,
            55.6346f,
            59.8696f,
            69.3706f,
            77.4213f,
            87.7386f,
            92.0117f,
            99.0734f,
            105.5425f
        )
    }
}
