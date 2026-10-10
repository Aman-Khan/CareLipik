package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult

class AssemblyAiTranscriptionEngine(
    private val gateway: AssemblyAiTranscriptionGateway
) : AudioTranscriptionEngine {
    override val option = TranscriptionEngineOption.AssemblyAiUniversal

    override fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage
    ): TranscriptionResult {
        if (audioPath.isBlank()) {
            return TranscriptionResult.Failure("The recording is unavailable.")
        }
        return when (
            val result = gateway.transcribe(AssemblyAiTranscriptionRequest(audioPath, language))
        ) {
            is AssemblyAiTranscriptionResult.Failure -> TranscriptionResult.Failure(result.message)
            is AssemblyAiTranscriptionResult.Success -> result.toTranscriptionResult()
        }
    }

    private fun AssemblyAiTranscriptionResult.Success.toTranscriptionResult(): TranscriptionResult {
        val structuredSegments = segments
            .filter { it.transcript.isNotBlank() && it.speakerId.isNotBlank() }
            .map { segment ->
                TranscriptSegment(
                    speakerId = segment.speakerId.trim(),
                    transcript = segment.transcript.trim(),
                    startTimeSeconds = segment.startTimeSeconds,
                    endTimeSeconds = segment.endTimeSeconds
                )
            }
        val diarizedText = structuredSegments.joinToString("\n\n") { segment ->
            "${segment.speakerId.toDisplayLabel()}: ${segment.transcript}"
        }
        val reviewText = diarizedText.ifBlank { transcript.trim() }
        if (reviewText.isBlank()) {
            return TranscriptionResult.Failure(
                "AssemblyAI did not detect speech. Check the recording and try again."
            )
        }
        val warning = when {
            structuredSegments.isEmpty() ->
                "AssemblyAI returned text without speaker turns. Review the transcript manually."
            structuredSegments.map { it.speakerId }.distinct().size == 1 ->
                "AssemblyAI detected only one voice. Review whether speakers were merged."
            else -> "AssemblyAI inferred speaker names from conversation context. Confirm every " +
                "name and clinical role before continuing."
        }
        return TranscriptionResult.Success(
            transcript = reviewText,
            segments = structuredSegments,
            speakerSeparationWarning = warning
        )
    }

    private fun String.toDisplayLabel(): String = split('-').joinToString(" ") { part ->
        part.replaceFirstChar(Char::uppercase)
    }
}
