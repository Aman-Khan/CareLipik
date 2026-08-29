package com.carelipik.app.ui.screens.home

import androidx.lifecycle.ViewModel
import com.carelipik.app.domain.model.DoctorProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HomeViewModel(initialState: HomeUiState = HomeUiState()) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun applyDoctorProfile(profile: DoctorProfile) {
        _uiState.update { state ->
            state.copy(
                doctorName = profile.fullName,
                specialty = profile.specialty,
                processingLabel = profile.processingPreference.displayName,
                processingDetail = profile.processingPreference.homeDetail
            )
        }
    }
}
