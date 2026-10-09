package com.carelipik.app.data.transcription

import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityAnalyzerTest {
    @Test
    fun analyze_reportsClippingAndActiveSpeech() {
        val samples = FloatArray(16_000) { index ->
            if (index < 8_000) 1f else (0.2f * sin(index / 8.0)).toFloat()
        }

        val report = AudioQualityAnalyzer.analyze(samples, 16_000)

        assertTrue(report.clippingPercent > 40f)
        assertTrue(report.activeSpeechSeconds > 0f)
        assertTrue(report.warnings.any { it.contains("clipping") })
    }

    @Test
    fun analyze_reportsMostlySilentRecording() {
        val samples = FloatArray(16_000)
        repeat(800) { samples[it] = 0.2f }

        val report = AudioQualityAnalyzer.analyze(samples, 16_000)

        assertTrue(report.silencePercent > 70f)
        assertTrue(report.warnings.any { it.contains("silence") })
    }
}
