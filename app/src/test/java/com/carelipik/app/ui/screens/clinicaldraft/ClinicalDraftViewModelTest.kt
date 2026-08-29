package com.carelipik.app.ui.screens.clinicaldraft

import com.carelipik.app.domain.extraction.ClinicalExtractionEngine
import com.carelipik.app.domain.extraction.ClinicalExtractionResult
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationEngine
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationRequest
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationResult
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.domain.model.ClinicalNoteSection
import com.carelipik.app.domain.model.MedicationDraft
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalDraftViewModelTest {
    @Test
    fun successfulExtraction_createsEditableDraft() {
        val draft = ClinicalDraft(presentingComplaint = "Synthetic complaint")
        val viewModel = ClinicalDraftViewModel(
            engine = StubEngine(ClinicalExtractionResult.Success(draft)),
            processAsynchronously = false
        )

        viewModel.generate("Synthetic transcript")
        viewModel.setHistory("Doctor-corrected history")

        assertEquals(ClinicalDraftStatus.Ready, viewModel.uiState.value.status)
        assertEquals("Doctor-corrected history", viewModel.uiState.value.draft.history)
        assertTrue(viewModel.validateForContinue())
    }

    @Test
    fun failedExtraction_displaysErrorAndPreventsContinue() {
        val viewModel = ClinicalDraftViewModel(
            engine = StubEngine(ClinicalExtractionResult.Failure("Engine unavailable")),
            processAsynchronously = false
        )

        viewModel.generate("Synthetic transcript")

        assertEquals(ClinicalDraftStatus.Error, viewModel.uiState.value.status)
        assertEquals("Engine unavailable", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.canContinue)
    }

    @Test
    fun emptyDraft_requiresContentBeforeDoctorReview() {
        val viewModel = ClinicalDraftViewModel(
            engine = StubEngine(ClinicalExtractionResult.Success(ClinicalDraft())),
            processAsynchronously = false
        )

        viewModel.generate("Synthetic transcript")

        assertFalse(viewModel.validateForContinue())
        assertEquals("Review and complete at least one section", viewModel.uiState.value.draftError)
    }

    @Test
    fun prescribedMedicine_requiresExplicitDoctorReview() {
        val viewModel = ClinicalDraftViewModel(
            engine = StubEngine(
                ClinicalExtractionResult.Success(ClinicalDraft(history = "Synthetic history"))
            ),
            processAsynchronously = false
        )
        viewModel.generate("Synthetic transcript")
        viewModel.addMedication()
        viewModel.updateMedication(
            0,
            MedicationDraft(name = "Synthetic medicine", dose = "One tablet")
        )

        assertFalse(viewModel.validateForContinue())
        assertEquals(
            "Doctor review is required for every medicine and dosage",
            viewModel.uiState.value.draftError
        )

        viewModel.updateMedication(
            0,
            viewModel.uiState.value.draft.medications.single().copy(isDoctorReviewed = true)
        )

        assertTrue(viewModel.validateForContinue())
    }

    @Test
    fun noteFormatSelection_createsTheRequestedEditableSections() {
        val viewModel = ClinicalDraftViewModel(
            engine = StubEngine(
                ClinicalExtractionResult.Success(
                    ClinicalDraft(
                        presentingComplaint = "Synthetic concern",
                        history = "Synthetic history"
                    )
                )
            ),
            processAsynchronously = false
        )
        viewModel.generate("Patient: Synthetic history")

        viewModel.selectNoteFormat(ClinicalNoteFormat.HistoryAndPhysical)

        assertEquals(ClinicalNoteFormat.HistoryAndPhysical, viewModel.uiState.value.draft.noteFormat)
        assertEquals(
            ClinicalNoteFormat.HistoryAndPhysical.sectionDefinitions.map { it.id },
            viewModel.uiState.value.draft.structuredSections.map { it.id }
        )
    }

    @Test
    fun hindiTranscript_requiresSuccessfulGeminiGenerationForEnglishNote() {
        val generatedDraft = ClinicalDraft(
            noteFormat = ClinicalNoteFormat.Soap,
            noteLanguage = ClinicalNoteLanguage.English,
            structuredSections = listOf(
                ClinicalNoteSection("subjective", "Subjective", "Synthetic English summary")
            ),
            generationSource = ClinicalNoteGenerationSource.Gemini
        )
        val onlineEngine = StubOnlineEngine(
            ClinicalNoteGenerationResult.Success(generatedDraft)
        )
        val viewModel = ClinicalDraftViewModel(
            engine = StubEngine(
                ClinicalExtractionResult.Success(ClinicalDraft(history = "कृत्रिम इतिहास"))
            ),
            onlineEngine = onlineEngine,
            processAsynchronously = false
        )
        viewModel.generate(
            transcript = "Patient: कृत्रिम इतिहास",
            language = TranscriptionLanguage.Hindi
        )
        viewModel.selectNoteLanguage(ClinicalNoteLanguage.English)

        assertFalse(viewModel.validateForContinue())
        assertTrue(viewModel.uiState.value.needsEnglishGeneration)

        viewModel.setOnlineGenerationConsent(true)
        viewModel.generateWithGemini()

        assertEquals(ClinicalNoteGenerationSource.Gemini, viewModel.uiState.value.draft.generationSource)
        assertEquals(ClinicalNoteLanguage.English, onlineEngine.request?.outputLanguage)
        assertFalse(viewModel.uiState.value.needsEnglishGeneration)
    }

    private class StubEngine(
        private val result: ClinicalExtractionResult
    ) : ClinicalExtractionEngine {
        override fun extract(transcript: String): ClinicalExtractionResult = result
    }

    private class StubOnlineEngine(
        private val result: ClinicalNoteGenerationResult
    ) : ClinicalNoteGenerationEngine {
        var request: ClinicalNoteGenerationRequest? = null

        override fun generate(request: ClinicalNoteGenerationRequest): ClinicalNoteGenerationResult {
            this.request = request
            return result
        }
    }
}
