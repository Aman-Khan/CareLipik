package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.DiarizedAudioTurn
import com.carelipik.app.domain.transcription.SpeakerDiarizationEngine
import com.carelipik.app.domain.transcription.SpeakerDiarizationResult
import com.carelipik.app.domain.transcription.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDiarizedTranscriptionTest {
    @Test
    fun twoDetectedSpeakers_areTranscribedAsStructuredTurns() {
        val samples = FloatArray(32_000) { index -> if (index < 16_000) 0.1f else 0.2f }
        val diarizer = SpeakerDiarizationEngine { _, _, _ ->
            SpeakerDiarizationResult.Success(
                listOf(
                    DiarizedAudioTurn("speaker-1", 0f, 1f),
                    DiarizedAudioTurn("speaker-2", 1f, 2f)
                )
            )
        }

        val result = OfflineDiarizedTranscription.transcribe(
            samples = samples,
            sampleRate = 16_000,
            diarizationEngine = diarizer,
            recognize = { audio -> if (audio.first() < 0.15f) "Good morning" else "Hello" }
        )

        assertEquals(
            listOf(
                TranscriptSegment("speaker-1", "Good morning"),
                TranscriptSegment("speaker-2", "Hello")
            ),
            result.segments
        )
        assertEquals("Speaker 1: Good morning\n\nSpeaker 2: Hello", result.transcript)
    }

    @Test
    fun unavailableDiarizer_fallsBackToContinuousTranscription() {
        val diarizer = SpeakerDiarizationEngine { _, _, _ ->
            SpeakerDiarizationResult.Unavailable("Models missing")
        }

        val result = OfflineDiarizedTranscription.transcribe(
            samples = FloatArray(16_000),
            sampleRate = 16_000,
            diarizationEngine = diarizer,
            recognize = { "Continuous transcript" }
        )

        assertEquals("Continuous transcript", result.transcript)
        assertEquals(emptyList<TranscriptSegment>(), result.segments)
    }

    @Test
    fun adjacentTurnsFromSameSpeaker_areMergedForChat() {
        val diarizer = SpeakerDiarizationEngine { _, _, _ ->
            SpeakerDiarizationResult.Success(
                listOf(
                    DiarizedAudioTurn("speaker-1", 0f, 0.5f),
                    DiarizedAudioTurn("speaker-1", 0.5f, 1f),
                    DiarizedAudioTurn("speaker-2", 1f, 1.5f)
                )
            )
        }
        var turn = 0

        val result = OfflineDiarizedTranscription.transcribe(
            samples = FloatArray(24_000),
            sampleRate = 16_000,
            diarizationEngine = diarizer,
            recognize = { "turn ${++turn}" }
        )

        assertEquals(
            listOf(
                TranscriptSegment("speaker-1", "turn 1 turn 2"),
                TranscriptSegment("speaker-2", "turn 3")
            ),
            result.segments
        )
    }

    @Test
    fun multipleSpeakers_andOverlap_arePreservedAndFlagged() {
        var requestedCount = -1
        val diarizer = SpeakerDiarizationEngine { _, _, expectedCount ->
            requestedCount = expectedCount
            SpeakerDiarizationResult.Success(
                listOf(
                    DiarizedAudioTurn("speaker-1", 0f, 1f),
                    DiarizedAudioTurn("speaker-2", 0.5f, 1.5f),
                    DiarizedAudioTurn("speaker-3", 1.5f, 2f)
                )
            )
        }

        val result = OfflineDiarizedTranscription.transcribe(
            samples = FloatArray(32_000) { 0.2f },
            sampleRate = 16_000,
            diarizationEngine = diarizer,
            recognize = { "speech" }
        )

        assertEquals(0, requestedCount)
        assertEquals(3, result.segments.map { it.speakerId }.distinct().size)
        assertTrue(result.speakerSeparationWarning.orEmpty().contains("Overlapping speech"))
        assertTrue(result.speakerSeparationWarning.orEmpty().contains("3 distinct voices"))
    }
}
