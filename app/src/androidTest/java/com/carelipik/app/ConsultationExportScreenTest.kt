package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.export.ExportedConsultationFile
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.ui.screens.export.ConsultationExportScreen
import com.carelipik.app.ui.screens.export.ConsultationExportStatus
import com.carelipik.app.ui.screens.export.ConsultationExportUiState
import com.carelipik.app.ui.theme.CareLipikTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConsultationExportScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun generatedPdfShowsPrivacyNoticeAndShareAction() {
        var shareRequested = false
        composeRule.setContent {
            CareLipikTheme {
                ConsultationExportScreen(
                    uiState = ConsultationExportUiState(
                        status = ConsultationExportStatus.Generated,
                        consultation = ApprovedConsultation(
                            id = "00000000-0000-0000-0000-000000000001",
                            approvedAtMillis = 123L,
                            patientName = "Synthetic reference",
                            patientAge = "40",
                            visitReason = "Synthetic visit",
                            draft = ClinicalDraft(history = "Synthetic history")
                        ),
                        exportedFile = ExportedConsultationFile(
                            localPath = "/private/cache/synthetic.pdf",
                            displayName = "synthetic.pdf",
                            sizeBytes = 2_048L,
                            mimeType = "application/pdf",
                            format = ConsultationExportFormat.ClinicalPdf
                        )
                    ),
                    onFormatSelected = {},
                    onElectronicSignatureChanged = {},
                    onSignerNameChanged = {},
                    onHandwrittenSignatureChanged = {},
                    onGenerate = {},
                    onShare = { shareRequested = true },
                    onBack = {},
                    onFinish = {}
                )
            }
        }

        composeRule.onNodeWithText("Share carefully").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Exports contain sensitive clinical information. CareLipik creates them in " +
                "private cache and never includes consultation audio."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("share_consultation_export")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { assertTrue(shareRequested) }
    }
}
