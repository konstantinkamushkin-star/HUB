package com.divehub.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.remote.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class SessionViewModel(
    private val graph: AppGraph,
    private val repo: AuthRepository,
) : ViewModel() {

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    private val _preferDiverShell = MutableStateFlow(false)
    val preferDiverShell: StateFlow<Boolean> = _preferDiverShell.asStateFlow()

    private val _bootError = MutableStateFlow<String?>(null)
    val bootError: StateFlow<String?> = _bootError.asStateFlow()

    init {
        viewModelScope.launch {
            _preferDiverShell.value = graph.tokenStore.getPreferDiverShell()
            _user.value = repo.cachedUser()
            runCatching { repo.refreshProfile() }
                .onSuccess { _user.value = it }
                .onFailure { e ->
                    if (e is HttpException && e.code() == 401) {
                        repo.logout()
                        _bootError.value = "SESSION_EXPIRED"
                    } else {
                        _bootError.value = null
                    }
                }
        }
    }

    fun onUserUpdated(u: UserDto) {
        _user.value = u
    }

    fun setPreferDiverShell(enabled: Boolean) {
        viewModelScope.launch {
            graph.tokenStore.setPreferDiverShell(enabled)
            _preferDiverShell.value = enabled
        }
    }

    suspend fun logout() {
        repo.logout()
        _user.value = null
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionViewModel(graph, AuthRepository(graph)) as T
            }
        }
    }
}
