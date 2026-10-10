package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.MedicalEntity
import com.carelipik.app.domain.transcription.MedicalEntityType
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApolloHybridTranscriptReviewAnalyzerTest {
    @Test
    fun analyze_combinesModelMedicationWithRuleAttributes() {
        val transcript = "Take metformin 500 mg twice daily."
        val analyzer = ApolloHybridTranscriptReviewAnalyzer(
            recognizer = { listOf(entity(transcript, "metformin", MedicalEntityType.Medication)) },
            fallback = { _, _ -> emptyList() }
        )

        val concerns = analyzer.analyze(transcript, TranscriptionLanguage.English)

        assertEquals(listOf("metformin", "500 mg", "twice daily"), concerns.map { it.text })
        assertTrue(concerns.all { it.suggestedReplacement == null })
    }

    @Test
    fun analyze_rejectsLowConfidenceAndInvalidOffsets() {
        val transcript = "Possible asthma."
        val analyzer = ApolloHybridTranscriptReviewAnalyzer(
            recognizer = {
                listOf(
                    entity(transcript, "asthma", MedicalEntityType.Disease, confidence = 0.3f),
                    MedicalEntity("wrong", 0, 5, MedicalEntityType.Disease, 0.9f, source = "apollo")
                )
            },
            fallback = { _, _ -> emptyList() }
        )

        assertTrue(analyzer.analyze(transcript, TranscriptionLanguage.English).isEmpty())
    }

    @Test
    fun analyze_usesFallbackWhenRecognizerFails() {
        val transcript = "Patient has cough."
        val analyzer = ApolloHybridTranscriptReviewAnalyzer(
            recognizer = { error("model unavailable") }
        )

        assertEquals("cough", analyzer.analyze(transcript, TranscriptionLanguage.English).single().text)
    }

    private fun entity(
        text: String,
        value: String,
        type: MedicalEntityType,
        confidence: Float = 0.95f
    ): MedicalEntity {
        val start = text.indexOf(value)
        return MedicalEntity(
            text = value,
            startIndex = start,
            endIndexExclusive = start + value.length,
            type = type,
            confidence = confidence,
            source = "apollo-medical-ner"
        )
    }
}
