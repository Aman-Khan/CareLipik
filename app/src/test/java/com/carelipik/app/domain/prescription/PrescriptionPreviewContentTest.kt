package com.carelipik.app.domain.prescription

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.MedicationDraft
import org.junit.Assert.*
import org.junit.Test

class PrescriptionPreviewContentTest {
    @Test fun preview_preservesEscapedTextAndDoctorSelectedMedicine() {
        var source = PrescriptionTemplates.create(null, ClinicalDraft(patientAge = "30"), "Synthetic patient", "Example doctor")
        source = PrescriptionTemplates.updateDetails(source, mapOf(PrescriptionDetail.Precautions to "Use 50% & rest_soon {today}"))
        source = PrescriptionTemplates.updateMedicines(source, listOf(MedicationDraft(
            name = "Synthetic medicine", dose = "1 tablet", frequency = "morning", duration = "3 days"
        )))
        val blocks = PrescriptionPreviewContent.blocks(source)
        val text = blocks.joinToString("\n") { it.text }
        assertTrue(text.contains("Doctor: Example doctor"))
        assertTrue(text.contains("Patient: Synthetic patient"))
        assertTrue(text.contains("Use 50% & rest_soon {today}"))
        assertTrue(text.contains("Synthetic medicine"))
        assertTrue(text.contains("Dose: 1 tablet"))
        assertTrue(text.contains("Duration: 3 days"))
        assertTrue(blocks.any { it.heading && it.text == "Precautions" })
        assertFalse(text.contains("CARELIPIK"))
        assertFalse(text.contains("\\par"))
    }

    @Test fun preview_updatesWhenMedicineIsRemoved() {
        val source = PrescriptionTemplates.create(null, ClinicalDraft(), "Synthetic", "Example")
        val added = PrescriptionTemplates.updateMedicines(source, listOf(MedicationDraft(name = "Synthetic medicine")))
        val removed = PrescriptionTemplates.updateMedicines(added, emptyList())
        assertFalse(PrescriptionPreviewContent.blocks(removed).any { it.text.contains("Synthetic medicine") })
    }
}
