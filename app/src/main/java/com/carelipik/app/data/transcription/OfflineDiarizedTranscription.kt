package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.SpeakerDiarizationEngine
import com.carelipik.app.domain.transcription.DiarizedAudioTurn
import com.carelipik.app.domain.transcription.SpeakerDiarizationResult
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatcher
import kotlin.math.ceil
import kotlin.math.floor

internal data class OfflineTranscriptionPayload(
    val transcript: String,
    val segments: List<TranscriptSegment> = emptyList(),
    val doctorVoiceMatch: DoctorVoiceRoleMatchResult? = null,
    val speakerSeparationWarning: String? = null
)

internal object OfflineDiarizedTranscription {
    fun transcribe(
        samples: FloatArray,
        sampleRate: Int,
        diarizationEngine: SpeakerDiarizationEngine?,
        doctorVoiceRoleMatcher: DoctorVoiceRoleMatcher? = null,
        fallbackAfterEmptyDiarization: Boolean = true,
        consolidateSpeakerAudio: Boolean = false,
        preparedDiarization: SpeakerDiarizationResult? = null,
        recognizeWithTiming: ((FloatArray, Double, String?) -> String)? = null,
        recognize: (FloatArray) -> String
    ): OfflineTranscriptionPayload {
        val diarization = preparedDiarization ?: diarizationEngine?.diarize(
            samples = samples,
            sampleRate = sampleRate,
            expectedSpeakerCount = -1
        )
        if (diarization is SpeakerDiarizationResult.Success) {
            // Merge audio BEFORE decoding; merging text afterwards cannot restore ASR context.
            // Never span an intervening speaker or overlap another speaker's turn.
            val turns = if (consolidateSpeakerAudio) diarization.turns.sortedBy { it.startSeconds }
                .fold(mutableListOf<DiarizedAudioTurn>()) { merged, turn ->
                    val previous = merged.lastOrNull()
                    if (previous?.speakerId == turn.speakerId &&
                        turn.startSeconds >= previous.endSeconds &&
                        turn.startSeconds - previous.endSeconds <= 0.8f &&
                        turn.endSeconds - previous.startSeconds <= 25f) {
                        merged[merged.lastIndex] = previous.copy(endSeconds = turn.endSeconds)
                    } else merged += turn
                    merged
                } else diarization.turns
            val segments = turns.mapNotNull { turn ->
                val startSample = floor(turn.startSeconds * sampleRate).toInt()
                    .coerceIn(0, samples.size)
                val endSample = ceil(turn.endSeconds * sampleRate).toInt()
                    .coerceIn(startSample, samples.size)
                if (endSample - startSample < MIN_TRANSCRIPTION_SAMPLES) {
                    null
                } else {
                    val audio = samples.copyOfRange(startSample, endSample)
                    (recognizeWithTiming?.invoke(audio, startSample.toDouble() / sampleRate, turn.speakerId)
                        ?: recognize(audio))
                        .trim()
                        .ifBlank { null }
                        ?.let { text ->
                            TranscriptSegment(
                                turn.speakerId, text,
                                startTimeSeconds = if (recognizeWithTiming != null) startSample.toDouble() / sampleRate else null,
                                endTimeSeconds = if (recognizeWithTiming != null) endSample.toDouble() / sampleRate else null
                            )
                        }
                }
            }.mergeAdjacentSpeakerSegments()
            if (segments.isNotEmpty()) {
                return OfflineTranscriptionPayload(
                    transcript = segments.joinToString("\n\n") { segment ->
                        "${segment.speakerId.toDisplayLabel()}: ${segment.transcript}"
                    },
                    segments = segments,
                    doctorVoiceMatch = doctorVoiceRoleMatcher?.match(
                        samples = samples,
                        sampleRate = sampleRate,
                        turns = diarization.turns
                    )
                )
            }
            if (!fallbackAfterEmptyDiarization) return OfflineTranscriptionPayload(transcript = "")
        }
        return OfflineTranscriptionPayload(
            transcript = (recognizeWithTiming?.invoke(samples, 0.0, null) ?: recognize(samples)).trim(),
            speakerSeparationWarning = when (diarization) {
                is SpeakerDiarizationResult.Failure -> "Speaker separation failed: ${diarization.message}"
                is SpeakerDiarizationResult.Unavailable -> diarization.message
                else -> null
            }
        )
    }

    private fun List<TranscriptSegment>.mergeAdjacentSpeakerSegments(): List<TranscriptSegment> =
        fold(mutableListOf()) { merged, segment ->
            val previous = merged.lastOrNull()
            if (previous?.speakerId == segment.speakerId) {
                merged[merged.lastIndex] = previous.copy(
                    transcript = "${previous.transcript} ${segment.transcript}".trim(),
                    endTimeSeconds = segment.endTimeSeconds
                )
            } else {
                merged += segment
            }
            merged
        }

    private fun String.toDisplayLabel(): String = split('-').joinToString(" ") { part ->
        part.replaceFirstChar(Char::uppercase)
    }

    private const val MIN_TRANSCRIPTION_SAMPLES = PcmWaveAudio.sampleRate / 5
}
