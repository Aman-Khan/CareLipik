package com.carelipik.app.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorVoiceMatchPolicyTest {
    @Test
    fun strongDistinctSimilarity_assignsDoctor() {
        val result = DoctorVoiceMatchPolicy.choose(
            mapOf("speaker-1" to 0.82f, "speaker-2" to 0.54f)
        )

        assertTrue(result is DoctorVoiceRoleMatchResult.Matched)
        result as DoctorVoiceRoleMatchResult.Matched
        assertEquals("speaker-1", result.match.doctorSpeakerId)
        assertTrue(result.match.confidence > 0f)
    }

    @Test
    fun similarityBelowGate_staysUnassigned() {
        val result = DoctorVoiceMatchPolicy.choose(
            mapOf("speaker-1" to 0.64f, "speaker-2" to 0.32f)
        )

        assertTrue(result is DoctorVoiceRoleMatchResult.Uncertain)
    }

    @Test
    fun ambiguousSimilarities_stayUnassigned() {
        val result = DoctorVoiceMatchPolicy.choose(
            mapOf("speaker-1" to 0.81f, "speaker-2" to 0.75f)
        )

        assertTrue(result is DoctorVoiceRoleMatchResult.Uncertain)
    }
}
