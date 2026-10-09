package com.carelipik.app.data.transcription

enum class RemoteTranscriptionMode(val wireValue: String) {
    Transcribe("transcribe"),
    CodeMix("codemix")
}

data class RemoteTranscriptionRequest(
    val audioPath: String,
    val model: String,
    val languageCode: String,
    val mode: RemoteTranscriptionMode,
    val expectedSpeakerCount: Int
)

data class RemoteSpeakerSegment(
    val speakerId: String,
    val transcript: String,
    val startTimeSeconds: Double? = null,
    val endTimeSeconds: Double? = null
)

sealed interface RemoteTranscriptionResult {
    data class Success(
        val transcript: String,
        val segments: List<RemoteSpeakerSegment> = emptyList(),
        val speakerSeparationWarning: String? = null
    ) : RemoteTranscriptionResult

    data class Failure(val message: String) : RemoteTranscriptionResult
}

/** Boundary for remote transcription via either CareLipik's backend or a direct provider client. */
fun interface RemoteTranscriptionGateway {
    fun transcribe(request: RemoteTranscriptionRequest): RemoteTranscriptionResult
}
