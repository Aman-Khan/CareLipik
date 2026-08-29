package com.carelipik.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carelipik.app.domain.model.ConsultationDestination
import com.carelipik.app.ui.screens.clinicaldraft.ClinicalDraftScreen
import com.carelipik.app.ui.screens.clinicaldraft.ClinicalDraftViewModel
import com.carelipik.app.ui.screens.doctorreview.DoctorReviewScreen
import com.carelipik.app.ui.screens.doctorreview.DoctorReviewViewModel
import com.carelipik.app.ui.screens.doctorprofile.DoctorProfileScreen
import com.carelipik.app.ui.screens.doctorprofile.DoctorProfileViewModel
import com.carelipik.app.ui.screens.home.HomeScreen
import com.carelipik.app.ui.screens.home.HomeViewModel
import com.carelipik.app.ui.screens.patientdetails.PatientDetailsScreen
import com.carelipik.app.ui.screens.patientdetails.PatientDetailsViewModel
import com.carelipik.app.ui.screens.placeholder.PlaceholderScreen
import com.carelipik.app.ui.screens.placeholder.UpcomingFeatureScreen
import com.carelipik.app.ui.screens.recording.RecordingScreen
import com.carelipik.app.ui.screens.recording.RecordingViewModel
import com.carelipik.app.ui.screens.transcript.TranscriptScreen
import com.carelipik.app.ui.screens.transcript.TranscriptViewModel
import com.carelipik.app.ui.screens.welcome.WelcomeScreen
import com.carelipik.app.ui.screens.welcome.WelcomeViewModel

