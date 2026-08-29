package com.carelipik.app.ui.screens.welcome

data class WelcomeUiState(
    val hasRecordingConsent: Boolean = false
) {
    val canContinue: Boolean
        get() = hasRecordingConsent
}
