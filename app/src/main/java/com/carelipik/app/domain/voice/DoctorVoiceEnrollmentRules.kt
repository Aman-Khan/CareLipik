package com.carelipik.app.domain.voice

import kotlin.math.sqrt

object DoctorVoiceEnrollmentRules {
    const val minimumDurationMillis = 8_000L
    const val recommendedDurationMillis = 12_000L
    const val maximumDurationMillis = 30_000L

    fun validationMessage(durationMillis: Long): String? = when {
        durationMillis < minimumDurationMillis ->
            "Record at least 8 seconds so the doctor voice sample is reliable."
        durationMillis > maximumDurationMillis ->
            "Keep the doctor voice sample under 30 seconds."
        else -> null
    }

    fun audioQualityMessage(samples: FloatArray): String? {
        if (samples.isEmpty()) return "No voice was detected. Please record the sample again."
        val clippedRatio = samples.count { sample -> kotlin.math.abs(sample) >= 0.98f } /
            samples.size.toFloat()
        if (clippedRatio > MAXIMUM_CLIPPED_RATIO) {
            return "The sample is too loud or distorted. Move slightly away from the microphone."
        }
        val frames = samples.asSequence().chunked(QUALITY_FRAME_SAMPLES)
            .map { frame ->
                sqrt(frame.sumOf { sample -> sample.toDouble() * sample } / frame.size)
            }
            .toList()
        val activeRatio = frames.count { rms -> rms >= ACTIVE_SPEECH_RMS } / frames.size.toFloat()
        if (activeRatio < MINIMUM_ACTIVE_FRAME_RATIO) {
            return "Not enough clear speech was detected. Try again in a quieter room."
        }
        return null
    }

    private const val QUALITY_FRAME_SAMPLES = 1_600
    private const val ACTIVE_SPEECH_RMS = 0.008
    private const val MINIMUM_ACTIVE_FRAME_RATIO = 0.18f
    private const val MAXIMUM_CLIPPED_RATIO = 0.05f
}
