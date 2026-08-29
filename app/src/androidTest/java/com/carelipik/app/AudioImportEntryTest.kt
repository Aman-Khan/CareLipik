package com.carelipik.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.carelipik.app.domain.model.RecordedAudioSource
import com.carelipik.app.ui.screens.recording.RecordingScreen
import com.carelipik.app.ui.screens.recording.RecordingStatus
import com.carelipik.app.ui.screens.recording.RecordingUiState
import com.carelipik.app.ui.theme.CareLipikTheme
import org.junit.Rule
import org.junit.Test

class AudioImportEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyScreen_offersRecordingAndAudioImport() {
        render(RecordingUiState())

        composeRule.onNodeWithText("Start recording").assertIsDisplayed()
        composeRule.onNodeWithText("Import audio").assertIsDisplayed()
    }

    @Test
    fun importedAudio_showsTemporaryFileDetailsAndReplacementActions() {
        render(
            RecordingUiState(
                status = RecordingStatus.Completed,
                elapsedSeconds = 65,
                hasSavedAudio = true,
                audioSource = RecordedAudioSource.Imported,
                audioDisplayName = "synthetic-hinglish.wav",
                audioSizeBytes = 512_000
            )
        )

        composeRule.onNodeWithText("synthetic-hinglish.wav")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Replace").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Remove imported audio")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun render(state: RecordingUiState) {
        composeRule.setContent {
            CareLipikTheme {
                RecordingScreen(
                    uiState = state,
                    onStart = {},
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onDiscard = {},
                    onImportAudio = {},
                    onTogglePlayback = {},
                    onTranscriptionLanguageChanged = {},
                    onTranscriptionEngineChanged = {},
                    onOnlineProcessingConsentChanged = {},
                    onBack = {},
                    onContinue = {}
                )
            }
        }
    }
}
