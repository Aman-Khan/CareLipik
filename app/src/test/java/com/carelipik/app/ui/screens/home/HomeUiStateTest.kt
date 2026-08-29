package com.carelipik.app.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun emptyProfile_promptsDoctorToSetUpProfile() {
        val state = HomeUiState()

        assertEquals("Welcome to CareLipik", state.greetingTitle)
        assertEquals("Set up doctor profile", state.profileTitle)
        assertEquals("Add your specialty, clinic and preferences", state.profileSupportingText)
    }

    @Test
    fun savedProfile_personalizesHomeWithoutPatientInformation() {
        val state = HomeUiState(
            doctorName = "Mehta",
            specialty = "General medicine"
        )

        assertEquals("Welcome, Dr Mehta", state.greetingTitle)
        assertEquals("Dr Mehta", state.profileTitle)
        assertEquals("General medicine", state.profileSupportingText)
    }
}
