package com.carelipik.app.domain.export

import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultationExportContentTest {
    @Test
    fun structuredJsonContainsApprovedFieldsAndNoAudioPath() {
        val json = ConsultationExportContent.structuredJson(consultation())

        assertTrue(json.contains("\"doctorApproved\": true"))
        assertTrue(json.contains("\"includesConsultationAudio\": false"))
        assertTrue(json.contains("Synthetic history"))
        assertTrue(json.contains("Doctor: Synthetic question"))
        assertFalse(json.contains("localPath"))
        assertFalse(json.contains(".wav"))
    }

    @Test
    fun fhirR4DocumentHasCompositionFirstAndDocumentReference() {
        val json = ConsultationExportContent.fhirR4Bundle(consultation())

        assertTrue(json.contains("\"resourceType\": \"Bundle\""))
        assertTrue(json.contains("\"type\": \"document\""))
        val compositionIndex = json.indexOf("\"resourceType\": \"Composition\"")
        val patientIndex = json.indexOf("\"resourceType\": \"Patient\"")
        val documentReferenceIndex = json.indexOf("\"resourceType\": \"DocumentReference\"")
        assertTrue(compositionIndex > 0)
        assertTrue(patientIndex > compositionIndex)
        assertTrue(documentReferenceIndex > patientIndex)
        assertTrue(json.contains("\"code\": \"11488-4\""))
        assertTrue(json.contains("Complete reviewed transcript"))
        assertFalse(json.contains(".wav"))
    }

    @Test
    fun plainTextUsesLabelledEhrSections() {
        val text = ConsultationExportContent.plainText(consultation())

        assertTrue(text.contains("PRESENTING COMPLAINT"))
        assertTrue(text.contains("ASSESSMENT NOTES"))
        assertTrue(text.contains("PLAN NOTES"))
        assertTrue(text.contains("COMPLETE REVIEWED TRANSCRIPT"))
        assertTrue(text.contains("Patient: Synthetic answer"))
        assertTrue(text.contains("Consultation audio is not included."))
    }

    private fun consultation() = ApprovedConsultation(
        id = "00000000-0000-0000-0000-000000000001",
        approvedAtMillis = 1_788_000_000_000L,
        patientName = "Synthetic reference \"A\"",
        patientAge = "47",
        visitReason = "Synthetic visit",
        draft = ClinicalDraft(
            presentingComplaint = "Synthetic complaint",
            history = "Synthetic history",
            keyFindings = "Synthetic findings",
            assessmentNotes = "Synthetic assessment",
            planNotes = "Synthetic plan",
            reviewedTranscript = "Doctor: Synthetic question\n\nPatient: Synthetic answer"
        )
    )
}
