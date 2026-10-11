package com.carelipik.app.data.extraction

import com.carelipik.app.domain.extraction.ClinicalNoteGenerationRequest
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedGemmaPromptBuilderTest {
    @Test
    fun build_indexesEachSpeakerTurnAndRequiresEvidence() {
        val prompt = MedGemmaPromptBuilder.build(request())

        assertTrue(prompt.contains("[T1] Doctor: What brings you in?"))
        assertTrue(prompt.contains("[T2] Patient: Synthetic cough."))
        assertTrue(prompt.contains("source_turn_ids"))
        assertTrue(prompt.contains("No diagnosis,"))
        assertTrue(prompt.contains("COMPACT JSON"))
    }

    @Test
    fun normalizeJson_dropsUnknownSectionsAndAddsMissingRequiredSections() {
        val normalized = MedGemmaPromptBuilder.normalizeJson(
            """Result: {"sections":[{"id":"subjective","content":"Synthetic cough","source_turn_ids":["T2"]},{"id":"invented","content":"unsafe","source_turn_ids":[]}],"prescribed_medications":[],"coverage_warnings":[]}""",
            request()
        )
        val sections = JSONObject(normalized).getJSONArray("sections")

        assertEquals(ClinicalNoteFormat.Soap.sectionDefinitions.size, sections.length())
        assertEquals("subjective", sections.getJSONObject(0).getString("id"))
        assertEquals("T2", sections.getJSONObject(0).getJSONArray("source_turn_ids").getString(0))
        assertFalse(normalized.contains("invented"))
    }

    @Test
    fun normalizeJson_removesUnsupportedContentButKeepsAReviewWarning() {
        val normalized = JSONObject(
            MedGemmaPromptBuilder.normalizeJson(
                """{"sections":[{"id":"subjective","content":"Unsupported claim","source_turn_ids":["T99"]}],"prescribed_medications":[],"coverage_warnings":[]}""",
                request()
            )
        )

        assertEquals("", normalized.getJSONArray("sections").getJSONObject(0).getString("content"))
        assertTrue(normalized.getJSONArray("coverage_warnings").getString(0).contains("omitted"))
    }

    @Test
    fun normalizeJson_recoversCompleteEvidenceLinkedSectionsFromTruncatedResponse() {
        val normalized = JSONObject(
            MedGemmaPromptBuilder.normalizeJson(
                """{"sections":[{"id":"subjective","content":"Synthetic cough","source_turn_ids":["T2"]},{"id":"objective","content":"unfinished"""",
                request()
            )
        )

        val sections = normalized.getJSONArray("sections")
        assertEquals("Synthetic cough", sections.getJSONObject(0).getString("content"))
        assertTrue(normalized.getJSONArray("coverage_warnings").getString(0).contains("incomplete"))
        assertEquals(0, normalized.getJSONArray("prescribed_medications").length())
    }

    private fun request() = ClinicalNoteGenerationRequest(
        reviewedTranscript = "Doctor: What brings you in?\nPatient: Synthetic cough.",
        sourceLanguage = TranscriptionLanguage.English,
        noteFormat = ClinicalNoteFormat.Soap,
        outputLanguage = ClinicalNoteLanguage.English,
        specialtyName = "General medicine",
        patientAge = "40",
        visitReason = "Synthetic cough"
    )
}
