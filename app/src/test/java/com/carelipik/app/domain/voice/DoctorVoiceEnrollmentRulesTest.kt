package com.carelipik.app.domain.voice

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DoctorVoiceEnrollmentRulesTest {
    @Test
    fun validation_rejectsSamplesShorterThanEightSeconds() {
        assertNotNull(DoctorVoiceEnrollmentRules.validationMessage(7_999L))
    }

    @Test
    fun validation_acceptsEightToThirtySecondSamples() {
        assertNull(DoctorVoiceEnrollmentRules.validationMessage(8_000L))
        assertNull(DoctorVoiceEnrollmentRules.validationMessage(12_000L))
        assertNull(DoctorVoiceEnrollmentRules.validationMessage(30_000L))
    }

    @Test
    fun validation_rejectsSamplesLongerThanThirtySeconds() {
        assertNotNull(DoctorVoiceEnrollmentRules.validationMessage(30_001L))
    }

    @Test
    fun quality_rejectsSilenceAndClippedAudio() {
        assertNotNull(DoctorVoiceEnrollmentRules.audioQualityMessage(FloatArray(16_000)))
        assertNotNull(DoctorVoiceEnrollmentRules.audioQualityMessage(FloatArray(16_000) { 1f }))
    }

    @Test
    fun quality_acceptsClearSpeechLevelAudio() {
        val samples = FloatArray(16_000) { index ->
            if (index % 4_000 < 3_000) 0.08f else 0f
        }

        assertNull(DoctorVoiceEnrollmentRules.audioQualityMessage(samples))
    }
}
