package com.carelipik.app.ui.screens.prescription

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteSection
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.carelipik.app.domain.model.MedicationDraft
import com.carelipik.app.domain.prescription.LetterheadRepository
import com.carelipik.app.domain.prescription.PrescriptionTemplates
import com.carelipik.app.domain.prescription.PrescriptionDetail
import org.junit.Assert.*
import org.junit.Test

class PrescriptionViewModelTest {
    private fun viewModel() = PrescriptionViewModel(
        repository = object : LetterheadRepository {
            override suspend fun load(): String? = null
            override suspend fun save(template: String) = Unit
        },
        engine = { error("Unexpected online conversion") },
        readPdf = { error("Unexpected PDF read") },
        writeDocument = { _, _ -> error("Unexpected export") },
        processAsynchronously = false
    )
    private val draft = ClinicalDraft(
        reviewedTranscript = "Doctor: tablet ExampleMed morning for 5 days",
        medications = listOf(MedicationDraft(name = "ExampleMed", isDoctorReviewed = true))
    )

    @Test fun planMedicine_populatesSuggestionsWithoutAutomaticallyPrescribing() {
        val vm = viewModel()
        vm.create(ClinicalDraft(reviewedTranscript = "Doctor: ExampleMed 500 mg morning for 5 days. Drink water.",
            generationSource = ClinicalNoteGenerationSource.MedGemma,
            structuredSections = listOf(ClinicalNoteSection("plan", "Plan", "ExampleMed 500 mg morning for 5 days. Drink water.", listOf("T1")))),
            "Synthetic patient", "Example doctor")
        assertEquals("ExampleMed", vm.uiState.value.suggestions.single().name)
        assertEquals("Drink water.", vm.uiState.value.adviceSuggestions.single().content)
        assertTrue(vm.uiState.value.medicines.isEmpty())
        assertFalse(vm.uiState.value.source.contains("ExampleMed"))
        vm.addSuggestion(0)
        assertTrue(vm.uiState.value.source.contains("ExampleMed"))
    }

    @Test fun prepare_onlySuggestsMedicines_evenWhenDraftSaysReviewed() {
        val vm = viewModel()
        vm.create(draft, "Synthetic patient", "Example doctor")
        assertEquals(1, vm.uiState.value.suggestions.size)
        assertTrue(vm.uiState.value.medicines.isEmpty())
        assertFalse(vm.uiState.value.source.contains("ExampleMed"))
    }

    @Test fun doctorAddEditAndRemove_autoSyncLatex_andPreserveOtherEdits() {
        val vm = viewModel()
        vm.create(draft, "Synthetic patient", "Example doctor")
        vm.setSource(vm.uiState.value.source.replace("Doctor signature:", "Custom footer. Doctor signature:"))
        vm.addSuggestion(0)
        assertEquals(1, vm.uiState.value.medicines.size)
        assertTrue(vm.uiState.value.source.contains("ExampleMed"))
        vm.updateMedicine(0, vm.uiState.value.medicines.single().copy(dose = "1 tablet", frequency = "morning, night", duration = "5 days"))
        assertTrue(vm.uiState.value.source.contains("Dose: 1 tablet"))
        assertTrue(vm.uiState.value.source.contains("frequency: morning, night"))
        assertTrue(vm.uiState.value.source.contains("Duration: 5 days"))
        vm.addSuggestion(0)
        assertEquals(1, vm.uiState.value.medicines.size)
        vm.removeMedicine(0)
        assertTrue(vm.uiState.value.medicines.isEmpty())
        assertFalse(vm.uiState.value.source.contains("ExampleMed"))
        assertTrue(vm.uiState.value.source.contains("Custom footer."))
        vm.create(draft, "Synthetic patient", "Example doctor")
        assertTrue(vm.uiState.value.medicines.isEmpty())
    }

    @Test fun missingMarkers_preventsMutation_andKeepsDoctorSource() {
        val vm = viewModel()
        vm.create(draft, "Synthetic patient", "Example doctor")
        val custom = vm.uiState.value.source.replace(PrescriptionTemplates.MEDICINES_START, "")
        vm.setSource(custom)
        vm.addSuggestion(0)
        assertTrue(vm.uiState.value.medicines.isEmpty())
        assertEquals(custom, vm.uiState.value.source)
        assertNotNull(vm.uiState.value.message)
    }

