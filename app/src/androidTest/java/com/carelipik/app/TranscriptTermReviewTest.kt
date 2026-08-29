package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.carelipik.app.data.transcription.RuleBasedTranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.ui.screens.transcript.TranscriptScreen
import com.carelipik.app.ui.screens.transcript.TranscriptStatus
import com.carelipik.app.ui.screens.transcript.TranscriptUiState
import com.carelipik.app.ui.theme.CareLipikTheme
import org.junit.Rule
import org.junit.Test

class TranscriptTermReviewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun possibleRecognitionError_isHighlightedWithDoctorActions() {
        val transcript = "I have had cup for three days."
        val concerns = RuleBasedTranscriptReviewAnalyzer().analyze(
            transcript,
            TranscriptionLanguage.English
        )
        composeRule.setContent {
            CareLipikTheme {
                TranscriptScreen(
                    uiState = TranscriptUiState(
                        status = TranscriptStatus.Ready,
                        transcript = transcript,
                        concerns = concerns
                    ),
                    onTranscriptChanged = {},
                    onConfirmConcern = {},
                    onApplySuggestion = {},
                    onAnalyzeTermsOnline = {},
                    onViewModeChanged = {},
                    onSpeakerRoleAssigned = { _, _ -> },
                    onRetry = {},
                    onBack = {},
                    onContinue = {}
                )
            }
        }

        composeRule.onNodeWithTag("highlighted_transcript_preview")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Possible recognition error")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Use “cough”")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun structuredTranscript_showsLayoutAndSpeakerRoleControls() {
        composeRule.setContent {
            CareLipikTheme {
                TranscriptScreen(
                    uiState = TranscriptUiState(
                        status = TranscriptStatus.Ready,
                        transcript = "Speaker 1: Hello\n\nSpeaker 2: Good morning",
                        segments = listOf(
                            TranscriptSegment("speaker-1", "Hello"),
                            TranscriptSegment("speaker-2", "Good morning")
                        )
                    ),
                    onTranscriptChanged = {},
                    onConfirmConcern = {},
                    onApplySuggestion = {},
                    onAnalyzeTermsOnline = {},
                    onViewModeChanged = {},
                    onSpeakerRoleAssigned = { _, _ -> },
                    onRetry = {},
                    onBack = {},
                    onContinue = {}
                )
            }
        }

        composeRule.onNodeWithText("Transcript layout").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Conversation").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Confirm detected speakers")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun onlineTranscript_showsClinicalAnalysisControlAndSafetyWarning() {
        composeRule.setContent {
            CareLipikTheme {
                TranscriptScreen(
                    uiState = TranscriptUiState(
                        status = TranscriptStatus.Ready,
                        transcript = "Patient takes Dolo 650.",
                        engine = TranscriptionEngineOption.SaarasHindiHinglish,
                        clinicalAnalysisSource = "Gemini",
                        clinicalAnalysisWarning =
                            "Medicine salts and terminology codes require doctor verification."
                    ),
                    onTranscriptChanged = {},
                    onConfirmConcern = {},
                    onApplySuggestion = {},
                    onAnalyzeTermsOnline = {},
                    onViewModeChanged = {},
                    onSpeakerRoleAssigned = { _, _ -> },
                    onRetry = {},
                    onBack = {},
                    onContinue = {}
                )
            }
        }

        composeRule.onNodeWithText("Gemini term analysis").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Analyze again").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Medicine salts and terminology codes require doctor verification."
        ).performScrollTo().assertIsDisplayed()
    }
}
