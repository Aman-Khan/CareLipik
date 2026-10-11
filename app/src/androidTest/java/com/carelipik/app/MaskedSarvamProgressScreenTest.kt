package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionStage
import com.carelipik.app.ui.screens.transcript.TranscriptScreen
import com.carelipik.app.ui.screens.transcript.TranscriptStatus
import com.carelipik.app.ui.screens.transcript.TranscriptUiState
import com.carelipik.app.ui.theme.CareLipikTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class MaskedSarvamProgressScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun maskedSarvam_showsActualProcessingStage() {
        val state = MutableStateFlow(TranscriptUiState(status = TranscriptStatus.Processing,
            engine = TranscriptionEngineOption.NameMaskedSaaras,
            transcriptionStage = TranscriptionStage.PreprocessingAudio))
        composeRule.setContent {
            val current by state.collectAsState()
            CareLipikTheme {
                TranscriptScreen(uiState = current, onSelectConcern = {}, onUpdateConcern = { _, _ -> },
                    onTrainingDataConsentChanged = {}, onTranscriptChanged = {}, onConfirmConcern = {},
                    onApplySuggestion = {}, onOnlineAnalysisConsentChanged = {}, onAnalyzeTermsOnline = {},
                    onViewModeChanged = {}, onSpeakerRoleAssigned = { _, _ -> }, onRetry = {}, onBack = {}, onContinue = {})
            }
        }
        composeRule.onNodeWithText("Step 1 of 3: Preprocessing audio", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Sending masked audio", substring = true).assertDoesNotExist()
        composeRule.runOnIdle { state.value = state.value.copy(transcriptionStage = TranscriptionStage.SarvamTranscription) }
        composeRule.onNodeWithText("Step 2 of 3: Sending masked audio", substring = true).assertIsDisplayed()
        composeRule.runOnIdle { state.value = state.value.copy(transcriptionStage = TranscriptionStage.RedactingPatientName) }
        composeRule.onNodeWithText("Step 3 of 3: Finalizing", substring = true).assertIsDisplayed()
        composeRule.runOnIdle { state.value = state.value.copy(engine = TranscriptionEngineOption.SaarasHindiHinglish) }
        composeRule.onNodeWithText("Step 3 of 3", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Securely uploading and separating speakers", substring = true).assertIsDisplayed()
    }
}
