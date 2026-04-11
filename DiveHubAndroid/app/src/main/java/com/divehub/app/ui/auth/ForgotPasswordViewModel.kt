package com.divehub.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PasswordResetStep {
    EMAIL,
    VERIFICATION_CODE,
    NEW_PASSWORD,
}

data class ForgotPasswordUiState(
    val step: PasswordResetStep = PasswordResetStep.EMAIL,
    val loading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val countdown: Int = 0,
)

class ForgotPasswordViewModel(
    private val repo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, success = null)
    }

    fun setStep(step: PasswordResetStep) {
        _state.value = _state.value.copy(step = step, error = null)
    }

    fun sendCode(email: String) {
        if (email.isBlank()) {
            _state.value = _state.value.copy(error = "Введите email")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, success = null)
            runCatching { repo.requestPasswordReset(email) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        loading = false,
                        step = PasswordResetStep.VERIFICATION_CODE,
                        success = "Код подтверждения отправлен",
                    )
                    startCountdown()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = repo.parseErrorMessage(e))
                }
        }
    }

    fun verifyCode(email: String, code: String) {
        if (code.length != 6 || code.any { !it.isDigit() }) {
            _state.value = _state.value.copy(error = "Код должен состоять из 6 цифр")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, success = null)
            runCatching { repo.verifyResetCode(email, code) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        loading = false,
                        step = PasswordResetStep.NEW_PASSWORD,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = repo.parseErrorMessage(e))
                }
        }
    }

    fun resetPassword(email: String, code: String, newPassword: String, confirmPassword: String, onDone: () -> Unit) {
        if (newPassword.length < 8) {
            _state.value = _state.value.copy(error = "Пароль должен быть не менее 8 символов")
            return
        }
        if (newPassword.none { it.isLetter() } || newPassword.none { it.isDigit() }) {
            _state.value = _state.value.copy(error = "Пароль должен содержать буквы и цифры")
            return
        }
        if (newPassword != confirmPassword) {
            _state.value = _state.value.copy(error = "Пароли не совпадают")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, success = null)
            runCatching { repo.resetPassword(email, code, newPassword) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        loading = false,
                        success = "Пароль успешно изменен",
                    )
                    onDone()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = repo.parseErrorMessage(e))
                }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in 60 downTo 0) {
                _state.value = _state.value.copy(countdown = sec)
                delay(1000)
            }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ForgotPasswordViewModel(AuthRepository(graph)) as T
            }
        }
    }
}
