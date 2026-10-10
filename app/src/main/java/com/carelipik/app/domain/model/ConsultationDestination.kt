package com.carelipik.app.domain.model

/** Top-level destinations and routes for the local consultation workflow. */
enum class ConsultationDestination(val title: String) {
    Home("Home"),
    DoctorProfile("Doctor profile"),
    ConsultationHistory("Consultation history"),
    SavedRecordings("Saved recordings"),
    Welcome("Recording consent"),
    PatientDetails("Patient details"),
    ConsultationRecording("Consultation recording"),
    Transcript("Transcript"),
    ClinicalDraft("Clinical draft"),
    Prescription("Report and prescription"),
    DoctorReview("Doctor review"),
    Export("Export")
}
