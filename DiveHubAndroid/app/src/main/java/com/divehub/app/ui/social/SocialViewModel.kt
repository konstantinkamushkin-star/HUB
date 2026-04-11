package com.divehub.app.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.SocialRepository
import com.divehub.app.data.remote.dto.FriendRequestDto
import com.divehub.app.data.remote.dto.UserDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SocialUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val friends: List<UserDto> = emptyList(),
    val received: List<FriendRequestDto> = emptyList(),
    val sent: List<FriendRequestDto> = emptyList(),
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchError: String? = null,
    val searchResults: List<UserDto> = emptyList(),
)

class SocialViewModel(private val repo: SocialRepository) : ViewModel() {
    private val _state = MutableStateFlow(SocialUiState())
    val state: StateFlow<SocialUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val friends = repo.friends()
                val received = repo.receivedRequests()
                val sent = repo.sentRequests()
                Triple(friends, received, sent)
            }.onSuccess { (friends, received, sent) ->
                _state.value = _state.value.copy(
                    loading = false,
                    friends = friends,
                    received = received,
                    sent = sent,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Load error")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query, searchError = null)
        searchJob?.cancel()
        if (query.trim().isEmpty()) {
            _state.value = _state.value.copy(searchResults = emptyList(), searching = false, searchError = null)
            return
        }
        if (query.trim().length < 2) {
            _state.value = _state.value.copy(searchResults = emptyList(), searching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(450)
            if (_state.value.searchQuery == query) {
                searchUsers()
            }
        }
    }

    fun searchUsers() {
        val query = _state.value.searchQuery.trim()
        if (query.length < 2) {
            _state.value = _state.value.copy(
                searching = false,
                searchError = ERR_MIN_QUERY,
                searchResults = emptyList(),
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(searching = true, searchError = null)
            runCatching { repo.searchUsers(query) }
                .onSuccess { users ->
                    val friendIds = _state.value.friends.map { it.id }.toSet()
                    _state.value = _state.value.copy(
                        searching = false,
                        searchResults = users.filter { it.id !in friendIds },
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        searching = false,
                        searchError = e.message ?: "Search error",
                    )
                }
        }
    }

    fun sendRequest(userId: String) {
        viewModelScope.launch {
            runCatching { repo.sendRequest(userId) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        searchResults = _state.value.searchResults.filterNot { it.id == userId },
                        searchError = null,
                    )
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        searchError = e.message ?: "Request failed",
                    )
                }
        }
    }

    fun accept(userId: String) {
        viewModelScope.launch {
            runCatching { repo.acceptRequest(userId) }.onSuccess { refresh() }
        }
    }

    fun decline(friendshipId: String) {
        viewModelScope.launch {
            runCatching { repo.declineRequest(friendshipId) }.onSuccess { refresh() }
        }
    }

    companion object {
        const val ERR_MIN_QUERY = "ERR_MIN_QUERY"

        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SocialViewModel(SocialRepository(graph)) as T
            }
        }
    }
}
