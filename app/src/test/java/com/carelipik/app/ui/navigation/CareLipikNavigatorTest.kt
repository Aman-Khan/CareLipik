package com.carelipik.app.ui.navigation

import com.carelipik.app.domain.model.ConsultationDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class CareLipikNavigatorTest {
    @Test
    fun nextFromWelcome_opensPatientDetails() {
        val navigator = CareLipikNavigator()

        navigator.navigateToNext()

        assertEquals(ConsultationDestination.PatientDetails, navigator.currentDestination)
    }

    @Test
    fun backFromPatientDetails_returnsToWelcome() {
        val navigator = CareLipikNavigator()
        navigator.navigateToNext()

        navigator.navigateBack()

        assertEquals(ConsultationDestination.Welcome, navigator.currentDestination)
    }
}
