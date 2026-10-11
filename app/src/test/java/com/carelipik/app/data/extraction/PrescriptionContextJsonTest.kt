package com.carelipik.app.data.extraction

import com.carelipik.app.domain.extraction.ClinicalNoteGenerationRequest
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PrescriptionContextJsonTest {
    private val request = ClinicalNoteGenerationRequest(
        reviewedTranscript = "Doctor: Take tablet ExampleMed 500 mg morning for 5 days. Drink water. Return in five days.\nPatient: Synthetic cough.",
        sourceLanguage = TranscriptionLanguage.English, noteFormat = ClinicalNoteFormat.Soap,
        outputLanguage = ClinicalNoteLanguage.English, specialtyName = "", patientAge = "", visitReason = "")
    private val context = """{"medicine_suggestions":[{"name":"ExampleMed","strength":"500 mg","frequency":"morning","duration":"5 days","source_turn_ids":["T1"]}],"symptoms":[{"content":"Synthetic cough.","source_turn_ids":["T2"]}],"advice":[{"content":"Doctor: Drink water.","source_turn_ids":["T1"]}],"follow_up":[{"content":"Return in five days.","source_turn_ids":["T1"]}]}"""

    @Test fun contextDetails_areNotLostWhenLegacyMedicineArrayIsIncomplete() {
        val draft = HttpGeminiClinicalNoteGenerationEngine("https://unused.invalid")
            .parseDraftResponse("""{"prescription_context":$context,"discussed_medications":[{"name":"ExampleMed"}]}""", request)
        val suggestions = com.carelipik.app.domain.prescription.TranscriptMedicineSuggestions()
            .extract(draft.reviewedTranscript, draft.discussedMedications)
        assertEquals("500 mg", suggestions.single().strength)
        assertEquals("morning", suggestions.single().frequency)
        assertEquals("5 days", suggestions.single().duration)
    }

    @Test fun parser_readsDedicatedMedicineAndAdviceWithoutPlan() {
        val draft = HttpGeminiClinicalNoteGenerationEngine("https://unused.invalid")
            .parseDraftResponse("""{"prescription_context":$context}""", request)
        assertEquals("ExampleMed", draft.discussedMedications.single().name)
        assertEquals("500 mg", draft.discussedMedications.single().strength)
        assertEquals(listOf("symptoms", "advice", "follow_up"), draft.prescriptionContext.map { it.id })
        assertEquals("Drink water.", draft.prescriptionContext[1].content)
        assertTrue(draft.medications.isEmpty())
    }
    @Test fun context_dropsInventedMedicinesAndInvalidEvidence() {
        val normalized = PrescriptionContextJson.normalize(JSONObject("""{"medicine_suggestions":[{"name":"InventedMed","source_turn_ids":["T1"]},{"name":"ExampleMed","source_turn_ids":["T99"]}],"advice":[{"content":"Unsupported advice","source_turn_ids":["T99"]}]}"""), request.reviewedTranscript)
        assertEquals(0, normalized.getJSONArray("medicine_suggestions").length())
        assertEquals(0, normalized.getJSONArray("advice").length())
    }
    @Test fun incompleteLocalResponse_recoversCompletePrescriptionContext() {
        val normalized = JSONObject(MedGemmaPromptBuilder.normalizeJson(
            """{"prescription_context":$context,"sections":[{"id":"plan","content":"unfinished"""", request))
        assertEquals(1, normalized.getJSONObject("prescription_context").getJSONArray("medicine_suggestions").length())
        assertEquals("Drink water.", normalized.getJSONObject("prescription_context").getJSONArray("advice").getJSONObject(0).getString("content"))
    }
    @Test fun cloudSchema_requiresSeparatePrescriptionFields() {
        val schema = DirectGeminiClinicalNoteGenerationEngine { null }.schema(request)
        val fields = schema.getJSONObject("properties").getJSONObject("prescription_context")
            .getJSONObject("properties")
        listOf("medicine_suggestions", "symptoms", "advice", "precautions", "investigations", "follow_up")
            .forEach { assertTrue(fields.has(it)) }
    }
}
