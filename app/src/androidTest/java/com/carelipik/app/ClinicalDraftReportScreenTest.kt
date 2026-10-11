package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
                    onGenerateWithMedGemma = {},
                    onAddMedication = {},
                    onMedicationChanged = { _, _ -> },
                    onRemoveMedication = {},
                    onRetry = {},
                    onBack = {},
                    onContinue = {}
                )
            }
        }

        composeRule.onNodeWithText("H&P").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("English").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("MedGemma structured clinical note")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Synthetic medicine").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("I verified this medicine and dosage")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
