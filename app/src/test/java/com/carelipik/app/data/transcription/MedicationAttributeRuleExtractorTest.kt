package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.MedicalAssertion
import com.carelipik.app.domain.transcription.MedicalEntity
import com.carelipik.app.domain.transcription.MedicalEntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationAttributeRuleExtractorTest {
    private val extractor = MedicationAttributeRuleExtractor()

    @Test
    fun analyze_linksStrengthFrequencyDurationAndRouteToMedication() {
        val text = "Take metformin 500 mg one tablet orally twice daily for 30 days."

        val result = extractor.analyze(text, listOf(entity(text, "metformin", MedicalEntityType.Medication)))

        val medication = result.medications.single()
        assertEquals("500 mg", medication.strength?.text)
        assertEquals("one tablet", medication.dose?.text)
        assertEquals("orally", medication.route?.text)
        assertEquals("twice daily", medication.frequency?.text)
        assertEquals("for 30 days", medication.duration?.text)
    }

    @Test
    fun analyze_doesNotLinkAttributeAcrossSentenceBoundary() {
        val text = "Continue metformin. Amlodipine is 5 mg once daily."

        val result = extractor.analyze(text, listOf(entity(text, "metformin", MedicalEntityType.Medication)))

        assertNull(result.medications.single().strength)
        assertNull(result.medications.single().frequency)
    }

    @Test
    fun analyze_marksNegatedDiseaseWithoutRemovingIt() {
        val text = "Patient has never had tuberculosis."

        val result = extractor.analyze(text, listOf(entity(text, "tuberculosis", MedicalEntityType.Disease)))

        assertEquals(MedicalAssertion.Negated, result.entities.single().assertion)
    }

    @Test
    fun analyze_extractsHindiFrequency() {
        val text = "मेटफॉर्मिन 500 मिलीग्राम दिन में दो बार लें।"

        val result = extractor.analyze(text, listOf(entity(text, "मेटफॉर्मिन", MedicalEntityType.Medication)))

        assertEquals("500 मिलीग्राम", result.medications.single().strength?.text)
        assertEquals("दिन में दो बार", result.medications.single().frequency?.text)
    }

    private fun entity(text: String, value: String, type: MedicalEntityType): MedicalEntity {
        val start = text.indexOf(value)
        return MedicalEntity(
            text = value,
            startIndex = start,
            endIndexExclusive = start + value.length,
            type = type,
            confidence = 0.95f,
            source = "apollo-medical-ner"
        )
    }
}
