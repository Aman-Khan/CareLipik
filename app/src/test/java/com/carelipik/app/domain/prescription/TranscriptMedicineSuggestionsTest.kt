package com.carelipik.app.domain.prescription

import com.carelipik.app.domain.model.MedicationDraft
import org.junit.Assert.*
import org.junit.Test

class TranscriptMedicineSuggestionsTest {
    private val extractor = TranscriptMedicineSuggestions()

    @Test fun planFallback_doesNotTreatDosageFormsAsMedicineNames() {
        assertTrue(extractor.extractFromPlan("Doctor: One tablet 500 mg daily", "One tablet 500 mg daily").isEmpty())
    }

    @Test fun planFallback_keepsEachMedicinesTimingSeparate() {
        val text = "ExampleOne 500 mg morning and ExampleTwo 100 mg night"
        val suggestions = extractor.extractFromPlan(text, text)
        assertEquals(listOf("morning", "night"), suggestions.map { it.frequency })
    }

    @Test fun planFallback_recoversMentionedMedicineAndRejectsInventedOne() {
        val suggestions = extractor.extractFromPlan("Doctor: ExampleMed 500 mg morning for 5 days",
            "ExampleMed 500 mg morning for 5 days. InventedMed 100 mg.")
        assertEquals("ExampleMed", suggestions.single().name)
        assertEquals("500 mg", suggestions.single().strength)
    }
    @Test fun partialStructuredMedicine_isEnrichedFromExplicitTranscript() {
        val suggestion = extractor.extract("Doctor: tablet ExampleMed 500 mg morning for 5 days",
            listOf(MedicationDraft(name = "ExampleMed"))).single()
        assertEquals("500 mg", suggestion.strength)
        assertEquals("morning", suggestion.frequency)
        assertEquals("5 days", suggestion.duration)
    }

    @Test fun extract_doesNotSuggestMedicines_fromSymptoms() {
        assertTrue(extractor.extract("Patient: I have a headache and fever. Doctor: Take rest and drink water.").isEmpty())
    }

    @Test fun extract_keepsExplicitMentionsWithEvidence_andLeavesMissingDetailsBlank() {
        val medicine = extractor.extract("Doctor: tablet ExampleMed 500 mg morning and night for 5 days").single()
        assertEquals("ExampleMed", medicine.name)
        assertEquals("500 mg", medicine.strength)
        assertEquals("morning, night", medicine.frequency)
        assertEquals("5 days", medicine.duration)
        assertEquals("", medicine.dose)
        assertFalse(medicine.isDoctorReviewed)
        assertTrue(medicine.sourceEvidence.contains("tablet ExampleMed"))
    }

    @Test fun extract_filtersUnsupportedExtractedNames_andDeduplicatesMentions() {
        val suggestions = extractor.extract("Tablet ExampleMed. Tablet ExampleMed.", listOf(
            MedicationDraft(name = "InventedMed", isDoctorReviewed = true),
            MedicationDraft(name = "ExampleMed", isDoctorReviewed = true)
        ))
        assertEquals(1, suggestions.size)
        assertEquals("ExampleMed", suggestions.single().name)
        assertFalse(suggestions.single().isDoctorReviewed)
    }

    @Test fun extract_doesNotInventDoseTimesOrDuration() {
        val suggestion = extractor.extract("Patient: I previously took tablet ExampleMed").single()
        assertEquals("", suggestion.dose)
        assertEquals("", suggestion.frequency)
        assertEquals("", suggestion.duration)
    }
    @Test fun extract_discardsDetailsThatAreNotInTranscript() {
        val suggestion = extractor.extract("Patient: I previously took ExampleMed", listOf(
            MedicationDraft(name = "ExampleMed", dose = "2 tablets", frequency = "morning", duration = "7 days")
        )).single()
        assertEquals("", suggestion.dose)
        assertEquals("", suggestion.frequency)
        assertEquals("", suggestion.duration)
        assertEquals("Patient: I previously took ExampleMed", suggestion.sourceEvidence)
    }

}
