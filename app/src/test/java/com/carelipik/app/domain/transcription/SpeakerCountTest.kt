package com.carelipik.app.domain.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerCountTest {
    @Test
    fun fixedClustering_forwardsChosenCountInsteadOfAutomaticEstimate() {
        var receivedCount = -1
        val samples = floatArrayOf(0.1f)
        val delegate = SpeakerDiarizationEngine { receivedSamples, rate, count ->
            assertEquals(samples, receivedSamples)
            assertEquals(16000, rate)
            receivedCount = count
            SpeakerDiarizationResult.Success(emptyList())
        }
        FixedCountSpeakerDiarizationEngine(delegate, 3).diarize(samples, 16000, -1)
        assertEquals(3, receivedCount)
    }

    @Test
    fun validCounts_allowSingleSpeakerAndGroups() {
        assertEquals(1, SpeakerCount.validate(1))
        assertEquals(4, SpeakerCount.validate(4))
        assertEquals(10, SpeakerCount.validate(10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroSpeakers_isRejected() {
        SpeakerCount.validate(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun excessiveCount_isRejected() {
        SpeakerCount.validate(11)
    }
}
