package com.carelipik.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.carelipik.app.ui.screens.prescription.PrescriptionScreen
import com.carelipik.app.ui.theme.CareLipikTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PrescriptionModesScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun generationAndSupportToggle_showSeparateContentAndContinueToReview() {
        var continued = 0
        composeRule.setContent {
            CareLipikTheme {
                PrescriptionScreen(onBack = {}, onContinue = { continued++ },
                    generationContent = { Text("Synthetic PDF editor") },
                    supportContent = { Text("Synthetic medicine verification") })
            }
        }
        composeRule.onNodeWithText("Synthetic PDF editor").assertIsDisplayed()
        composeRule.onNodeWithText("Synthetic medicine verification").assertDoesNotExist()
        composeRule.onNodeWithText("Prescription support").performClick()
        composeRule.onNodeWithText("Synthetic medicine verification").assertIsDisplayed()
        composeRule.onNodeWithText("Synthetic PDF editor").assertDoesNotExist()
        composeRule.onNodeWithText("Prescription generation").performClick()
        composeRule.onNodeWithText("Synthetic PDF editor").assertIsDisplayed()
        composeRule.onNodeWithText("Continue to doctor review").performScrollTo().performClick()
        assertEquals(1, continued)
    }
}
