package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.carelipik.app.ui.screens.home.ConsultationSummaryUi
import com.carelipik.app.ui.screens.home.HomeScreen
import com.carelipik.app.ui.screens.home.HomeUiState
import com.carelipik.app.ui.theme.CareLipikTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class HomeRecentConsultationScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun recentCardOpensItsConsultationInsteadOfTheHistoryList() {
        var openedConsultationId: String? = null
        var openedHistory = false
        val consultationId = "00000000-0000-0000-0000-000000000001"
        composeRule.setContent {
            CareLipikTheme {
                HomeScreen(
                    uiState = HomeUiState(
                        recentConsultations = listOf(
                            ConsultationSummaryUi(
                                id = consultationId,
                                patientLabel = "Synthetic reference",
                                dateLabel = "Aug 30, 2026",
                                visitReason = "Synthetic visit",
                                noteFormatLabel = "SOAP"
                            )
                        )
                    ),
                    onStartConsultation = {},
                    onOpenProfile = {},
                    onOpenHistory = { openedHistory = true },
                    onOpenConsultation = { openedConsultationId = it }
                )
            }
        }

        composeRule.onNodeWithTag("home_recent_$consultationId").performClick()

        composeRule.runOnIdle {
            assertEquals(consultationId, openedConsultationId)
            assertFalse(openedHistory)
        }
    }
}
