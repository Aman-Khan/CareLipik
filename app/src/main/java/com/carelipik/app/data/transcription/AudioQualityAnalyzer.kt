package com.carelipik.app.data.transcription

import kotlin.math.log10
import kotlin.math.sqrt

internal data class AudioQualityReport(
    val clippingPercent: Float,
    val silencePercent: Float,
    val activeSpeechSeconds: Float,
    val estimatedSnrDb: Float,
    val warnings: List<String>
)

/** Lightweight, deterministic preflight analysis. No recording data leaves the device. */
internal object AudioQualityAnalyzer {
    fun analyze(samples: FloatArray, sampleRate: Int): AudioQualityReport {
        if (samples.isEmpty() || sampleRate <= 0) {
            return AudioQualityReport(0f, 100f, 0f, 0f, listOf("No usable audio was recorded."))
        }
        val frameSize = (sampleRate / 50).coerceAtLeast(1) // 20 ms
        val frameRms = samples.asList().chunked(frameSize).map { frame ->
            sqrt(frame.sumOf { sample -> (sample * sample).toDouble() } / frame.size).toFloat()
        }
        val sorted = frameRms.sorted()
        val noiseFloor = sorted[(sorted.lastIndex * 0.2f).toInt().coerceAtLeast(0)]
        val speechThreshold = maxOf(MIN_SPEECH_RMS, noiseFloor * NOISE_MULTIPLIER)
        val activeFrames = frameRms.count { it >= speechThreshold }
        val silencePercent = 100f * (frameRms.size - activeFrames) / frameRms.size
        val clippingPercent = 100f * samples.count { kotlin.math.abs(it) >= CLIPPING_LEVEL } /
            samples.size
        val speechRms = frameRms.filter { it >= speechThreshold }.average().toFloat()
        val snrDb = if (noiseFloor > 0f && speechRms > 0f) {
            (20f * log10(speechRms / noiseFloor)).coerceIn(0f, 80f)
        } else {
            0f
        }
        val activeSeconds = activeFrames * frameSize.toFloat() / sampleRate
        val warnings = buildList {
            if (clippingPercent >= 0.5f) add("Audio is clipping. Move the phone farther away or lower the speaking volume.")
            if (silencePercent >= 70f) add("Most of the recording is silence; check microphone distance and placement.")
            if (activeSeconds < 2f) add("Too little active speech was detected for reliable transcription.")
            if (noiseFloor >= 0.025f || (snrDb in 0f..8f && activeSeconds >= 2f)) {
                add("Background noise is high; speaker separation may be unreliable.")
            }
            if (speechRms in 0f..<0.018f && activeSeconds >= 2f) {
                add("Speech is very quiet; move the phone closer to the speakers.")
            }
        }
        return AudioQualityReport(clippingPercent, silencePercent, activeSeconds, snrDb, warnings)
    }

    private const val MIN_SPEECH_RMS = 0.012f
    private const val NOISE_MULTIPLIER = 2.5f
    private const val CLIPPING_LEVEL = 0.98f
}
