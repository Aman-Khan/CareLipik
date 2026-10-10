package com.carelipik.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.carelipik.app.domain.model.ConsultationDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity-scoped navigation state that survives configuration changes with the consultation. */
class CareLipikNavigator : ViewModel() {
    private val _destination = MutableStateFlow(ConsultationDestination.Home)
    val destination: StateFlow<ConsultationDestination> = _destination.asStateFlow()
    var currentDestination: ConsultationDestination
        get() = _destination.value
        private set(value) {
            _destination.value = value
        }
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

    fun openSavedRecordings() {
        currentDestination = ConsultationDestination.SavedRecordings
    }

    fun resumeRecording() {
        currentDestination = ConsultationDestination.ConsultationRecording
    }

    fun returnHome() {
        currentDestination = ConsultationDestination.Home
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
            ConsultationDestination.SavedRecordings,
            ConsultationDestination.ConsultationHistory -> ConsultationDestination.Home
            ConsultationDestination.Welcome -> ConsultationDestination.PatientDetails
            ConsultationDestination.PatientDetails -> ConsultationDestination.ConsultationRecording
            ConsultationDestination.ConsultationRecording -> ConsultationDestination.Transcript
            ConsultationDestination.Transcript -> ConsultationDestination.ClinicalDraft
            ConsultationDestination.ClinicalDraft -> ConsultationDestination.Prescription
            ConsultationDestination.Prescription -> ConsultationDestination.DoctorReview
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
            ConsultationDestination.SavedRecordings,
            ConsultationDestination.ConsultationHistory,
            ConsultationDestination.Welcome -> ConsultationDestination.Home
            ConsultationDestination.PatientDetails -> ConsultationDestination.Welcome
            ConsultationDestination.ConsultationRecording -> ConsultationDestination.PatientDetails
            ConsultationDestination.Transcript -> ConsultationDestination.ConsultationRecording
            ConsultationDestination.ClinicalDraft -> ConsultationDestination.Transcript
            ConsultationDestination.Prescription -> ConsultationDestination.ClinicalDraft
            ConsultationDestination.DoctorReview -> ConsultationDestination.Prescription
            ConsultationDestination.Export -> exportReturnDestination
        }
    }
}
