package com.carelipik.app.domain.transcription

import org.junit.Assert.*
import org.junit.Test

class PatientNameMaskingTest {
    @Test fun matcher_handlesCaseAndPartialNamesWithoutMatchingSubstrings() {
        assertTrue(PatientNameMatcher.containsName("VERMA has a cough", "Rahul Verma"))
        assertTrue(PatientNameMatcher.containsName("Hello Rahul.", "Rahul Verma"))
        assertFalse(PatientNameMatcher.containsName("Rahulish", "Rahul Verma"))
        assertFalse(PatientNameMatcher.containsName("has a cough", "Rahul Verma"))
    }

    @Test fun matcher_handlesHindiScript() {
        assertTrue(PatientNameMatcher.containsName("राहुल को बुखार है", "राहुल वर्मा"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun matcher_rejectsMissingName() { PatientNameMatcher.containsName("speech", "") }

    @Test fun scrubber_replacesSpeechOnlyInsideWindowAndPreservesOriginal() {
        val original = FloatArray(10) { 0.8f }
        val masked = AudioWaveformScrubber.scrub(original, 10, listOf(AudioMaskWindow(0.2, 0.5))) { 0.01f }
        assertArrayEquals(FloatArray(10) { 0.8f }, original, 0f)
        assertArrayEquals(floatArrayOf(.8f, .8f, .01f, .01f, .01f, .8f, .8f, .8f, .8f, .8f), masked, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun scrubber_rejectsWindowsOutsideRecording() {
        AudioWaveformScrubber.scrub(FloatArray(10), 10, listOf(AudioMaskWindow(0.5, 2.0))) { 0f }
    }
}
