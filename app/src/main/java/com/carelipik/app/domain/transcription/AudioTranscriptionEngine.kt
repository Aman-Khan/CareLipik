package com.carelipik.app.domain.transcription

import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult

sealed interface TranscriptionResult {
    data class Success(
        val transcript: String,
        val segments: List<TranscriptSegment> = emptyList(),
        val doctorVoiceMatch: DoctorVoiceRoleMatchResult? = null,
        val speakerSeparationWarning: String? = null,
        val hybridReview: HybridTranscriptionReview? = null
    ) : TranscriptionResult
    data class Failure(val message: String) : TranscriptionResult
}

data class TranscriptSegment(
    val speakerId: String,
    val transcript: String,
    val startTimeSeconds: Double? = null,
    val endTimeSeconds: Double? = null,
    val isSpeakerUncertain: Boolean = false
)

/** Boundary for a speech-to-text implementation, whether it runs locally or remotely. */
interface AudioTranscriptionEngine {
    val option: TranscriptionEngineOption

    fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage = TranscriptionLanguage.Auto
    ): TranscriptionResult
}
