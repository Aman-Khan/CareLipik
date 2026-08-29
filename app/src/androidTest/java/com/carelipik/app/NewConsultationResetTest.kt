package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.carelipik.app.data.audio.FakeConsultationRecorder
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.ui.navigation.CareLipikApp
import com.carelipik.app.ui.screens.patientdetails.PatientDetailsViewModel
import com.carelipik.app.ui.screens.recording.RecordingUiState
import com.carelipik.app.ui.screens.recording.RecordingViewModel
import com.carelipik.app.ui.screens.welcome.WelcomeViewModel
import com.carelipik.app.ui.theme.CareLipikTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class NewConsultationResetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun startNewConsultation_clearsPriorPatientConsentAndRecordingSelections() {
        val patientViewModel = PatientDetailsViewModel().apply {
            setPatientName("Previous synthetic reference")
            setAge("47")
            setVisitReason("Previous synthetic visit")
        }
        val welcomeViewModel = WelcomeViewModel().apply {
            setRecordingConsent(true)
        }
        val recordingViewModel = RecordingViewModel(
            recorder = FakeConsultationRecorder(),
            useAutomaticTimer = false
        ).apply {
            setTranscriptionLanguage(TranscriptionLanguage.Hinglish)
            setOnlineProcessingConsent(true)
        }
        composeRule.setContent {
            CareLipikTheme {
                CareLipikApp(
                    welcomeViewModel = welcomeViewModel,
                    patientDetailsViewModel = patientViewModel,
                    recordingViewModel = recordingViewModel
                )
            }
        }

        composeRule.onNodeWithTag("home_empty_start_consultation").performClick()

        composeRule.runOnIdle {
            assertEquals("", patientViewModel.uiState.value.patientName)
            assertEquals("", patientViewModel.uiState.value.age)
            assertEquals("", patientViewModel.uiState.value.visitReason)
            assertFalse(welcomeViewModel.uiState.value.hasRecordingConsent)
            assertEquals(RecordingUiState(), recordingViewModel.uiState.value)
        }
    }
}
