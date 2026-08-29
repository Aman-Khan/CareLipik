package com.carelipik.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class HomeProfileFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun saveDoctorProfile_personalizesHome() {
        composeRule.onNodeWithText("Welcome to CareLipik").assertIsDisplayed()
        composeRule.onNodeWithText("Set up doctor profile").performClick()

        composeRule.onNodeWithText("Doctor profile").assertIsDisplayed()
        composeRule.onNodeWithTag("doctor_profile_name").performTextInput("Asha Mehta")
        composeRule.onNodeWithTag("doctor_profile_save").performScrollTo().performClick()

        composeRule.onNodeWithText("Welcome, Dr Asha Mehta").assertIsDisplayed()
        composeRule.onNodeWithText("Dr Asha Mehta").assertIsDisplayed()
    }
}
