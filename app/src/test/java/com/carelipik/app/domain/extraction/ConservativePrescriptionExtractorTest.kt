package com.carelipik.app.domain.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConservativePrescriptionExtractorTest {
    @Test
    fun extract_returnsOnlyExplicitDoctorPrescriptionWithDoseAttributes() {
        val transcript = """
            Patient: I already take metformin 500 mg daily.
            Doctor: Start amoxicillin 500 mg twice daily for 5 days.
            Patient: The pharmacy suggested azithromycin 250 mg.
        """.trimIndent()

        val medicine = ConservativePrescriptionExtractor.extract(transcript).single()

        assertEquals("amoxicillin", medicine.name)
        assertEquals("500 mg", medicine.strength)
        assertEquals("twice daily", medicine.frequency)
        assertEquals("for 5 days", medicine.duration)
        assertTrue(medicine.sourceEvidence.contains("amoxicillin"))
    }
}
