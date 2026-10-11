package com.carelipik.app.domain.prescription

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.carelipik.app.domain.model.ClinicalNoteSection
import org.junit.Assert.*
import org.junit.Test

class ReportPrescriptionSuggestionsTest {
    @Test fun dedicatedContext_showsCleanIndividualAdviceInsteadOfWholePlan() {
        val draft = ClinicalDraft(reviewedTranscript = "Doctor: Drink water and return in five days.",
            generationSource = ClinicalNoteGenerationSource.MedGemma,
            structuredSections = listOf(ClinicalNoteSection("plan", "Plan", "Entire plan", listOf("T1"))),
            prescriptionContext = listOf(ClinicalNoteSection("advice", "Advice", "Doctor: Drink water.", listOf("T1")),
                ClinicalNoteSection("follow_up", "Follow-up", "Return in five days.", listOf("T1"))))
        assertEquals(listOf("Drink water.", "Return in five days."), ReportPrescriptionSuggestions.advice(draft).map { it.content })
    }
    @Test fun legacyPlan_keepsAdviceAndExcludesMedicineRegimen() {
        val draft = ClinicalDraft(reviewedTranscript = "Doctor: tablet ExampleMed 500 mg. Drink water.",
            generationSource = ClinicalNoteGenerationSource.Gemini,
            structuredSections = listOf(ClinicalNoteSection("plan", "Plan", "Tablet ExampleMed 500 mg. Drink water.", listOf("T1"))))
        assertEquals("Drink water.", ReportPrescriptionSuggestions.advice(draft).single().content)
    }

    @Test fun advice_showsGeneratedPlanWithActualEvidence_andExcludesAssessment() {
        val draft = ClinicalDraft(
            reviewedTranscript = "Doctor: Return in five days\nPatient: Understood",
            generationSource = ClinicalNoteGenerationSource.Gemini,
            structuredSections = listOf(
                ClinicalNoteSection("plan", "Plan", "Follow up in five days", listOf("T1")),
                ClinicalNoteSection("assessment", "Assessment", "Example assessment", listOf("T1"))
            )
        )
        val advice = ReportPrescriptionSuggestions.advice(draft).single()
        assertEquals("Follow up in five days", advice.content)
        assertEquals("Doctor: Return in five days", advice.evidence)
    }

    @Test fun advice_omitsUnsupportedPlan_andRuleBasedDraft() {
        val draft = ClinicalDraft(
            reviewedTranscript = "Doctor: Example transcript",
            generationSource = ClinicalNoteGenerationSource.MedGemma,
            structuredSections = listOf(ClinicalNoteSection("plan", "Plan", "Unsupported", listOf("T99")))
        )
        assertTrue(ReportPrescriptionSuggestions.advice(draft).isEmpty())
        assertTrue(ReportPrescriptionSuggestions.advice(draft.copy(
            generationSource = ClinicalNoteGenerationSource.OfflineTranscript,
            structuredSections = listOf(ClinicalNoteSection("plan", "Plan", "Example", listOf("T1")))
        )).isEmpty())
    }
}
