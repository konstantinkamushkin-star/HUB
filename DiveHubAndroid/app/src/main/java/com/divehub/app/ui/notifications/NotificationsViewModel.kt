package com.divehub.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.NotificationsRepository
import com.divehub.app.data.remote.dto.AppNotificationDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val loading: Boolean = true,
    val notifications: List<AppNotificationDto> = emptyList(),
    val error: String? = null,
)

class NotificationsViewModel(private val repo: NotificationsRepository) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            runCatching { repo.list() }
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = null,
                        notifications = list,
                    )
                }
                .onFailure { e ->
                    _state.value = prev.copy(
                        loading = false,
                        error = e.message ?: "Error",
                    )
                }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            runCatching { repo.markAllRead() }
                .onSuccess {
                    _state.value = _state.value.copy(
                        notifications = _state.value.notifications.map { it.copy(isRead = true) },
                    )
                }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repo.delete(id) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        notifications = _state.value.notifications.filter { it.id != id },
                    )
                }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NotificationsViewModel(NotificationsRepository(graph)) as T
            }
        }
    }
}
