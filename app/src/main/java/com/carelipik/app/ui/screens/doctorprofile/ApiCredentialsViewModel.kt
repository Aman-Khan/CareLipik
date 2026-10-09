package com.carelipik.app.ui.screens.doctorprofile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carelipik.app.data.local.EncryptedApiCredentialRepository
import com.carelipik.app.domain.repository.ApiCredentialRepository
import com.carelipik.app.domain.repository.ApiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApiCredentialsUiState(
    val sarvamInput: String = "",
    val geminiInput: String = "",
    val hasSarvamKey: Boolean = false,
    val hasGeminiKey: Boolean = false,
    val isWorking: Boolean = false,
    val message: String? = null
)

class ApiCredentialsViewModel(
    private val repository: ApiCredentialRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ApiCredentialsUiState())
    val uiState: StateFlow<ApiCredentialsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun setSarvamInput(value: String) {
        _uiState.update { it.copy(sarvamInput = value, message = null) }
    }

    fun setGeminiInput(value: String) {
        _uiState.update { it.copy(geminiInput = value, message = null) }
    }

    fun save(provider: ApiProvider) {
        val key = when (provider) {
            ApiProvider.Sarvam -> _uiState.value.sarvamInput
            ApiProvider.Gemini -> _uiState.value.geminiInput
        }
        if (key.isBlank()) {
            _uiState.update { it.copy(message = "Enter a key before saving.") }
            return
        }
        _uiState.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.saveKey(provider, key) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            sarvamInput = if (provider == ApiProvider.Sarvam) "" else state.sarvamInput,
                            geminiInput = if (provider == ApiProvider.Gemini) "" else state.geminiInput,
                            hasSarvamKey = state.hasSarvamKey || provider == ApiProvider.Sarvam,
                            hasGeminiKey = state.hasGeminiKey || provider == ApiProvider.Gemini,
                            isWorking = false,
                            message = "${provider.name} key saved securely on this device."
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(isWorking = false, message = "The key could not be saved.")
                    }
                }
        }
    }

    fun delete(provider: ApiProvider) {
        _uiState.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.deleteKey(provider) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            hasSarvamKey = state.hasSarvamKey && provider != ApiProvider.Sarvam,
                            hasGeminiKey = state.hasGeminiKey && provider != ApiProvider.Gemini,
                            isWorking = false,
                            message = "${provider.name} key removed from this device."
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(isWorking = false, message = "The key could not be removed.")
                    }
                }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    hasSarvamKey = repository.hasKey(ApiProvider.Sarvam),
                    hasGeminiKey = repository.hasKey(ApiProvider.Gemini)
                )
            }
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ApiCredentialsViewModel::class.java))
            return ApiCredentialsViewModel(
                EncryptedApiCredentialRepository(applicationContext)
            ) as T
        }
    }
}
