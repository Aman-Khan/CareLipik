package com.carelipik.app.domain.model

import com.carelipik.app.domain.transcription.TranscriptionLanguage

enum class ProcessingPreference(
    val displayName: String,
    val shortDescription: String,
    val homeDetail: String
) {
    PrivateOnDevice(
        displayName = "Private · On-device",
        shortDescription = "Keep recordings and transcription on this device.",
        homeDetail = "On-device processing only"
    ),
    SmartHybrid(
        displayName = "Smart · Hybrid",
        shortDescription = "Prefer on-device processing and ask before using online enhancement.",
        homeDetail = "On-device first · asks before online processing"
    ),
    EnhancedOnline(
        displayName = "Enhanced · Online",
        shortDescription = "Prefer online speaker separation when consent and internet are available.",
        homeDetail = "Online enhancement preferred · consent still required"
    )
}

data class DoctorProfile(
    val fullName: String,
    val specialty: String,
    val registrationNumber: String,
    val clinicName: String,
    val preferredLanguages: Set<TranscriptionLanguage>,
    val processingPreference: ProcessingPreference
)
