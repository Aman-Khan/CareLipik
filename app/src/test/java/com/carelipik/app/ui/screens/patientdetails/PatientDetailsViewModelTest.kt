package com.carelipik.app.ui.screens.patientdetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientDetailsViewModelTest {
    @Test
    fun emptyPatientName_preventsContinueAndShowsError() {
        val viewModel = PatientDetailsViewModel()

        assertFalse(viewModel.validateForContinue())

        assertEquals("Enter a patient name or reference", viewModel.uiState.value.patientNameError)
    }

    @Test
    fun validPatientName_withBlankAge_allowsContinue() {
        val viewModel = PatientDetailsViewModel()

        viewModel.setPatientName("Patient 001")

        assertTrue(viewModel.validateForContinue())
        assertNull(viewModel.uiState.value.ageError)
    }

    @Test
    fun ageOutsideClinicalRange_preventsContinue() {
        val viewModel = PatientDetailsViewModel()

        viewModel.setPatientName("Patient 001")
        viewModel.setAge("131")

        assertFalse(viewModel.validateForContinue())
        assertEquals("Enter an age from 0 to 130", viewModel.uiState.value.ageError)
    }

    @Test
    fun ageInput_ignoresNonNumericCharacters() {
        val viewModel = PatientDetailsViewModel()

        viewModel.setAge("42")
        viewModel.setAge("42 years")

        assertEquals("42", viewModel.uiState.value.age)
    }
}
