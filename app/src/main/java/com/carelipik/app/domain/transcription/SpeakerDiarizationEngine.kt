package com.carelipik.app.domain.transcription

data class DiarizedAudioTurn(
    val speakerId: String,
    val startSeconds: Float,
    val endSeconds: Float
)

sealed interface SpeakerDiarizationResult {
    data class Success(val turns: List<DiarizedAudioTurn>) : SpeakerDiarizationResult
    data class Unavailable(val message: String) : SpeakerDiarizationResult
    data class Failure(val message: String) : SpeakerDiarizationResult
}

/** Detects who spoke when from audio without assigning clinical roles. */
fun interface SpeakerDiarizationEngine {
    fun diarize(
        samples: FloatArray,
        sampleRate: Int,
        expectedSpeakerCount: Int // -1 estimates the speaker count from the audio.
    ): SpeakerDiarizationResult
}
