package com.carelipik.app.data.extraction

import com.carelipik.app.domain.extraction.ClinicalExtractionEngine
import com.carelipik.app.domain.extraction.ClinicalExtractionResult
import com.carelipik.app.domain.model.ClinicalDraft

/** Deterministic UI-development output; it does not interpret real clinical content. */
class FakeClinicalExtractionEngine : ClinicalExtractionEngine {
    override fun extract(transcript: String): ClinicalExtractionResult {
        if (transcript.isBlank()) {
            return ClinicalExtractionResult.Failure("A reviewed transcript is required.")
        }
        return ClinicalExtractionResult.Success(
            ClinicalDraft(
                presentingComplaint = "Mild cough for three days.",
                history = "Patient reports a cough lasting three days.",
                keyFindings = "Fever and breathing difficulty were asked about; responses were not documented in the sample transcript.",
                assessmentNotes = "Not established from the sample transcript. Doctor review required.",
                planNotes = "Not established from the sample transcript. Doctor to complete."
            )
        )
    }
}
