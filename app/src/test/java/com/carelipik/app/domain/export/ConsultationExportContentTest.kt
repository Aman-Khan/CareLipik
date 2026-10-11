package com.carelipik.app.domain.export

import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.domain.model.ClinicalNoteSection
import com.carelipik.app.domain.model.MedicationDraft
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
        assertTrue(json.contains("Complete reviewed conversation log"))
        assertFalse(json.contains(".wav"))
    }

    @Test
    fun plainTextUsesLabelledEhrSections() {
        val text = ConsultationExportContent.plainText(consultation())

        assertTrue(text.contains("CHIEF COMPLAINT"))
        assertTrue(text.contains("ASSESSMENT"))
        assertTrue(text.contains("PLAN"))
        assertTrue(text.contains("PRESCRIBED MEDICINES AND DOSAGES"))
        assertTrue(text.contains("Synthetic medicine"))
        assertTrue(text.contains("COMPLETE REVIEWED CONVERSATION LOG"))
        assertTrue(text.contains("Patient: Synthetic answer"))
        assertTrue(text.contains("Consultation audio is not included."))
    }

    @Test
    fun transcriptCanBeExcludedAndElectronicSignatureIsIncluded() {
        val consultation = consultation().copy(
            includeReviewedTranscriptInExport = false,
            electronicSignerName = "Dr Synthetic",
            electronicallySignedAtMillis = 1_788_000_000_000L,
            handwrittenSignature = "0.1,0.2;0.8,0.7"
        )

        val text = ConsultationExportContent.plainText(consultation)
        val json = ConsultationExportContent.structuredJson(consultation)

        assertFalse(text.contains("COMPLETE REVIEWED CONVERSATION LOG"))
        assertFalse(text.contains("Patient: Synthetic answer"))
        assertTrue(text.contains("ELECTRONICALLY SIGNED BY: Dr Synthetic"))
        assertTrue(json.contains("\"reviewedTranscript\": null"))
        assertTrue(json.contains("Dr Synthetic"))
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
            reviewedTranscript = "Doctor: Synthetic question\n\nPatient: Synthetic answer",
            noteFormat = ClinicalNoteFormat.HistoryAndPhysical,
            noteLanguage = ClinicalNoteLanguage.English,
            structuredSections = listOf(
                ClinicalNoteSection("chief_complaint", "Chief complaint", "Synthetic complaint"),
                ClinicalNoteSection(
                    "history_present_illness",
                    "History of present illness",
                    "Synthetic history"
                ),
                ClinicalNoteSection("assessment", "Assessment", "Synthetic assessment"),
                ClinicalNoteSection("plan", "Plan", "Synthetic plan")
            ),
            medications = listOf(
                MedicationDraft(
                    name = "Synthetic medicine",
                    strength = "5 mg",
                    dose = "One tablet",
                    isDoctorReviewed = true
                )
            )
        )
    )
}
