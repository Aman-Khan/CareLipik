package com.carelipik.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.carelipik.app.data.local.EncryptedLocalConsultationRepository
import com.carelipik.app.domain.model.DoctorProfile
import com.carelipik.app.domain.repository.ConsultationRepository
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HomeViewModel(
    initialState: HomeUiState = HomeUiState(),
    private val consultationRepository: ConsultationRepository? = null,
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshRecentConsultations()
    }

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

    fun refreshRecentConsultations() {
        val repository = consultationRepository ?: return
        runOperation {
            val recent = repository.list().take(MAX_RECENT_CONSULTATIONS).map { consultation ->
                ConsultationSummaryUi(
                    id = consultation.id,
                    patientLabel = consultation.patientName,
                    dateLabel = DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM,
                        DateFormat.SHORT
                    ).format(Date(consultation.approvedAtMillis)),
                    visitReason = consultation.visitReason.ifBlank { "No visit reason recorded" },
                    noteFormatLabel = consultation.draft.noteFormat.displayName
                )
            }
            _uiState.update { it.copy(recentConsultations = recent) }
        }
    }

    private fun runOperation(operation: suspend () -> Unit) {
        if (processAsynchronously) {
            viewModelScope.launch { operation() }
        } else {
            runBlocking { operation() }
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            return HomeViewModel(
                consultationRepository = EncryptedLocalConsultationRepository(applicationContext)
            ) as T
        }
    }

    private companion object {
        const val MAX_RECENT_CONSULTATIONS = 3
    }
}
