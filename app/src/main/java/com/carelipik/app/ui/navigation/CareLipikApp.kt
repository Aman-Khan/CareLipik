package com.carelipik.app.ui.navigation

import android.content.Intent
import java.io.File
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carelipik.app.domain.model.ConsultationDestination
import com.carelipik.app.ui.screens.clinicaldraft.ClinicalDraftScreen
import com.carelipik.app.ui.screens.clinicaldraft.ClinicalDraftViewModel
import com.carelipik.app.ui.screens.doctorreview.DoctorReviewScreen
import com.carelipik.app.ui.screens.doctorreview.DoctorReviewViewModel
import com.carelipik.app.ui.screens.doctorprofile.DoctorProfileScreen
import com.carelipik.app.ui.screens.doctorprofile.DoctorProfileViewModel
import com.carelipik.app.ui.screens.doctorprofile.DoctorVoiceEnrollmentViewModel
import com.carelipik.app.ui.screens.home.HomeScreen
import com.carelipik.app.ui.screens.home.HomeViewModel
import com.carelipik.app.ui.screens.export.ConsultationExportScreen
import com.carelipik.app.ui.screens.export.ConsultationExportViewModel
import com.carelipik.app.ui.screens.history.ConsultationHistoryScreen
import com.carelipik.app.ui.screens.history.ConsultationHistoryViewModel
import com.carelipik.app.ui.screens.patientdetails.PatientDetailsScreen
import com.carelipik.app.ui.screens.patientdetails.PatientDetailsViewModel
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
    doctorVoiceEnrollmentViewModel: DoctorVoiceEnrollmentViewModel? = null,
    welcomeViewModel: WelcomeViewModel = viewModel(),
    patientDetailsViewModel: PatientDetailsViewModel = viewModel(),
    recordingViewModel: RecordingViewModel? = null,
    transcriptViewModel: TranscriptViewModel? = null,
    clinicalDraftViewModel: ClinicalDraftViewModel = viewModel(),
    doctorReviewViewModel: DoctorReviewViewModel? = null,
    consultationHistoryViewModel: ConsultationHistoryViewModel? = null,
    consultationExportViewModel: ConsultationExportViewModel? = null
) {
    val context = LocalContext.current
    val activeRecordingViewModel = recordingViewModel ?: viewModel(
        factory = RecordingViewModel.Factory(context)
    )
    val activeTranscriptViewModel = transcriptViewModel ?: viewModel(
        factory = TranscriptViewModel.Factory(context)
    )
    val activeDoctorVoiceEnrollmentViewModel = doctorVoiceEnrollmentViewModel ?: viewModel(
        factory = DoctorVoiceEnrollmentViewModel.Factory(context)
    )
    val activeDoctorReviewViewModel = doctorReviewViewModel ?: viewModel(
        factory = DoctorReviewViewModel.Factory(context)
    )
    val activeConsultationHistoryViewModel = consultationHistoryViewModel ?: viewModel(
        factory = ConsultationHistoryViewModel.Factory(context)
    )
    val activeConsultationExportViewModel = consultationExportViewModel ?: viewModel(
        factory = ConsultationExportViewModel.Factory(context)
    )
    val homeUiState by homeViewModel.uiState.collectAsState()
    val doctorProfileUiState by doctorProfileViewModel.uiState.collectAsState()
    val doctorVoiceEnrollmentUiState by
        activeDoctorVoiceEnrollmentViewModel.uiState.collectAsState()
    val welcomeUiState by welcomeViewModel.uiState.collectAsState()
    val patientDetailsUiState by patientDetailsViewModel.uiState.collectAsState()
    val recordingUiState by activeRecordingViewModel.uiState.collectAsState()
    val transcriptUiState by activeTranscriptViewModel.uiState.collectAsState()
    val clinicalDraftUiState by clinicalDraftViewModel.uiState.collectAsState()
    val doctorReviewUiState by activeDoctorReviewViewModel.uiState.collectAsState()
    val consultationHistoryUiState by activeConsultationHistoryViewModel.uiState.collectAsState()
    val consultationExportUiState by activeConsultationExportViewModel.uiState.collectAsState()
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
                voiceUiState = doctorVoiceEnrollmentUiState,
                onFullNameChanged = doctorProfileViewModel::setFullName,
                onSpecialtyChanged = doctorProfileViewModel::setSpecialty,
                onRegistrationNumberChanged = doctorProfileViewModel::setRegistrationNumber,
                onClinicNameChanged = doctorProfileViewModel::setClinicName,
                onPreferredLanguageChanged = doctorProfileViewModel::togglePreferredLanguage,
                onProcessingPreferenceChanged = doctorProfileViewModel::setProcessingPreference,
                onStartVoiceSample = activeDoctorVoiceEnrollmentViewModel::startRecording,
                onStopVoiceSample = activeDoctorVoiceEnrollmentViewModel::stopAndSave,
                onDeleteVoiceSample = activeDoctorVoiceEnrollmentViewModel::deleteSample,
                onBack = navigator::navigateBack,
                onSave = {
                    doctorProfileViewModel.saveProfile()?.let { profile ->
                        homeViewModel.applyDoctorProfile(profile)
                        navigator.navigateBack()
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            ConsultationDestination.ConsultationHistory -> ConsultationHistoryScreen(
                uiState = consultationHistoryUiState,
                onOpen = activeConsultationHistoryViewModel::select,
                onExport = { consultation ->
                    activeConsultationExportViewModel.load(consultation)
                    navigator.openExportFromHistory()
                },
                onDelete = activeConsultationHistoryViewModel::delete,
                onBack = {
                    if (consultationHistoryUiState.selected != null) {
                        activeConsultationHistoryViewModel.closeDetail()
                    } else {
                        navigator.navigateBack()
                    }
                },
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
                onConfirmConcern = activeTranscriptViewModel::confirmConcern,
                onApplySuggestion = activeTranscriptViewModel::applySuggestedReplacement,
                onViewModeChanged = activeTranscriptViewModel::setViewMode,
                onSpeakerRoleAssigned = activeTranscriptViewModel::assignSpeakerRole,
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
                        activeDoctorReviewViewModel.loadDraft(clinicalDraftViewModel.currentDraft())
                        navigator.navigateToNext()
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            ConsultationDestination.DoctorReview -> DoctorReviewScreen(
                uiState = doctorReviewUiState,
                onConfirmationChanged = activeDoctorReviewViewModel::setConfirmedReview,
                onBack = navigator::navigateBack,
                onApprove = {
                    activeDoctorReviewViewModel.approve(
                        patient = patientDetailsViewModel.currentDetails(),
                        onSaved = { consultation ->
                            activeRecordingViewModel.discardRecording()
                            activeConsultationHistoryViewModel.refresh()
                            activeConsultationExportViewModel.load(consultation)
                            navigator.navigateToNext()
                        }
                    )
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
            ConsultationDestination.Export -> ConsultationExportScreen(
                uiState = consultationExportUiState,
                onGenerate = activeConsultationExportViewModel::generate,
                onShare = {
                    consultationExportUiState.pdf?.let { pdf ->
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            File(pdf.localPath)
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share clinical note"))
                    }
                },
                onBack = navigator::navigateBack,
                onFinish = navigator::finishExport,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
