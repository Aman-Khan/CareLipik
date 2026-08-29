package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
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
        var deletedId: String? = null
        var selected by mutableStateOf<ApprovedConsultation?>(null)
        composeRule.setContent {
            CareLipikTheme {
                ConsultationHistoryScreen(
                    uiState = ConsultationHistoryUiState(
                        consultations = listOf(item),
                        selected = selected,
                        isLoading = false
                    ),
                    onOpen = {
                        openedId = it
                        selected = item
                    },
                    onDelete = { deletedId = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Synthetic reference").performClick()
        composeRule.runOnIdle { assertEquals(item.id, openedId) }
        composeRule.onNodeWithText("Synthetic history").assertIsDisplayed()
        composeRule.onNodeWithText("Delete consultation").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(item.id, deletedId) }
    }
}
