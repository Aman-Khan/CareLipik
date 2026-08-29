package com.carelipik.app.domain.voice

import com.carelipik.app.domain.transcription.DiarizedAudioTurn

data class DoctorVoiceRoleMatch(
    val doctorSpeakerId: String,
    val confidence: Float,
    val similarity: Float,
    val similarityMargin: Float
)

sealed interface DoctorVoiceRoleMatchResult {
    data class Matched(val match: DoctorVoiceRoleMatch) : DoctorVoiceRoleMatchResult
    data class Uncertain(
        val bestSimilarity: Float? = null,
        val similarityMargin: Float? = null
    ) : DoctorVoiceRoleMatchResult
    data object NotEnrolled : DoctorVoiceRoleMatchResult
    data class Unavailable(val message: String) : DoctorVoiceRoleMatchResult
}

/** Matches a detected speaker to the enrolled doctor voice without assigning uncertain audio. */
fun interface DoctorVoiceRoleMatcher {
    fun match(
        samples: FloatArray,
        sampleRate: Int,
        turns: List<DiarizedAudioTurn>
    ): DoctorVoiceRoleMatchResult
}

object DoctorVoiceMatchPolicy {
    const val minimumSimilarity = 0.65f
    const val minimumSimilarityMargin = 0.08f

    fun choose(similarities: Map<String, Float>): DoctorVoiceRoleMatchResult {
        val candidates = similarities.entries.sortedByDescending(Map.Entry<String, Float>::value)
        if (candidates.size < 2) return DoctorVoiceRoleMatchResult.Uncertain()
        val best = candidates[0]
        val margin = best.value - candidates[1].value
        if (best.value < minimumSimilarity || margin < minimumSimilarityMargin) {
            return DoctorVoiceRoleMatchResult.Uncertain(best.value, margin)
        }
        val confidence = (
            (best.value - minimumSimilarity) / (1f - minimumSimilarity) * 0.6f +
                (margin / 0.25f).coerceIn(0f, 1f) * 0.4f
            ).coerceIn(0f, 1f)
        return DoctorVoiceRoleMatchResult.Matched(
            DoctorVoiceRoleMatch(best.key, confidence, best.value, margin)
        )
    }
}
