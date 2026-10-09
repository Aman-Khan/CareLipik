package com.carelipik.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.carelipik.app.domain.model.RecordedAudioSource
import com.carelipik.app.domain.model.SavedRecording
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.ui.screens.savedrecordings.SavedRecordingsScreen
import com.carelipik.app.ui.screens.savedrecordings.SavedRecordingsUiState
import com.carelipik.app.ui.theme.CareLipikTheme
import com.carelipik.app.ui.screens.recording.RecordingScreen
import com.carelipik.app.ui.screens.recording.RecordingStatus
import com.carelipik.app.ui.screens.recording.RecordingUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SavedRecordingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedRecording_canBeSavedWithoutOnlineProcessingConsent() {
        var saved = false
        composeRule.setContent {
            CareLipikTheme {
                RecordingScreen(
                    uiState = RecordingUiState(
                        status = RecordingStatus.Completed, hasSavedAudio = true,
                        transcriptionEngine = TranscriptionEngineOption.SaarasHindiHinglish,
                        hasOnlineProcessingConsent = false
                    ),
                    onStart = {}, onPause = {}, onResume = {}, onStop = {}, onDiscard = {},
                    onImportAudio = {}, onTogglePlayback = {}, onTranscriptionLanguageChanged = {},
                    onTranscriptionEngineChanged = {}, onOnlineProcessingConsentChanged = {},
                    onBack = {}, onContinue = {}, onSaveForLater = { saved = true }
                )
            }
        }
        composeRule.onNodeWithText("Save and continue later").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(true, saved) }
    }

    @Test
    fun savedRecording_offersResumeAndRequiresConfirmationBeforeDelete() {
        val recording = SavedRecording(
            "00000000-0000-0000-0000-000000000001", 1_000L, "Synthetic patient", "30",
            "Synthetic visit", TranscriptionLanguage.English, TranscriptionEngineOption.MedAsrEnglish,
            "Synthetic WAV", 60_000L, RecordedAudioSource.Microphone, true
        )
        var resumed: String? = null
        var deleted: String? = null
        composeRule.setContent {
            CareLipikTheme {
                SavedRecordingsScreen(
                    SavedRecordingsUiState(listOf(recording)),
                    onResume = { resumed = it }, onDelete = { deleted = it },
                    onRefresh = {}, onBack = {}
                )
            }
        }
        composeRule.onNodeWithText("Synthetic patient").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Continue consultation").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(recording.id, resumed) }
        composeRule.onNodeWithText("Delete").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete saved recording?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, deleted) }
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(null, deleted) }
    }
}
