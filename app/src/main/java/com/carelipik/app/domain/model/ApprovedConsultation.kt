package com.carelipik.app.domain.model

data class ApprovedConsultation(
    val id: String,
    val approvedAtMillis: Long,
    val patientName: String,
    val patientAge: String,
    val visitReason: String,
    val draft: ClinicalDraft
)
