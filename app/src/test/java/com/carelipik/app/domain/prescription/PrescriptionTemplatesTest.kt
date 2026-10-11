package com.carelipik.app.domain.prescription

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.MedicationDraft
import org.junit.Assert.*
import org.junit.Test

class PrescriptionTemplatesTest {
    @Test fun reusableTemplate_fillsEscapedProfileAndPatientFieldsWithoutDuplicates() {
        val template = PrescriptionTemplates.common.replace(PrescriptionTemplates.BODY,
            "${PrescriptionTemplates.DOCTOR}\n${PrescriptionTemplates.PATIENT}\n${PrescriptionTemplates.BODY}")
        val result = PrescriptionTemplates.create(template, ClinicalDraft(patientAge = "30"),
            "Synthetic & patient", "Example_ doctor", "Example specialty", "REG#123", "Example clinic")
        assertTrue(result.contains("Synthetic \\& patient"))
        assertTrue(result.contains("Example\\_ doctor"))
        assertTrue(result.contains("REG\\#123"))
        assertTrue(result.contains("Example specialty"))
        assertTrue(result.contains("Example clinic"))
        assertEquals(2, result.split("Doctor:").size)
        assertEquals(2, result.split("Patient:").size)
        val updated = PrescriptionTemplates.updateMedicines(result, listOf(MedicationDraft(name = "ExampleMed")))
        assertTrue(updated.contains("ExampleMed"))
        assertTrue(updated.contains("REG\\#123"))
    }

    @Test fun validate_rejectsPlaceholderOutsideDocument() {
        val invalid = PrescriptionTemplates.common.replace(PrescriptionTemplates.BODY, "") + "\n" + PrescriptionTemplates.BODY
        assertThrows(IllegalArgumentException::class.java) { PrescriptionTemplates.validate(invalid) }
    }

    @Test fun validate_rejectsDuplicateDoctorPlaceholder() {
        val invalid = PrescriptionTemplates.common.replace(PrescriptionTemplates.BODY,
            "${PrescriptionTemplates.DOCTOR}\n${PrescriptionTemplates.DOCTOR}\n${PrescriptionTemplates.BODY}")
        assertThrows(IllegalArgumentException::class.java) { PrescriptionTemplates.validate(invalid) }
    }

    @Test fun create_usesCommonLayout_whenLetterheadIsMissingOrInvalid() {
        val fallback = PrescriptionTemplates.create(null, ClinicalDraft(), "Example", "Doctor")
        assertTrue(fallback.contains("\\documentclass"))
        assertFalse(fallback.contains(PrescriptionTemplates.BODY))
        assertEquals(fallback, PrescriptionTemplates.create("invalid", ClinicalDraft(), "Example", "Doctor"))
    }

    @Test fun create_preservesSavedLetterhead_butDoesNotAutomaticallyIncludeAnyMedicine() {
        val template = PrescriptionTemplates.common.replace("\\begin{document}", "\\begin{document}\nExample Clinic")
        val draft = ClinicalDraft(medications = listOf(
            MedicationDraft(name = "Verified medicine", dose = "Example dose", isDoctorReviewed = true),
            MedicationDraft(name = "Unverified medicine")
        ))
        val source = PrescriptionTemplates.create(template, draft, "Example", "Doctor")
        assertTrue(source.contains("Example Clinic"))
        assertFalse(source.contains("Verified medicine"))
        assertFalse(source.contains("Unverified medicine"))
    }

    @Test fun escape_treatsPatientAndMedicineContent_asPlainText() {
        assertEquals("A\\&B\\_C\\%\\#\\$\\{\\}\\textbackslash{}", PrescriptionTemplates.escape("A&B_C%#${'$'}{}\\"))
    }

    @Test fun validate_rejectsDuplicateBodyMarkers() {
        assertThrows(IllegalArgumentException::class.java) {
            PrescriptionTemplates.validate(PrescriptionTemplates.common.replace(PrescriptionTemplates.BODY,
                PrescriptionTemplates.BODY + PrescriptionTemplates.BODY))
        }
    }

    @Test fun updateMedicines_syncsDoseTimesDays_andPreservesOtherSourceEdits() {
        val empty = PrescriptionTemplates.create(null, ClinicalDraft(), "Example", "Doctor")
            .replace("Doctor signature:", "Custom footer. Doctor signature:")
        val medicine = MedicationDraft(name = "Example medicine", dose = "1 tablet",
            frequency = "morning, night", duration = "5 days")
        val added = PrescriptionTemplates.updateMedicines(empty, listOf(medicine))
        assertTrue(added.contains("Example medicine"))
        assertTrue(added.contains("Dose: 1 tablet"))
        assertTrue(added.contains("frequency: morning, night"))
        assertTrue(added.contains("Duration: 5 days"))
        assertTrue(added.contains("Custom footer."))
        val removed = PrescriptionTemplates.updateMedicines(added, emptyList())
        assertFalse(removed.contains("Example medicine"))
        assertTrue(removed.contains("Custom footer."))
    }

    @Test fun updateMedicines_rejectsMissingMarkers_insteadOfOverwritingSource() {
        assertThrows(IllegalArgumentException::class.java) {
            PrescriptionTemplates.updateMedicines("Doctor's custom document", emptyList())
        }
    }

}
