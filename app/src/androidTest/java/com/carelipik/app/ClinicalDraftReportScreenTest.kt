package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.material3.Text
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteSection
import com.carelipik.app.domain.model.MedicationDraft
import com.carelipik.app.ui.screens.clinicaldraft.ClinicalDraftScreen
import com.carelipik.app.ui.screens.clinicaldraft.ClinicalDraftStatus
import com.carelipik.app.ui.screens.clinicaldraft.ClinicalDraftUiState
import com.carelipik.app.ui.theme.CareLipikTheme
import org.junit.Rule
import org.junit.Test

class ClinicalDraftReportScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reportOptionsEnglishGenerationAndMedicationReviewAreVisible() {
        var onDeviceRequests = 0
        composeRule.setContent {
            CareLipikTheme {
                ClinicalDraftScreen(
                    uiState = ClinicalDraftUiState(
                        status = ClinicalDraftStatus.Ready,
                        draft = ClinicalDraft(
                            noteFormat = ClinicalNoteFormat.Soap,
                            structuredSections = listOf(
                                ClinicalNoteSection("subjective", "Subjective", "Synthetic note")
                            ),
                            medications = listOf(
                                MedicationDraft(name = "Synthetic medicine", dose = "One tablet")
                            )
                        )
                    ),
                    onPatientAgeChanged = {},
                    onNoteFormatSelected = {},
                    onNoteLanguageSelected = {},
                    onSpecialtyNameChanged = {},
                    onSectionChanged = { _, _ -> },
                    onOnlineGenerationConsentChanged = {},
                    onGenerateWithGemini = {},
                    onGenerateWithMedGemma = { onDeviceRequests++ },
                    onAddMedication = {},
                    onMedicationChanged = { _, _ -> },
                    onRemoveMedication = {},
                    onRetry = {},
                    onBack = {},
                    onContinue = {},
                    prescriptionContent = { Text("Prescription source editor") }
                )
            }
        }

        composeRule.onNodeWithText("Generate on device").performScrollTo().performClick()
        org.junit.Assert.assertEquals(1, onDeviceRequests)
        composeRule.onNodeWithText("Prescription").performScrollTo().performClick()
        composeRule.onNodeWithText("Generate on device").performScrollTo().performClick()
        org.junit.Assert.assertEquals(2, onDeviceRequests)
        composeRule.onNodeWithText("Prescription source editor").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("H&P").assertDoesNotExist()
        composeRule.onNodeWithText("Report").performScrollTo().performClick()
        composeRule.onNodeWithText("Prescription source editor").assertDoesNotExist()
        composeRule.onNodeWithText("H&P").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("English").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("On-device clinical note")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Synthetic medicine").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("I verified this medicine and dosage")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
