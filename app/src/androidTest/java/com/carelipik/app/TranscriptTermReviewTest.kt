package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.carelipik.app.ui.screens.transcript.TranscriptViewModel
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    fun enhancement_showsLoaderUntilAnalyzerCompletes() {
        val completion = CountDownLatch(1)
        val model = TranscriptViewModel(onlineReviewAnalyzer = OnlineTranscriptReviewAnalyzer { _, _ ->
            completion.await(15, TimeUnit.SECONDS)
            OnlineTranscriptReviewResult.Success(emptyList(), "Synthetic provider", false)
        }).apply {
            setTranscript("Synthetic consultation text")
            setOnlineAnalysisConsent(true)
        }
        composeRule.setContent {
            val state by model.uiState.collectAsState()
            CareLipikTheme {
                TranscriptScreen(
                    uiState = state, onTranscriptChanged = model::setDisplayedTranscript,
                    onConfirmConcern = model::confirmConcern, onApplySuggestion = model::applySuggestedReplacement,
                    onOnlineAnalysisConsentChanged = model::setOnlineAnalysisConsent,
                    onAnalyzeTermsOnline = model::analyzeTermsOnline, onViewModeChanged = model::setViewMode,
                    onSpeakerRoleAssigned = model::assignSpeakerRole, onRetry = {}, onBack = {}, onContinue = {}
                )
            }
        }
        try {
            composeRule.onNodeWithText("Enhance terms with Gemini").performScrollTo().performClick()
            composeRule.onNodeWithTag("medical_term_loader").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("full_transcript_editor").performScrollTo().assertIsNotEnabled()
            completion.countDown()
            composeRule.waitUntil(5_000) { !model.uiState.value.isAnalyzingTerms }
            composeRule.onNodeWithTag("medical_term_loader").assertDoesNotExist()
        } finally {
            completion.countDown()
        }
    }

    @Test
    fun offlineTranscript_showsConsentGatedGeminiEnhancement() {
        composeRule.setContent {
            CareLipikTheme {
                TranscriptScreen(
                    uiState = TranscriptUiState(
                        status = TranscriptStatus.Ready,
                        transcript = "Patient takes paracetamol.",
                        engine = TranscriptionEngineOption.WhisperMultilingual
                    ),
                    onTranscriptChanged = {},
                    onConfirmConcern = {},
                    onApplySuggestion = {},
                    onSelectConcern = {},
                    onUpdateConcern = { _, _ -> },
                    onTrainingDataConsentChanged = {},
                    onOnlineAnalysisConsentChanged = {},
                    onAnalyzeTermsOnline = {},
                    onViewModeChanged = {},
                    onSpeakerRoleAssigned = { _, _ -> },
                    onRetry = {},
                    onBack = {},
                    onContinue = {}
                )
            }
        }

        composeRule.onNodeWithText("Optional online medical-term enhancement")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Enhance terms with Gemini")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(
            "I have consent to send this transcript for online medical-term analysis"
        ).performScrollTo().assertIsDisplayed()
    }

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
                    onSelectConcern = {},
                    onUpdateConcern = { _, _ -> },
                    onTrainingDataConsentChanged = {},
                    onOnlineAnalysisConsentChanged = {},
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
                    onSelectConcern = {},
                    onUpdateConcern = { _, _ -> },
                    onTrainingDataConsentChanged = {},
                    onOnlineAnalysisConsentChanged = {},
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
                        hasOnlineAnalysisConsent = true,
                        clinicalAnalysisWarning =
                            "Medicine salts and terminology codes require doctor verification."
                    ),
                    onTranscriptChanged = {},
                    onConfirmConcern = {},
                    onApplySuggestion = {},
                    onSelectConcern = {},
                    onUpdateConcern = { _, _ -> },
                    onTrainingDataConsentChanged = {},
                    onOnlineAnalysisConsentChanged = {},
                    onAnalyzeTermsOnline = {},
                    onViewModeChanged = {},
                    onSpeakerRoleAssigned = { _, _ -> },
                    onRetry = {},
                    onBack = {},
                    onContinue = {}
                )
            }
        }

        composeRule.onNodeWithText("Gemini medical-term enhancement")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Analyze again").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Medicine salts and terminology codes require doctor verification."
        ).performScrollTo().assertIsDisplayed()
    }
}
