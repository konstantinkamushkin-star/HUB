package com.divehub.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.repository.TripsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CenterInstructorsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val centerName: String? = null,
    val instructors: List<UserDto> = emptyList(),
)

class CenterInstructorsViewModel(
    private val graph: AppGraph,
    private val centerId: String,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CenterInstructorsUiState())
    val state: StateFlow<CenterInstructorsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { CenterInstructorsUiState(loading = true, error = null) }
            runCatching {
                val repo = TripsRepository(graph)
                val name = repo.listManagedDiveCenters().find { it.id == centerId }?.name
                val list = repo.listInstructorsForCenter(centerId)
                _state.update {
                    CenterInstructorsUiState(
                        loading = false,
                        centerName = name,
                        instructors = list,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    CenterInstructorsUiState(
                        loading = false,
                        error = authRepo.parseErrorMessage(e),
                    )
                }
            }
        }
    }

    companion object {
        fun factory(graph: AppGraph, centerId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CenterInstructorsViewModel(
                    graph,
                    centerId,
                    AuthRepository(graph),
                ) as T
            }
        }
    }
}
