package com.carelipik.app.domain.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerIdentityResolverTest {
    @Test
    fun matchingVoice_keepsIdentityAcrossLongPauseAndAnotherSpeaker() {
        val turns = listOf(turn("a", 0f), turn("b", 5f), turn("c", 60f))
        val result = SpeakerIdentityResolver.resolve(
            turns,
            mapOf("a" to floatArrayOf(1f, 0f), "b" to floatArrayOf(0f, 1f),
                "c" to floatArrayOf(0.99f, 0.01f))
        )
        assertEquals(listOf("speaker-1", "speaker-2", "speaker-1"), result.map { it.speakerId })
        assertEquals(turns.map { it.startSeconds }, result.map { it.startSeconds })
        assertEquals(turns.map { it.endSeconds }, result.map { it.endSeconds })
    }

    @Test
    fun distinctVoices_preservesFourPeople() {
        val turns = (0..3).map { turn("voice-$it", it * 10f) }
        val embeddings = (0..3).associate { index ->
            "voice-$index" to FloatArray(4) { if (it == index) 1f else 0f }
        }
        assertEquals(4, SpeakerIdentityResolver.resolve(turns, embeddings)
            .map { it.speakerId }.distinct().size)
    }

    @Test
    fun missingOrInvalidVoiceEvidence_doesNotMergePeople() {
        val turns = (0..3).map { turn("voice-$it", it * 10f) }
        val embeddings = mapOf("voice-0" to floatArrayOf(1f, 0f),
            "voice-1" to floatArrayOf(Float.NaN, 0f), "voice-2" to floatArrayOf(0f, 0f))
        assertEquals(4, SpeakerIdentityResolver.resolve(turns, embeddings)
            .map { it.speakerId }.distinct().size)
    }

    @Test
    fun intermediateVoice_doesNotChainMergeDistinctPeople() {
        val turns = listOf(turn("a", 0f), turn("b", 5f), turn("c", 10f))
        val embeddings = mapOf("a" to floatArrayOf(1f, 0f),
            "b" to floatArrayOf(0.7071f, 0.7071f), "c" to floatArrayOf(0f, 1f))
        assertEquals(2, SpeakerIdentityResolver.resolve(turns, embeddings)
            .map { it.speakerId }.distinct().size)
    }

    private fun turn(id: String, start: Float) = DiarizedAudioTurn(id, start, start + 3f)
}
