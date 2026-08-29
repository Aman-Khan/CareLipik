package com.carelipik.app.ui.screens.welcome

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WelcomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    fun setRecordingConsent(isAccepted: Boolean) {
        _uiState.value = WelcomeUiState(hasRecordingConsent = isAccepted)
    }

    fun resetForNewConsultation() {
        _uiState.value = WelcomeUiState()
    }
}
