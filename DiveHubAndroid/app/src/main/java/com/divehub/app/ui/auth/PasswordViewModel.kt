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

class PasswordViewModel(
    private val repo: AuthRepository,
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun submit(current: String, newPass: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { repo.changePassword(current, newPass) }
                .onSuccess {
                    _loading.value = false
                    onDone()
                }
                .onFailure { e ->
                    _loading.value = false
                    _error.value = repo.parseErrorMessage(e)
                }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PasswordViewModel(AuthRepository(graph)) as T
            }
        }
    }
}
