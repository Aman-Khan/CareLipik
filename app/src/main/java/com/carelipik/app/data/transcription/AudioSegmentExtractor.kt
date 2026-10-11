package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.HybridTranscriptionConfig
import com.carelipik.app.domain.transcription.MedAsrLanguageCompatibility
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.UncertainSegment
import com.carelipik.app.domain.transcription.WhisperDecodingSegment
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

internal data class HybridAudioWindow(
    val startSample: Int,
    val endSample: Int,
    val regionIndices: List<Int>,
    val reasons: List<String>
)

/** Plans bounded windows against already decoded original PCM; creates no temporary recordings. */
internal class AudioSegmentExtractor(private val config: HybridTranscriptionConfig) {
    fun plan(
        samples: FloatArray,
        sampleRate: Int,
        regions: List<WhisperDecodingSegment>,
        uncertain: List<UncertainSegment>,
        language: TranscriptionLanguage,
        checkCancelled: () -> Unit
    ): List<HybridAudioWindow> {
        require(sampleRate == PcmWaveAudio.sampleRate) { "MedASR requires mono 16 kHz PCM." }
        fun sample(ms: Long, roundUp: Boolean): Int {
            val value = ms.toDouble() * sampleRate / 1_000
            return (if (roundUp) ceil(value) else floor(value)).toInt().coerceIn(0, samples.size)
        }
        val windows = uncertain.mapNotNull { candidate ->
            val region = regions.getOrNull(candidate.segmentIndex) ?: return@mapNotNull null
            if (region.transcriptStartIndex < 0 || region.transcriptEndIndex < region.transcriptStartIndex) return@mapNotNull null
            if (!MedAsrLanguageCompatibility.isEnglish(region, language)) return@mapNotNull null
            val previous = regions.getOrNull(candidate.segmentIndex - 1)
            val next = regions.getOrNull(candidate.segmentIndex + 1)
            val beforeAllowed = previous == null || (previous.speakerId == region.speakerId &&
                MedAsrLanguageCompatibility.isEnglish(previous, language))
            val afterAllowed = next == null || (next.speakerId == region.speakerId &&
                MedAsrLanguageCompatibility.isEnglish(next, language))
            val start = if (beforeAllowed) maxOf(region.startMs - config.contextPaddingMs, previous?.startMs ?: 0)
                else region.startMs
            val end = if (afterAllowed) minOf(region.endMs + config.contextPaddingMs,
                next?.endMs ?: (samples.size.toLong() * 1_000 / sampleRate)) else region.endMs
            if (regions.any { other ->
                    other.startMs < end && other.endMs > start &&
                        (other.speakerId != region.speakerId || !MedAsrLanguageCompatibility.isEnglish(other, language))
                }) return@mapNotNull null
            HybridAudioWindow(sample(start, false), sample(end, true), listOf(candidate.segmentIndex), candidate.reasons)
        }.sortedBy { it.startSample }

        val merged = mutableListOf<HybridAudioWindow>()
        windows.forEach { window ->
            val previous = merged.lastOrNull()
            val adjacent = previous != null && window.regionIndices.first() == previous.regionIndices.last() + 1 &&
                regions[window.regionIndices.first()].speakerId == regions[previous.regionIndices.last()].speakerId
            if (adjacent && window.startSample - previous!!.endSample <= config.mergeGapMs * sampleRate / 1_000 &&
                (maxOf(window.endSample, previous.endSample) - previous.startSample).toLong() * 1_000 <=
                config.maximumSegmentMs * sampleRate
            ) {
                merged[merged.lastIndex] = previous.copy(
                    endSample = maxOf(previous.endSample, window.endSample),
                    regionIndices = previous.regionIndices + window.regionIndices,
                    reasons = (previous.reasons + window.reasons).distinct()
                )
            } else merged += window
        }

        val budget = (samples.size * config.maximumAudioFraction).toLong()
        var used = 0L
        var calls = 0
        var lastEnd = -1
        return buildList {
            for (window in merged) {
                checkCancelled()
                if (calls >= config.maximumMedAsrCalls) break
                val length = window.endSample - window.startSample
                val durationMs = length.toLong() * 1_000 / sampleRate
                val inferenceCalls = (length.toLong() + SherpaMedAsrTranscriptionEngine.MAX_CHUNK_SAMPLES - 1) /
                    SherpaMedAsrTranscriptionEngine.MAX_CHUNK_SAMPLES
                // Never truncate a candidate to fit the budget, or verify the entire recording.
                if (durationMs !in config.minimumSegmentMs..config.maximumSegmentMs ||
                    length >= samples.size || used + length > budget || window.startSample < lastEnd ||
                    calls + inferenceCalls > config.maximumMedAsrCalls
                ) continue
                var energy = 0.0
                for (index in window.startSample until window.endSample) {
                    if (index % 8_192 == 0) checkCancelled()
                    energy += samples[index].toDouble() * samples[index]
                }
                if (sqrt(energy / length) < config.minimumRms) continue
                add(window)
                used += length
                calls += inferenceCalls.toInt()
                lastEnd = window.endSample
            }
        }
    }

    fun extract(samples: FloatArray, window: HybridAudioWindow): FloatArray =
        samples.copyOfRange(window.startSample, window.endSample)
}
