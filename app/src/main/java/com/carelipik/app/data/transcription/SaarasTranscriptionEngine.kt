package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptSegment

/** Hindi and code-mixed Hindi/English transcription through the CareLipik backend. */
class SaarasTranscriptionEngine(
    private val gateway: RemoteTranscriptionGateway
) : AudioTranscriptionEngine {
    override val option: TranscriptionEngineOption =
        TranscriptionEngineOption.SaarasHindiHinglish

    override fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage
    ): TranscriptionResult {
        val mode = when (language) {
            TranscriptionLanguage.Hindi -> RemoteTranscriptionMode.Transcribe
            TranscriptionLanguage.Hinglish -> RemoteTranscriptionMode.CodeMix
            else -> return TranscriptionResult.Failure(
                "Saaras is currently available for Hindi and Hinglish recordings only."
            )
        }
        if (audioPath.isBlank()) {
            return TranscriptionResult.Failure("The recording is unavailable.")
        }

        return when (
            val result = gateway.transcribe(
                RemoteTranscriptionRequest(
                    audioPath = audioPath,
                    model = MODEL,
                    languageCode = HINDI_LANGUAGE_CODE,
                    mode = mode,
                    expectedSpeakerCount = EXPECTED_SPEAKER_COUNT
                )
            )
        ) {
            is RemoteTranscriptionResult.Failure -> TranscriptionResult.Failure(result.message)
            is RemoteTranscriptionResult.Success -> result.toTranscriptionResult()
        }
    }

    private fun RemoteTranscriptionResult.Success.toTranscriptionResult(): TranscriptionResult {
        val speakerLabels = linkedMapOf<String, Int>()
        val structuredSegments = segments
            .filter { it.transcript.isNotBlank() }
            .map { segment ->
                val speakerNumber = speakerLabels.getOrPut(segment.speakerId) {
                    speakerLabels.size + 1
                }
                TranscriptSegment(
                    speakerId = "speaker-$speakerNumber",
                    transcript = segment.transcript.trim()
                )
            }
        val diarizedTranscript = structuredSegments.joinToString(separator = "\n\n") { segment ->
            "${segment.speakerId.toDisplayLabel()}: ${segment.transcript}"
        }
        val reviewText = diarizedTranscript.ifBlank { transcript.trim() }
        return if (reviewText.isBlank()) {
            TranscriptionResult.Failure(
                "No speech was detected. Check the recording and try again."
            )
        } else {
            TranscriptionResult.Success(reviewText, structuredSegments)
        }
    }

    private fun String.toDisplayLabel(): String = split('-').joinToString(" ") { part ->
        part.replaceFirstChar(Char::uppercase)
    }

    private companion object {
        const val MODEL = "saaras:v3"
        const val HINDI_LANGUAGE_CODE = "hi-IN"
        const val EXPECTED_SPEAKER_COUNT = 2
    }
}
