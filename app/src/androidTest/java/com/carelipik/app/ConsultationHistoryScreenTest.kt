package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.repository.ConsultationReportArtifact
import com.carelipik.app.ui.screens.history.ConsultationHistoryScreen
import com.carelipik.app.ui.screens.history.ConsultationHistoryUiState
import com.carelipik.app.ui.theme.CareLipikTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConsultationHistoryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listOpensDetailAndExposesDeleteControl() {
        val item = ApprovedConsultation(
            id = "00000000-0000-0000-0000-000000000001",
            approvedAtMillis = 123L,
            patientName = "Synthetic reference",
            patientAge = "45",
            visitReason = "Synthetic visit",
            draft = ClinicalDraft(history = "Synthetic history")
        )
        var openedId: String? = null
        var openedReport: ConsultationReportArtifact? = null
        var sharedReport: ConsultationReportArtifact? = null
        var deletedId: String? = null
        var selected by mutableStateOf<ApprovedConsultation?>(null)
        val report = ConsultationReportArtifact(
            consultationId = item.id,
            format = ConsultationExportFormat.ClinicalPdf,
            generatedAtMillis = 456L,
            displayName = "synthetic-report.pdf",
            sizeBytes = 1_024L
        )
        composeRule.setContent {
            CareLipikTheme {
                ConsultationHistoryScreen(
                    uiState = ConsultationHistoryUiState(
                        consultations = listOf(item),
                        selected = selected,
                        reports = if (selected == null) emptyList() else listOf(report),
                        isLoading = false
                    ),
                    onOpen = {
                        openedId = it
                        selected = item
                    },
                    onExport = {},
                    onOpenReport = { openedReport = it },
                    onShareReport = { sharedReport = it },
                    onDeleteReport = {},
                    onDelete = { deletedId = it },
                    onBack = {}
                )
            }
        }

        composeRule.onAllNodesWithText("Step 1 of 1").assertCountEquals(0)
        composeRule.onNodeWithText("Synthetic reference").performClick()
        composeRule.runOnIdle { assertEquals(item.id, openedId) }
        composeRule.onNodeWithText("Synthetic history").assertIsDisplayed()
        composeRule.onNodeWithText("Open report").performScrollTo().performClick()
        composeRule.onNodeWithText("Share report").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(report, openedReport)
            assertEquals(report, sharedReport)
        }
        composeRule.onNodeWithText("Delete consultation").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(item.id, deletedId) }
    }
}
