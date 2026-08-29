package com.carelipik.app.data.extraction

import com.carelipik.app.domain.extraction.ClinicalExtractionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptBackedClinicalExtractionEngineTest {
    private val engine = TranscriptBackedClinicalExtractionEngine()

    @Test
    fun labelledTranscript_retainsEveryPatientStatementAndCompleteSource() {
        val transcript = """
            Doctor: What brought you in today?

            Patient: I am 47 years old. I have cough and fever for four days.

            Doctor: Any breathing problem or chest pain?

            Patient: Mild wheezing and shortness of breath on stairs. No chest pain.

            Doctor: Any past history?

            Patient: Childhood asthma, type 2 diabetes, and high blood pressure.
        """.trimIndent()

        val result = engine.extract(transcript) as ClinicalExtractionResult.Success

        assertEquals("47", result.draft.patientAge)
        assertTrue(result.draft.presentingComplaint.contains("cough and fever"))
        assertTrue(result.draft.history.contains("Mild wheezing"))
        assertTrue(result.draft.history.contains("type 2 diabetes"))
        assertTrue(result.draft.keyFindings.contains("No chest pain"))
        assertEquals(transcript, result.draft.reviewedTranscript)
        assertTrue(result.draft.assessmentNotes.isBlank())
        assertTrue(result.draft.planNotes.isBlank())
    }

    @Test
    fun unlabelledTranscript_isPreservedWithoutInventingClinicalContent() {
        val transcript = "Synthetic unlabelled reviewed transcript."

        val result = engine.extract(transcript) as ClinicalExtractionResult.Success

        assertEquals(transcript, result.draft.presentingComplaint)
        assertEquals(transcript, result.draft.reviewedTranscript)
        assertTrue(result.draft.patientAge.isBlank())
    }
}
