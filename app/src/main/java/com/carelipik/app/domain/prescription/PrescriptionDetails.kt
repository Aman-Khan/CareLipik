package com.carelipik.app.domain.prescription

enum class PrescriptionDetail(val label: String) {
    Symptoms("Symptoms / complaints"),
    Precautions("Precautions"),
    Allergies("Allergies"),
    Investigations("Investigations / tests"),
    FollowUp("Follow-up"),
    Notes("Additional instructions")
}
