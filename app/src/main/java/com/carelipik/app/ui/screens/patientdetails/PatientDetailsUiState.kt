package com.carelipik.app.ui.screens.patientdetails

data class PatientDetailsUiState(
    val patientName: String = "",
    val age: String = "",
    val visitReason: String = "",
    val hasAttemptedContinue: Boolean = false
) {
    val patientNameError: String?
        get() = when {
            !hasAttemptedContinue -> null
            patientName.isBlank() -> "Enter a patient name or reference"
            else -> null
        }

    val ageError: String?
        get() {
            if (age.isBlank()) return null
            val numericAge = age.toIntOrNull()
            return if (numericAge == null || numericAge !in 0..130) {
                "Enter an age from 0 to 130"
            } else {
                null
            }
        }

    val canContinue: Boolean
        get() = patientName.isNotBlank() && ageError == null
}
