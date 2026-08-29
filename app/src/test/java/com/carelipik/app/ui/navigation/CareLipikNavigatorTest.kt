package com.carelipik.app.ui.navigation

import com.carelipik.app.domain.model.ConsultationDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class CareLipikNavigatorTest {
    @Test
    fun initialDestination_isHome() {
        val navigator = CareLipikNavigator()

        assertEquals(ConsultationDestination.Home, navigator.currentDestination)
    }

    @Test
    fun startConsultation_opensRecordingConsent() {
        val navigator = CareLipikNavigator()

        navigator.startConsultation()

        assertEquals(ConsultationDestination.Welcome, navigator.currentDestination)
    }

    @Test
    fun nextFromWelcome_opensPatientDetails() {
        val navigator = CareLipikNavigator()
        navigator.startConsultation()
        navigator.navigateToNext()

        assertEquals(ConsultationDestination.PatientDetails, navigator.currentDestination)
    }

    @Test
    fun backFromPatientDetails_returnsToWelcome() {
        val navigator = CareLipikNavigator()
        navigator.startConsultation()
        navigator.navigateToNext()

        navigator.navigateBack()

        assertEquals(ConsultationDestination.Welcome, navigator.currentDestination)
    }

    @Test
    fun backFromConsent_returnsHome() {
        val navigator = CareLipikNavigator()
        navigator.startConsultation()

        navigator.navigateBack()

        assertEquals(ConsultationDestination.Home, navigator.currentDestination)
    }

    @Test
    fun profileAndHistory_openFromHomeAndReturnHome() {
        val navigator = CareLipikNavigator()

        navigator.openDoctorProfile()
        assertEquals(ConsultationDestination.DoctorProfile, navigator.currentDestination)
        navigator.navigateBack()
        assertEquals(ConsultationDestination.Home, navigator.currentDestination)

        navigator.openConsultationHistory()
        assertEquals(ConsultationDestination.ConsultationHistory, navigator.currentDestination)
        navigator.navigateBack()
        assertEquals(ConsultationDestination.Home, navigator.currentDestination)
    }

    @Test
    fun exportOpenedFromHistory_returnsToHistory() {
        val navigator = CareLipikNavigator()
        navigator.openConsultationHistory()
        navigator.openExportFromHistory()

        navigator.navigateBack()

        assertEquals(ConsultationDestination.ConsultationHistory, navigator.currentDestination)
    }

    @Test
    fun finishingExport_returnsHome() {
        val navigator = CareLipikNavigator()
        navigator.openConsultationHistory()
        navigator.openExportFromHistory()

        navigator.finishExport()

        assertEquals(ConsultationDestination.Home, navigator.currentDestination)
    }
}
