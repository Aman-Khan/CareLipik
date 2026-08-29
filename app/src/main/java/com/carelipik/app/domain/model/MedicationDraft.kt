package com.carelipik.app.domain.model

/** A medicine explicitly reviewed by the doctor; blank fields mean not documented. */
data class MedicationDraft(
    val name: String = "",
    val genericName: String = "",
    val strength: String = "",
    val dose: String = "",
    val route: String = "",
    val frequency: String = "",
    val duration: String = "",
    val instructions: String = "",
    val sourceEvidence: String = "",
    val isDoctorReviewed: Boolean = false
) {
    val hasContent: Boolean
        get() = name.isNotBlank()
}
