package com.carelipik.app.ui.screens.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carelipik.app.data.local.EncryptedLocalConsultationRepository
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.repository.ConsultationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConsultationHistoryUiState(
    val consultations: List<ApprovedConsultation> = emptyList(),
    val selected: ApprovedConsultation? = null,
    val isLoading: Boolean = true
)

class ConsultationHistoryViewModel(
    private val repository: ConsultationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConsultationHistoryUiState())
    val uiState: StateFlow<ConsultationHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                consultations = repository.list(),
                isLoading = false
            )
        }
    }

    fun select(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selected = repository.get(id))
        }
    }

    fun closeDetail() {
        _uiState.value = _uiState.value.copy(selected = null)
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            _uiState.value = _uiState.value.copy(
                consultations = repository.list(),
                selected = null,
                isLoading = false
            )
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConsultationHistoryViewModel::class.java))
            return ConsultationHistoryViewModel(
                EncryptedLocalConsultationRepository(applicationContext)
            ) as T
        }
    }
}