@Composable
fun CareLipikApp(
    homeViewModel: HomeViewModel = viewModel(),
    doctorProfileViewModel: DoctorProfileViewModel = viewModel(),
    welcomeViewModel: WelcomeViewModel = viewModel(),
    patientDetailsViewModel: PatientDetailsViewModel = viewModel(),
    recordingViewModel: RecordingViewModel? = null,
    transcriptViewModel: TranscriptViewModel? = null,
    clinicalDraftViewModel: ClinicalDraftViewModel = viewModel(),
    doctorReviewViewModel: DoctorReviewViewModel = viewModel()
) {
    val context = LocalContext.current
    val activeRecordingViewModel = recordingViewModel ?: viewModel(
        factory = RecordingViewModel.Factory(context)
    )
    val activeTranscriptViewModel = transcriptViewModel ?: viewModel(
        factory = TranscriptViewModel.Factory(context)
    )
    val homeUiState by homeViewModel.uiState.collectAsState()
    val doctorProfileUiState by doctorProfileViewModel.uiState.collectAsState()
    val welcomeUiState by welcomeViewModel.uiState.collectAsState()
    val patientDetailsUiState by patientDetailsViewModel.uiState.collectAsState()
    val recordingUiState by activeRecordingViewModel.uiState.collectAsState()
    val transcriptUiState by activeTranscriptViewModel.uiState.collectAsState()
    val clinicalDraftUiState by clinicalDraftViewModel.uiState.collectAsState()
    val doctorReviewUiState by doctorReviewViewModel.uiState.collectAsState()
    val navigator = remember { CareLipikNavigator() }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (navigator.currentDestination) {
            ConsultationDestination.Home -> HomeScreen(
                uiState = homeUiState,
                onStartConsultation = navigator::startConsultation,
                onOpenProfile = navigator::openDoctorProfile,
                onOpenHistory = navigator::openConsultationHistory,
                modifier = Modifier.padding(innerPadding)
            )
            ConsultationDestination.DoctorProfile -> DoctorProfileScreen(
                uiState = doctorProfileUiState,
                onFullNameChanged = doctorProfileViewModel::setFullName,
                onSpecialtyChanged = doctorProfileViewModel::setSpecialty,
                onRegistrationNumberChanged = doctorProfileViewModel::setRegistrationNumber,
                onClinicNameChanged = doctorProfileViewModel::setClinicName,
                onPreferredLanguageChanged = doctorProfileViewModel::togglePreferredLanguage,
                onProcessingPreferenceChanged = doctorProfileViewModel::setProcessingPreference,
                onBack = navigator::navigateBack,
                onSave = {
                    doctorProfileViewModel.saveProfile()?.let { profile ->
                        homeViewModel.applyDoctorProfile(profile)
                        navigator.navigateBack()
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            ConsultationDestination.ConsultationHistory -> UpcomingFeatureScreen(
                destination = navigator.currentDestination,
                onBack = navigator::navigateBack,
                modifier = Modifier.padding(innerPadding)
            )
            ConsultationDestination.Welcome -> WelcomeScreen(
                uiState = welcomeUiState,
                onConsentChanged = welcomeViewModel::setRecordingConsent,
                onBack = navigator::navigateBack,
                onContinue = navigator::navigateToNext,
                modifier = Modifier.padding(innerPadding)
            )
            ConsultationDestination.Transcript -> TranscriptScreen(
                uiState = transcriptUiState,
                onTranscriptChanged = activeTranscriptViewModel::setTranscript,
                onRetry = activeTranscriptViewModel::retry,
                onBack = navigator::navigateBack,
                onContinue = {
                    if (activeTranscriptViewModel.validateForContinue()) {
                        clinicalDraftViewModel.generate(activeTranscriptViewModel.transcriptText())
                        navigator.navigateToNext()
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            ConsultationDestination.ClinicalDraft -> ClinicalDraftScreen(
                uiState = clinicalDraftUiState,
                onPresentingComplaintChanged = clinicalDraftViewModel::setPresentingComplaint,
                onHistoryChanged = clinicalDraftViewModel::setHistory,
                onKeyFindingsChanged = clinicalDraftViewModel::setKeyFindings,
                onAssessmentNotesChanged = clinicalDraftViewModel::setAssessmentNotes,
                onPlanNotesChanged = clinicalDraftViewModel::setPlanNotes,
                onRetry = clinicalDraftViewModel::retry,
                onBack = navigator::navigateBack,
                onContinue = {
                    if (clinicalDraftViewModel.validateForContinue()) {
                        doctorReviewViewModel.loadDraft(clinicalDraftViewModel.currentDraft())
                        navigator.navigateToNext()
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            ConsultationDestination.DoctorReview -> DoctorReviewScreen(
                uiState = doctorReviewUiState,
                onConfirmationChanged = doctorReviewViewModel::setConfirmedReview,
                onBack = navigator::navigateBack,
                onApprove = {
                    if (doctorReviewViewModel.validateApproval()) {
                        navigator.navigateToNext()
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            ConsultationDestination.PatientDetails -> PatientDetailsScreen(
                uiState = patientDetailsUiState,
                onPatientNameChanged = patientDetailsViewModel::setPatientName,
                onAgeChanged = patientDetailsViewModel::setAge,
                onVisitReasonChanged = patientDetailsViewModel::setVisitReason,
                onBack = navigator::navigateBack,
                onContinue = {
                    if (patientDetailsViewModel.validateForContinue()) {
                        navigator.navigateToNext()
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            ConsultationDestination.ConsultationRecording -> RecordingScreen(
                uiState = recordingUiState,
                onStart = activeRecordingViewModel::startRecording,
                onPause = activeRecordingViewModel::pauseRecording,
                onResume = activeRecordingViewModel::resumeRecording,
                onStop = activeRecordingViewModel::stopRecording,
                onDiscard = activeRecordingViewModel::discardRecording,
                onImportAudio = activeRecordingViewModel::importAudio,
                onTogglePlayback = activeRecordingViewModel::togglePlayback,
                onTranscriptionLanguageChanged = activeRecordingViewModel::setTranscriptionLanguage,
                onTranscriptionEngineChanged = activeRecordingViewModel::setTranscriptionEngine,
                onOnlineProcessingConsentChanged =
                    activeRecordingViewModel::setOnlineProcessingConsent,
                onBack = {
                    activeRecordingViewModel.stopPlayback()
                    navigator.navigateBack()
                },
                onContinue = {
                    activeRecordingViewModel.recordedAudioPath()?.let { audioPath ->
                        activeRecordingViewModel.stopPlayback()
                        activeTranscriptViewModel.transcribe(
                            audioPath,
                            activeRecordingViewModel.transcriptionLanguage(),
                            activeRecordingViewModel.transcriptionEngine()
                        )
                        navigator.navigateToNext()
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            else -> PlaceholderScreen(
                destination = navigator.currentDestination,
                onNext = navigator::navigateToNext,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
