package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptSegment

/** Multilingual online transcription and diarization through the CareLipik backend. */
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
            TranscriptionLanguage.English,
            TranscriptionLanguage.Hindi -> RemoteTranscriptionMode.Transcribe
            TranscriptionLanguage.Hinglish -> RemoteTranscriptionMode.CodeMix
            else -> return TranscriptionResult.Failure(
                "Choose English, Hindi, or Hinglish to use Saaras."
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
                    languageCode = language.saarasLanguageCode(),
                    mode = mode
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
                    transcript = segment.transcript.trim(),
                    startTimeSeconds = segment.startTimeSeconds,
                    endTimeSeconds = segment.endTimeSeconds
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
            TranscriptionResult.Success(
                transcript = reviewText,
                segments = structuredSegments,
                speakerSeparationWarning = speakerSeparationWarning
            )
        }
    }

    private fun TranscriptionLanguage.saarasLanguageCode(): String = when (this) {
        TranscriptionLanguage.English -> ENGLISH_LANGUAGE_CODE
        TranscriptionLanguage.Hindi,
        TranscriptionLanguage.Hinglish -> HINDI_LANGUAGE_CODE
        TranscriptionLanguage.Auto -> error("Auto language is not supported by this engine")
    }

    private fun String.toDisplayLabel(): String = split('-').joinToString(" ") { part ->
        part.replaceFirstChar(Char::uppercase)
    }

    private companion object {
        const val MODEL = "saaras:v3"
        const val ENGLISH_LANGUAGE_CODE = "en-IN"
        const val HINDI_LANGUAGE_CODE = "hi-IN"
    }
}
