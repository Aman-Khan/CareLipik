package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.SpeakerDiarizationEngine
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
        recognize: (FloatArray) -> String
    ): OfflineTranscriptionPayload {
        val quality = AudioQualityAnalyzer.analyze(samples, sampleRate)
        val diarization = diarizationEngine?.diarize(
            samples = samples,
            sampleRate = sampleRate,
            expectedSpeakerCount = AUTO_DETECT_SPEAKER_COUNT
        )
        if (diarization is SpeakerDiarizationResult.Success) {
            val speakerCount = diarization.turns.map { it.speakerId }.distinct().size
            val overlapSeconds = diarization.turns.sumOf { first ->
                diarization.turns.asSequence()
                    .filter { second -> second.speakerId != first.speakerId }
                    .map { second ->
                        (minOf(first.endSeconds, second.endSeconds) -
                            maxOf(first.startSeconds, second.startSeconds)).coerceAtLeast(0f)
                    }
                    .maxOrNull()?.toDouble() ?: 0.0
            }.toFloat() / 2f
            val warnings = buildList {
                addAll(quality.warnings)
                if (overlapSeconds >= OVERLAP_WARNING_SECONDS) {
                    add("Overlapping speech was detected. Those words and speaker labels are uncertain and require review.")
                }
                if (speakerCount > 2) {
                    add("$speakerCount distinct voices were detected. Assign Doctor, Patient, and any additional participants manually.")
                }
            }
            val segments = diarization.turns.mapNotNull { turn ->
                val startSample = floor(turn.startSeconds * sampleRate).toInt()
                    .coerceIn(0, samples.size)
                val endSample = ceil(turn.endSeconds * sampleRate).toInt()
                    .coerceIn(startSample, samples.size)
                if (endSample - startSample < MIN_TRANSCRIPTION_SAMPLES) {
                    null
                } else {
                    recognize(samples.copyOfRange(startSample, endSample))
                        .trim()
                        .ifBlank { null }
                        ?.let { text ->
                            TranscriptSegment(
                                speakerId = turn.speakerId,
                                transcript = text,
                                isSpeakerUncertain = diarization.turns.any { other ->
                                    other.speakerId != turn.speakerId &&
                                        minOf(turn.endSeconds, other.endSeconds) -
                                        maxOf(turn.startSeconds, other.startSeconds) > 0.05f
                                }
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
                    ),
                    speakerSeparationWarning = warnings.distinct().joinToString(" ").ifBlank { null }
                )
            }
        }
        return OfflineTranscriptionPayload(
            transcript = recognize(samples).trim(),
            speakerSeparationWarning = quality.warnings.joinToString(" ").ifBlank { null }
        )
    }

    private fun List<TranscriptSegment>.mergeAdjacentSpeakerSegments(): List<TranscriptSegment> =
        fold(mutableListOf()) { merged, segment ->
            val previous = merged.lastOrNull()
            if (previous?.speakerId == segment.speakerId) {
                merged[merged.lastIndex] = previous.copy(
                    transcript = "${previous.transcript} ${segment.transcript}".trim(),
                    isSpeakerUncertain = previous.isSpeakerUncertain || segment.isSpeakerUncertain
                )
            } else {
                merged += segment
            }
            merged
        }

    private fun String.toDisplayLabel(): String = split('-').joinToString(" ") { part ->
        part.replaceFirstChar(Char::uppercase)
    }

    private const val AUTO_DETECT_SPEAKER_COUNT = 0
    private const val OVERLAP_WARNING_SECONDS = 0.25f
    private const val MIN_TRANSCRIPTION_SAMPLES = PcmWaveAudio.sampleRate / 5
}
