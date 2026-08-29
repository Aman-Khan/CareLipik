package com.carelipik.app.domain.model

/** Routes for the local consultation workflow. */
enum class ConsultationDestination(val title: String) {
    Welcome("Welcome"),
    PatientDetails("Patient details"),
    ConsultationRecording("Consultation recording"),
    Transcript("Transcript"),
    ClinicalDraft("Clinical draft"),
    DoctorReview("Doctor review"),
    Export("Export")
}
