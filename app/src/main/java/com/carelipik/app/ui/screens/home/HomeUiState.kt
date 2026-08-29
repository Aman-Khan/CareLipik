package com.carelipik.app.ui.screens.home

data class ConsultationSummaryUi(
    val id: String,
    val patientLabel: String,
    val dateLabel: String,
    val visitReason: String,
    val noteFormatLabel: String,
    val statusLabel: String = "Approved"
)

data class HomeUiState(
    val doctorName: String = "",
    val specialty: String = "",
    val processingLabel: String = "Smart processing",
    val processingDetail: String = "On-device first · asks before online processing",
    val recentConsultations: List<ConsultationSummaryUi> = emptyList()
) {
    val greetingTitle: String
        get() = doctorName.trim().takeIf { it.isNotEmpty() }?.let { "Welcome, Dr $it" }
            ?: "Welcome to CareLipik"

    val profileTitle: String
        get() = doctorName.trim().takeIf { it.isNotEmpty() }?.let { "Dr $it" }
            ?: "Set up doctor profile"

    val profileSupportingText: String
        get() = specialty.trim().takeIf { it.isNotEmpty() }
            ?: "Add your specialty, clinic and preferences"
}