    @Test fun newConsultation_clearsSelectedMedicinesAndSuggestions() {
        val vm = viewModel()
        vm.create(draft, "Synthetic patient", "Example doctor")
        vm.addSuggestion(0)
        vm.reset()
        assertTrue(vm.uiState.value.medicines.isEmpty())
        assertTrue(vm.uiState.value.suggestions.isEmpty())
        assertEquals("", vm.uiState.value.source)
    }
    @Test fun generatedReport_populatesSuggestions_withoutAutomaticallySelectingThem() {
        val vm = viewModel()
        val generated = draft.copy(
            generationSource = ClinicalNoteGenerationSource.MedGemma,
            structuredSections = listOf(ClinicalNoteSection("plan", "Plan", "Return in five days", listOf("T1")))
        )
        vm.create(generated, "Synthetic patient", "Example doctor")
        assertEquals(1, vm.uiState.value.suggestions.size)
        assertEquals(1, vm.uiState.value.adviceSuggestions.size)
        assertTrue(vm.uiState.value.selectedAdvice.isEmpty())
        assertTrue(vm.uiState.value.medicines.isEmpty())
        assertFalse(vm.uiState.value.source.contains("Return in five days"))
        vm.addAdviceSuggestion(0)
        assertTrue(vm.uiState.value.source.contains("Return in five days"))
        vm.addSuggestion(0)
        assertTrue(vm.uiState.value.source.contains("Return in five days"))
        vm.create(generated.copy(structuredSections = emptyList()), "Synthetic patient", "Example doctor")
        assertEquals(1, vm.uiState.value.selectedAdvice.size)
        vm.removeAdvice(0)
        assertFalse(vm.uiState.value.source.contains("Return in five days"))
        assertTrue(vm.uiState.value.source.contains("ExampleMed"))
    }

    @Test fun symptomReferencesAndPrescriptionDetails_requireDoctorAction_andSyncSeparately() {
        val vm = viewModel()
        val generated = draft.copy(
            generationSource = ClinicalNoteGenerationSource.MedGemma,
            structuredSections = listOf(ClinicalNoteSection("subjective", "Subjective", "Synthetic complaint", listOf("T1")))
        )
        vm.create(generated, "Synthetic patient", "Example doctor")
        assertEquals(1, vm.uiState.value.symptomReferences.size)
        assertFalse(vm.uiState.value.source.contains("Synthetic complaint"))
        vm.addSuggestion(0)
        vm.addSymptomReference(0)
        vm.setDetail(PrescriptionDetail.Precautions, "Synthetic precaution & instruction")
        assertTrue(vm.uiState.value.source.contains("Synthetic complaint"))
        assertTrue(vm.uiState.value.source.contains("Synthetic precaution \\& instruction"))
        assertTrue(vm.uiState.value.source.contains("ExampleMed"))
        vm.create(generated, "Synthetic patient", "Example doctor")
        assertEquals("Synthetic complaint", vm.uiState.value.details[PrescriptionDetail.Symptoms])
        vm.reset()
        assertTrue(vm.uiState.value.details.isEmpty())
        assertTrue(vm.uiState.value.symptomReferences.isEmpty())
    }

    @Test fun modelDiscussedMedicines_areSuggestionsOnly() {
        val vm = viewModel()
        vm.create(ClinicalDraft(
            reviewedTranscript = "Patient: I used ExampleMed last year",
            generationSource = ClinicalNoteGenerationSource.MedGemma,
            discussedMedications = listOf(MedicationDraft(name = "ExampleMed"))
        ), "Synthetic patient", "Example doctor")
        assertEquals("ExampleMed", vm.uiState.value.suggestions.single().name)
        assertTrue(vm.uiState.value.medicines.isEmpty())
        assertFalse(vm.uiState.value.source.contains("ExampleMed"))
    }

}
