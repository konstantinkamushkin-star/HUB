package com.divehub.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.AdminCenterInstructorsRepository
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.SocialRepository
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
    val assigning: Boolean = false,
    val candidates: List<UserDto> = emptyList(),
    val assignError: String? = null,
)

class CenterInstructorsViewModel(
    private val graph: AppGraph,
    private val centerId: String,
    private val authRepo: AuthRepository,
    private val socialRepo: SocialRepository,
    private val localRepo: AdminCenterInstructorsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CenterInstructorsUiState())
    val state: StateFlow<CenterInstructorsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val repo = TripsRepository(graph)
                val name = repo.listManagedDiveCenters().find { it.id == centerId }?.name
                val remote = repo.listInstructorsForCenter(centerId)
                val list = localRepo.mergeWithLocal(centerId, remote)
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        centerName = name,
                        instructors = list,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, error = authRepo.parseErrorMessage(e))
                }
            }
        }
    }

    fun searchCandidates(query: String) {
        val q = query.trim()
        if (q.length < 2) {
            _state.update { it.copy(candidates = emptyList(), assignError = null, assigning = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(assigning = true, assignError = null) }
            runCatching { socialRepo.searchUsers(q) }
                .onSuccess { users ->
                    val assigned = _state.value.instructors.map { it.id }.toSet()
                    _state.update {
                        it.copy(
                            assigning = false,
                            assignError = null,
                            candidates = users
                                .filterNot { u -> assigned.contains(u.id) }
                                .filter { u ->
                                    val role = u.role.orEmpty()
                                    role.equals("INSTRUCTOR", ignoreCase = true) || role.isBlank()
                                }
                                .take(20),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            assigning = false,
                            candidates = emptyList(),
                            assignError = authRepo.parseErrorMessage(e),
                        )
                    }
                }
        }
    }

    fun assign(user: UserDto) {
        viewModelScope.launch {
            localRepo.assign(centerId, user)
            _state.update {
                val merged = (it.instructors + user).distinctBy { u -> u.id }
                it.copy(
                    instructors = merged,
                    candidates = it.candidates.filterNot { c -> c.id == user.id },
                    assignError = null,
                )
            }
        }
    }

    fun unassign(user: UserDto) {
        viewModelScope.launch {
            localRepo.unassign(centerId, user.id)
            _state.update {
                it.copy(instructors = it.instructors.filterNot { i -> i.id == user.id })
            }
        }
    }

    fun clearAssignError() {
        _state.update { it.copy(assignError = null) }
    }

    companion object {
        fun factory(graph: AppGraph, centerId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CenterInstructorsViewModel(
                    graph,
                    centerId,
                    AuthRepository(graph),
                    SocialRepository(graph),
                    AdminCenterInstructorsRepository(graph),
                ) as T
            }
        }
    }
}
