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
    val transcript: String
)

sealed interface RemoteTranscriptionResult {
    data class Success(
        val transcript: String,
        val segments: List<RemoteSpeakerSegment> = emptyList()
    ) : RemoteTranscriptionResult

    data class Failure(val message: String) : RemoteTranscriptionResult
}

/** Boundary to the CareLipik backend. Provider credentials must remain on that backend. */
fun interface RemoteTranscriptionGateway {
    fun transcribe(request: RemoteTranscriptionRequest): RemoteTranscriptionResult
}
