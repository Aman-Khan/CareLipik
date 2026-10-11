package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import com.carelipik.app.domain.model.ClinicalNoteSection
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.carelipik.app.domain.model.MedicationDraft
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.prescription.LetterheadRepository
import com.carelipik.app.domain.prescription.PrescriptionDetail
import com.carelipik.app.ui.screens.prescription.LetterheadSetup
import com.carelipik.app.ui.screens.prescription.PrescriptionEditor
import com.carelipik.app.ui.screens.prescription.PrescriptionViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrescriptionFieldsScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun viewModel() = PrescriptionViewModel(
        repository = object : LetterheadRepository {
            override suspend fun load(): String? = null
            override suspend fun save(template: String) = Unit
        },
        engine = { error("No network requests in UI test") },
        readPdf = { error("No real files in UI test") },
        writeDocument = { _, _ -> error("No export in UI test") },
        processAsynchronously = false
    )

    @Test fun structuredSuggestions_hideConversationAndRequireDoctorAddition() {
        val vm = viewModel()
        val transcript = "Doctor: tablet ExampleMed 500 mg. Drink water."
        vm.create(ClinicalDraft(reviewedTranscript = transcript,
            generationSource = ClinicalNoteGenerationSource.MedGemma,
            discussedMedications = listOf(MedicationDraft(name = "ExampleMed", strength = "500 mg")),
            prescriptionContext = listOf(ClinicalNoteSection("advice", "Advice", "Drink water.", listOf("T1")))),
            "Synthetic patient", "Example doctor")
        composeRule.setContent {
            val state by vm.uiState.collectAsState()
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { PrescriptionEditor(state, vm) {} }
            }
        }
        composeRule.onNodeWithText(transcript, substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("ExampleMed").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(vm.uiState.value.medicines.isEmpty()) }
        composeRule.onNodeWithText("Add to prescription").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(vm.uiState.value.medicines.single().name == "ExampleMed") }
        composeRule.onNodeWithText("Drink water.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Prescription PDF preview").performScrollTo().assertIsDisplayed()
    }

    @Test fun prescriptionDetails_syncDoctorEnteredPrecautionsToLatex() {
        val vm = viewModel()
        vm.create(ClinicalDraft(), "Synthetic patient", "Example doctor")
        composeRule.setContent {
            val state by vm.uiState.collectAsState()
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PrescriptionEditor(state, vm) {}
                }
            }
        }
        composeRule.onNodeWithText("Symptoms / complaints").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Precautions").performScrollTo().performTextInput("Synthetic precaution")
        composeRule.runOnIdle {
            assertTrue(vm.uiState.value.details[PrescriptionDetail.Precautions] == "Synthetic precaution")
            assertTrue(vm.uiState.value.source.contains("Synthetic precaution"))
        }
        composeRule.onNodeWithText("Prescription LaTeX source").assertDoesNotExist()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeRule.onNodeWithText("Prescription PDF preview").performScrollTo().assertIsDisplayed()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Prescription PDF page 1"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Prescription PDF page 1").performScrollTo().assertIsDisplayed()
    }

    @Test fun letterheadSetup_exposesCameraAndImagePdfImportAfterConsent() {
        val vm = viewModel()
        vm.setConsent(true)
        composeRule.setContent {
            val state by vm.uiState.collectAsState()
            MaterialTheme { LetterheadSetup(state, vm) }
        }
        composeRule.onNodeWithText("Import letter pad image/PDF").assertIsEnabled()
        composeRule.onNodeWithText("Photograph letter pad").assertIsEnabled()
    }
}
