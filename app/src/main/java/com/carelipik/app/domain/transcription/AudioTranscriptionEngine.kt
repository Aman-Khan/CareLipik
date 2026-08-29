package com.carelipik.app.domain.transcription

sealed interface TranscriptionResult {
    data class Success(
        val transcript: String,
        val segments: List<TranscriptSegment> = emptyList()
    ) : TranscriptionResult
    data class Failure(val message: String) : TranscriptionResult
}

data class TranscriptSegment(
    val speakerId: String,
    val transcript: String
)

/** Boundary for a speech-to-text implementation, whether it runs locally or remotely. */
interface AudioTranscriptionEngine {
    val option: TranscriptionEngineOption

    fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage = TranscriptionLanguage.Auto
    ): TranscriptionResult
}
