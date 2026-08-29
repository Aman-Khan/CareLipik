package com.carelipik.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.carelipik.app.domain.model.ConsultationDestination

/** Route holder that keeps top-level app navigation separate from the consultation flow. */
class CareLipikNavigator {
    var currentDestination by mutableStateOf(ConsultationDestination.Home)
        private set
    private var exportReturnDestination = ConsultationDestination.DoctorReview

    fun startConsultation() {
        currentDestination = ConsultationDestination.Welcome
    }

    fun openDoctorProfile() {
        currentDestination = ConsultationDestination.DoctorProfile
    }

    fun openConsultationHistory() {
        currentDestination = ConsultationDestination.ConsultationHistory
    }

    fun openExportFromHistory() {
        exportReturnDestination = ConsultationDestination.ConsultationHistory
        currentDestination = ConsultationDestination.Export
    }

    fun finishExport() {
        currentDestination = ConsultationDestination.Home
    }

    fun navigateToNext() {
        currentDestination = when (currentDestination) {
            ConsultationDestination.Home -> ConsultationDestination.Welcome
            ConsultationDestination.DoctorProfile,
            ConsultationDestination.ConsultationHistory -> ConsultationDestination.Home
            ConsultationDestination.Welcome -> ConsultationDestination.PatientDetails
            ConsultationDestination.PatientDetails -> ConsultationDestination.ConsultationRecording
            ConsultationDestination.ConsultationRecording -> ConsultationDestination.Transcript
            ConsultationDestination.Transcript -> ConsultationDestination.ClinicalDraft
            ConsultationDestination.ClinicalDraft -> ConsultationDestination.DoctorReview
            ConsultationDestination.DoctorReview -> {
                exportReturnDestination = ConsultationDestination.DoctorReview
                ConsultationDestination.Export
            }
            ConsultationDestination.Export -> ConsultationDestination.Home
        }
    }

    fun navigateBack() {
        currentDestination = when (currentDestination) {
            ConsultationDestination.Home -> ConsultationDestination.Home
            ConsultationDestination.DoctorProfile,
            ConsultationDestination.ConsultationHistory,
            ConsultationDestination.Welcome -> ConsultationDestination.Home
            ConsultationDestination.PatientDetails -> ConsultationDestination.Welcome
            ConsultationDestination.ConsultationRecording -> ConsultationDestination.PatientDetails
            ConsultationDestination.Transcript -> ConsultationDestination.ConsultationRecording
            ConsultationDestination.ClinicalDraft -> ConsultationDestination.Transcript
            ConsultationDestination.DoctorReview -> ConsultationDestination.ClinicalDraft
            ConsultationDestination.Export -> exportReturnDestination
        }
    }
}
