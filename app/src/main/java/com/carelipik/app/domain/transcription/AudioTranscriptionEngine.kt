package com.carelipik.app.domain.transcription

sealed interface TranscriptionResult {
    data class Success(val transcript: String) : TranscriptionResult
    data class Failure(val message: String) : TranscriptionResult
}

/** Boundary for a speech-to-text implementation, whether it runs locally or remotely. */
interface AudioTranscriptionEngine {
    val option: TranscriptionEngineOption

    fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage = TranscriptionLanguage.Auto
    ): TranscriptionResult
}
