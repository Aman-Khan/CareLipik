package com.carelipik.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import com.carelipik.app.domain.model.ConsultationDestination
import com.carelipik.app.ui.navigation.CareLipikNavigator
import com.carelipik.app.ui.screens.patientdetails.PatientDetailsViewModel
import com.carelipik.app.ui.screens.welcome.WelcomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConsultationRotationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityRecreation_retainsScreenPatientDetailsAndConsent() {
        lateinit var originalNavigator: CareLipikNavigator
        composeRule.activityRule.scenario.onActivity { activity ->
            val models = ViewModelProvider(activity)
            originalNavigator = models[CareLipikNavigator::class.java]
            models[WelcomeViewModel::class.java].setRecordingConsent(true)
            models[PatientDetailsViewModel::class.java].apply {
                setPatientName("Synthetic rotation reference")
                setAge("42")
                setVisitReason("Synthetic visit reason")
            }
            originalNavigator.startConsultation()
            originalNavigator.navigateToNext()
        }
        composeRule.onNodeWithText("Patient details").assertExists()

        // Rotation recreates the Activity while retaining its ViewModelStore.
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Patient details").assertExists()
        composeRule.onNodeWithText("Synthetic rotation reference").assertExists()
        composeRule.onNodeWithText("42").assertExists()
        composeRule.onNodeWithText("Synthetic visit reason").assertExists()
        composeRule.activityRule.scenario.onActivity { activity ->
            val models = ViewModelProvider(activity)
            assertSame(originalNavigator, models[CareLipikNavigator::class.java])
            assertTrue(models[WelcomeViewModel::class.java].uiState.value.hasRecordingConsent)
        }
    }

    @Test
    fun activityRecreation_retainsExportReturnDestination() {
        composeRule.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[CareLipikNavigator::class.java].apply {
                openConsultationHistory()
                openExportFromHistory()
            }
        }

        composeRule.activityRule.scenario.recreate()

        composeRule.activityRule.scenario.onActivity { activity ->
            val navigator = ViewModelProvider(activity)[CareLipikNavigator::class.java]
            assertEquals(ConsultationDestination.Export, navigator.destination.value)
            navigator.navigateBack()
            assertEquals(ConsultationDestination.ConsultationHistory, navigator.destination.value)
        }
    }
}
