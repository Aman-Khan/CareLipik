package com.carelipik.app.data.extraction

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationRequest
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClinicalNoteGenerationParsingTest {
    @Test
    fun groundedResponse_parsesSectionsMedicinesAndCoverageWarnings() {
        val engine = HttpGeminiClinicalNoteGenerationEngine("")
        val request = ClinicalNoteGenerationRequest(
            reviewedTranscript = "Doctor: Take SyntheticMed.\n\nPatient: I have a cough.",
            sourceLanguage = TranscriptionLanguage.Hindi,
            noteFormat = ClinicalNoteFormat.Soap,
            outputLanguage = ClinicalNoteLanguage.English,
            specialtyName = "General medicine",
            patientAge = "47",
            visitReason = "Synthetic cough"
        )

        val draft = engine.parseDraftResponse(
            body = """{
                "sections": [
                  {"id":"subjective","title":"Subjective","content":"Patient reports cough.","source_turn_ids":["T2"]}
                ],
                "prescribed_medications": [
                  {"name":"SyntheticMed","generic_name":"","strength":"5 mg","dose":"one tablet","route":"oral","frequency":"daily","duration":"three days","instructions":"after food","source_evidence":"Doctor: Take SyntheticMed.","doctor_reviewed":true}
                ],
                "coverage_warnings":["Patient turn T4 is not represented."],
                "source":"gemini"
            }""".trimIndent(),
            request = request
        )

        assertEquals(ClinicalNoteGenerationSource.Gemini, draft.generationSource)
        assertEquals("Patient reports cough.", draft.structuredSections.first().content)
        assertEquals("SyntheticMed", draft.medications.single().name)
        assertFalse(draft.medications.single().isDoctorReviewed)
        assertEquals(1, draft.coverageWarnings.size)
        assertEquals(request.reviewedTranscript, draft.reviewedTranscript)
    }
}
