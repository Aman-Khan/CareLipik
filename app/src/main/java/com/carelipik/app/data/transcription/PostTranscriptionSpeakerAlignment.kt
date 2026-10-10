package com.carelipik.app.data.transcription

import android.util.Log

import com.carelipik.app.domain.transcription.DiarizedAudioTurn
import com.carelipik.app.domain.transcription.SpeakerDiarizationResult
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.WhisperDecodingSegment
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult

/** Applies the independent speaker timeline after ASR; never cuts audio at a speaker boundary. */
internal object PostTranscriptionSpeakerAlignment {
    fun assemble(decoded: List<WhisperDecodingSegment>, diarization: SpeakerDiarizationResult?,
        doctorMatch: DoctorVoiceRoleMatchResult?, backendNotice: String?): WhisperPrimaryResult {
        val turns = (diarization as? SpeakerDiarizationResult.Success)?.turns.orEmpty()
        var uncertain = false
        var previousWordSpeaker: String? = null
        var boundaryAdjustments = 0
        fun speaker(start: Long, end: Long, requireSingleSpeaker: Boolean = false): String {
            val duration = (end - start).coerceAtLeast(1)
            val overlaps = turns.groupBy(DiarizedAudioTurn::speakerId).mapValues { (_, spans) ->
                spans.sumOf { turn ->
                    (minOf(end, (turn.endSeconds * 1000).toLong()) -
                        maxOf(start, (turn.startSeconds * 1000).toLong())).coerceAtLeast(0)
                }
            }.entries.sortedByDescending { it.value }
            val best = overlaps.firstOrNull()
            if (best == null || best.value < duration * 0.5 ||
                (overlaps.getOrNull(1)?.value ?: 0) > best.value * 0.5 ||
                (requireSingleSpeaker && (overlaps.getOrNull(1)?.value ?: 0) > 200)) {
                uncertain = true
                // Speech in segmentation gaps retains the nearest established voice. Prefer
                // the preceding turn on a tie; never create another voice cluster here.
                if (best != null && best.value > 0) return best.key
                val previous = turns.filter { (it.endSeconds * 1000).toLong() <= start }
                    .maxByOrNull { it.endSeconds }
                val next = turns.filter { (it.startSeconds * 1000).toLong() >= end }
                    .minByOrNull { it.startSeconds }
                val previousGap = previous?.let { start - (it.endSeconds * 1000).toLong() } ?: Long.MAX_VALUE
                val nextGap = next?.let { (it.startSeconds * 1000).toLong() - end } ?: Long.MAX_VALUE
                return (if (previousGap <= nextGap) previous else next)?.speakerId ?: "speaker-unknown"
            }
            return best.key
        }
        val aligned = decoded.flatMap { segment ->
            val words = segment.wordTimings
            if (words.isEmpty()) {
                val id = speaker(segment.startMs, segment.endMs, requireSingleSpeaker = true)
                previousWordSpeaker = id
                listOf(segment.copy(speakerId = id))
            } else {
                val groups = mutableListOf<Triple<String, Int, Int>>()
                words.forEachIndexed { index, word ->
                    val pointSpeaker = word.alignmentMs?.let { speaker((it - 40).coerceAtLeast(0), it + 40) }
                        ?: speaker(word.startMs, word.endMs)
                    // Native word intervals can extend across a brief reply. A DTW point
                    // clearly inside one exclusive voice turn is stronger evidence than
                    // the duration of that broad interval; do not smooth that voice away.
                    val interiorSpeaker = word.alignmentMs?.let { point ->
                        val active = turns.filter { turn ->
                            point >= (turn.startSeconds * 1000).toLong() &&
                                point < (turn.endSeconds * 1000).toLong()
                        }
                        active.map { it.speakerId }.distinct().singleOrNull()?.takeIf { id ->
                            active.any { turn ->
                                turn.speakerId == id &&
                                    point - (turn.startSeconds * 1000).toLong() >= 80 &&
                                    (turn.endSeconds * 1000).toLong() - point >= 80
                            }
                        }
                    }
                    // DTW gives an attention point, not the whole spoken word. A late point
                    // can fall across a hand-off while most of the word belongs to the old voice.
                    val duration = (word.endMs - word.startMs).coerceAtLeast(1)
                    val coverage = turns.groupBy(DiarizedAudioTurn::speakerId).mapValues { (_, spans) ->
                        spans.sumOf { turn ->
                            (minOf(word.endMs, (turn.endSeconds * 1000).toLong()) -
                                maxOf(word.startMs, (turn.startSeconds * 1000).toLong())).coerceAtLeast(0)
                        }.coerceAtMost(duration)
                    }
                    val ranked = coverage.entries.sortedByDescending { it.value }
                    val strongest = ranked.firstOrNull()
                    var id = interiorSpeaker ?: if (strongest != null && strongest.value >= duration * 0.65 &&
                        (ranked.getOrNull(1)?.value ?: 0) <= duration * 0.25) strongest.key else pointSpeaker
                    val preceding = previousWordSpeaker
                    val precedingCoverage = coverage[preceding] ?: 0
                    if (interiorSpeaker == null && preceding != null && preceding != id && precedingCoverage >= duration * 0.35 &&
                        precedingCoverage >= (coverage[id] ?: 0)) id = preceding
                    if (id != pointSpeaker) {
                        uncertain = true
                        boundaryAdjustments++
                    }
                    previousWordSpeaker = id
                    val previous = groups.lastOrNull()
                    if (previous?.first == id) groups[groups.lastIndex] = Triple(id, previous.second, index)
                    else groups += Triple(id, index, index)
                }
                groups.mapIndexed { index, group ->
                    val from = if (index == 0) 0 else words[group.second].startIndex
                    val to = if (index == groups.lastIndex) segment.text.length else words[groups[index + 1].second].startIndex
                    val slice = segment.text.substring(from, to)
                    val leading = slice.length - slice.trimStart().length
                    val text = slice.trim()
                    val offset = from + leading
                    segment.copy(text = text, speakerId = group.first,
                        startMs = if (index == 0) segment.startMs else words[group.second].startMs,
                        endMs = if (index == groups.lastIndex) segment.endMs else words[group.third].endMs,
                        tokens = emptyList(), tokenStartTimesMs = emptyList(), tokenDurationsMs = emptyList(),
                        tokenLogProbabilities = emptyList(),
                        wordConfidences = segment.wordConfidences.filter { it.startIndex >= offset && it.endIndex <= offset + text.length }
                            .map { it.copy(startIndex = it.startIndex - offset, endIndex = it.endIndex - offset) },
                        wordTimings = words.subList(group.second, group.third + 1)
                            .map { it.copy(startIndex = it.startIndex - offset, endIndex = it.endIndex - offset) })
                }
            }
        }
        val transcript = StringBuilder()
        val mapped = aligned.mapIndexed { index, segment ->
            if (index == 0 || aligned[index - 1].speakerId != segment.speakerId) {
                if (index > 0) transcript.append("\n\n")
                transcript.append(segment.speakerId!!.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }).append(": ")
            } else transcript.append(' ')
            val from = transcript.length
            transcript.append(segment.text)
            segment.copy(transcriptStartIndex = from, transcriptEndIndex = transcript.length)
        }
        val display = mapped.fold(mutableListOf<TranscriptSegment>()) { output, segment ->
            val previous = output.lastOrNull()
            if (previous != null && previous.speakerId == segment.speakerId) {
                output[output.lastIndex] = previous.copy(transcript = "${previous.transcript} ${segment.text}",
                    endTimeSeconds = segment.endMs / 1000.0)
            } else output += TranscriptSegment(segment.speakerId!!, segment.text, segment.startMs / 1000.0, segment.endMs / 1000.0)
            output
        }
        val detectedVoices = turns.map { it.speakerId }.distinct().size
        val assignedVoices = mapped.mapNotNull { it.speakerId }.filterNot { it == "speaker-unknown" }.distinct().size
        val warning = when (diarization) {
            is SpeakerDiarizationResult.Failure -> "Speaker separation failed: ${diarization.message}"
            is SpeakerDiarizationResult.Unavailable -> diarization.message
            else -> buildList {
                if (assignedVoices < detectedVoices) add("Detected $detectedVoices voices, but transcript words were assigned to only $assignedVoices. Some short speaker turns may be missing or misassigned; review against the recording.")
                if (uncertain) add("Some speaker boundaries are uncertain. Verify them against the recording.")
            }.joinToString(" ").ifBlank { null }
        }
        Log.i("CareLipikDiarization", "Transcript alignment: voice clusters=${turns.map { it.speakerId }.distinct().size}, " +
            "assigned voices=${mapped.mapNotNull { it.speakerId }.filterNot { it == "speaker-unknown" }.distinct().size}, " +
            "unassigned regions=${mapped.count { it.speakerId == "speaker-unknown" }}, boundary adjustments=$boundaryAdjustments")
        return WhisperPrimaryResult(TranscriptionResult.Success(transcript.toString(), display, doctorMatch,
            speakerSeparationWarning = warning), mapped, backendNotice)
    }
}
