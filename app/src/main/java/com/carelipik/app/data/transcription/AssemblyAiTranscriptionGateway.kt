package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.TranscriptionLanguage

data class AssemblyAiTranscriptionRequest(
    val audioPath: String,
    val language: TranscriptionLanguage
)

sealed interface AssemblyAiTranscriptionResult {
    data class Success(
        val transcript: String,
        val segments: List<RemoteSpeakerSegment>,
        val detectedLanguage: String? = null,
        val modelUsed: String? = null
    ) : AssemblyAiTranscriptionResult

    data class Failure(val message: String) : AssemblyAiTranscriptionResult
}

fun interface AssemblyAiTranscriptionGateway {
    fun transcribe(request: AssemblyAiTranscriptionRequest): AssemblyAiTranscriptionResult
}
