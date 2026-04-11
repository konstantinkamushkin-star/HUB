package com.divehub.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(
    private val repo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun login(
        email: String,
        password: String,
        onSuccess: (mustChangePassword: Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = AuthUiState(loading = true)
            runCatching { repo.login(email, password) }
                .onSuccess {
                    val must = repo.cachedUser()?.mustChangePassword == true
                    _state.value = AuthUiState(loading = false)
                    onSuccess(must)
                }
                .onFailure { e ->
                    _state.value = AuthUiState(loading = false, error = repo.parseErrorMessage(e))
                }
        }
    }

    fun register(
        email: String,
        password: String,
        displayName: String,
        personalDataConsent: Boolean,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = AuthUiState(loading = true)
            runCatching { repo.register(email, password, displayName, personalDataConsent) }
                .onSuccess {
                    _state.value = AuthUiState(loading = false)
                    onSuccess()
                }
                .onFailure { e ->
                    _state.value = AuthUiState(loading = false, error = repo.parseErrorMessage(e))
                }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(AuthRepository(graph)) as T
            }
        }
    }
}
