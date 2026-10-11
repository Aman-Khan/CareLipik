package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.carelipik.app.domain.transcription.SpeakerRole
import com.carelipik.app.ui.screens.transcript.TranscriptScreen
import com.carelipik.app.ui.screens.transcript.TranscriptViewMode
import com.carelipik.app.ui.screens.transcript.TranscriptViewModel
import com.carelipik.app.ui.theme.CareLipikTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TranscriptPeopleReviewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fourthPerson_canBeRenamedAndAppearsInConversation() {
        val model = TranscriptViewModel(processAsynchronously = false).apply {
            setTranscript((1..4).joinToString("\n") { "Speaker $it: Synthetic greeting $it" })
            assignSpeakerRole("speaker-1", SpeakerRole.Doctor)
            assignSpeakerRole("speaker-2", SpeakerRole.Patient)
            assignSpeakerRole("speaker-3", SpeakerRole.Other)
            assignSpeakerRole("speaker-4", SpeakerRole.Other)
        }
        composeRule.setContent {
            val state by model.uiState.collectAsState()
            CareLipikTheme {
                TranscriptScreen(
                    uiState = state,
                    onTranscriptChanged = model::setDisplayedTranscript,
                    onConfirmConcern = model::confirmConcern,
                    onApplySuggestion = model::applySuggestedReplacement,
                    onOnlineAnalysisConsentChanged = model::setOnlineAnalysisConsent,
                    onAnalyzeTermsOnline = model::analyzeTermsOnline,
                    onViewModeChanged = model::setViewMode,
                    onSpeakerRoleAssigned = model::assignSpeakerRole,
                    onSpeakerNameChanged = model::setSpeakerName,
                    onRetry = {},
                    onBack = {},
                    onContinue = {}
                )
            }
        }

        composeRule.onNodeWithTag("speaker_name_speaker-4")
            .performScrollTo().performTextInput("Synthetic interpreter")
        composeRule.onNodeWithTag("full_transcript_editor").performScrollTo()
            .assertTextContains("Doctor: Synthetic greeting 1", substring = true)
            .assertTextContains("Patient: Synthetic greeting 2", substring = true)
            .assertTextContains("Synthetic interpreter (Other): Synthetic greeting 4", substring = true)
        composeRule.runOnIdle {
            assertEquals("Synthetic interpreter", model.uiState.value.speakerNames["speaker-4"])
            model.setViewMode(TranscriptViewMode.Conversation)
        }
        composeRule.onNodeWithTag("conversation_label_speaker-4")
            .performScrollTo().assertTextEquals("Synthetic interpreter (Other)")
        composeRule.onNodeWithText("Synthetic greeting 4").performScrollTo().assertExists()
    }
}
