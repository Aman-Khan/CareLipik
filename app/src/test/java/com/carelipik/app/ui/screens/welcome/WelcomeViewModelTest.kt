package com.carelipik.app.ui.screens.welcome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeViewModelTest {
    @Test
    fun initialState_disablesContinue() {
        val viewModel = WelcomeViewModel()

        assertFalse(viewModel.uiState.value.hasRecordingConsent)
        assertFalse(viewModel.uiState.value.canContinue)
    }

    @Test
    fun acceptingConsent_enablesContinue() {
        val viewModel = WelcomeViewModel()

        viewModel.setRecordingConsent(true)

        assertTrue(viewModel.uiState.value.hasRecordingConsent)
        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun withdrawingConsent_disablesContinue() {
        val viewModel = WelcomeViewModel()

        viewModel.setRecordingConsent(true)
        viewModel.setRecordingConsent(false)

        assertFalse(viewModel.uiState.value.canContinue)
    }
}
