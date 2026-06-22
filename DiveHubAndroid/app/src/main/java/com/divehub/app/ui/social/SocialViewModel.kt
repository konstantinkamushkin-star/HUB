package com.divehub.app.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.SocialRepository
import com.divehub.app.data.remote.dto.CreateGroupTripBody
import com.divehub.app.data.remote.dto.DiscoverNearbyDto
import com.divehub.app.data.remote.dto.FriendLocationDto
import com.divehub.app.data.remote.dto.FriendRequestDto
import com.divehub.app.data.remote.dto.GroupTripDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.ProfilePreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private data class SocialRefreshBundle(
    val friends: List<UserDto>,
    val received: List<FriendRequestDto>,
    val sent: List<FriendRequestDto>,
    val trips: List<GroupTripDto>,
)

data class SocialUiState(
    val loading: Boolean = true,
    val error: String? = null,
    /** API root for resolving `UserDto.avatarUrl` (same as Feed). */
    val imageApiRoot: String = "",
    val friends: List<UserDto> = emptyList(),
    val received: List<FriendRequestDto> = emptyList(),
    val sent: List<FriendRequestDto> = emptyList(),
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchError: String? = null,
    val searchResults: List<UserDto> = emptyList(),
    val friendLocations: List<FriendLocationDto> = emptyList(),
    val discoverNearby: List<DiscoverNearbyDto> = emptyList(),
    val trackingMode: Int = 0,
    val groupTrips: List<GroupTripDto> = emptyList(),
    val groupTripsLoading: Boolean = false,
)

class SocialViewModel(
    private val graph: AppGraph,
    private val repo: SocialRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SocialUiState())
    val state: StateFlow<SocialUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val imgRoot = graph.tokenStore.getRootBaseUrl()
            _state.value = _state.value.copy(loading = true, error = null, imageApiRoot = imgRoot)
            runCatching {
                SocialRefreshBundle(
                    friends = repo.friends(),
                    received = repo.receivedRequests(),
                    sent = repo.sentRequests(),
                    trips = repo.groupTrips(),
                )
            }.onSuccess { bundle ->
                val friends = bundle.friends
                val received = bundle.received
                val sent = bundle.sent
                val trips = bundle.trips
                _state.value = _state.value.copy(
                    loading = false,
                    friends = friends,
                    received = received,
                    sent = sent,
                    groupTrips = trips,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Load error")
            }
        }
    }

    fun loadFriendLocations(lat: Double?, lng: Double?) {
        viewModelScope.launch {
            val prefs = ProfilePreferencesRepository(graph).loadPrivacyPrefs()
            if (prefs.shareLocation && lat != null && lng != null) {
                runCatching { repo.reportLocation(lat, lng, null) }
            }
            runCatching { repo.friendLocations(lat, lng) }
                .onSuccess { locs ->
                    _state.value = _state.value.copy(friendLocations = locs)
                }
        }
    }

    fun loadDiversNearby(lat: Double, lng: Double) {
        viewModelScope.launch {
            runCatching { repo.discoverNearby(lat, lng) }
                .onSuccess { users ->
                    val friendIds = _state.value.friends.map { it.id }.toSet()
                    _state.value = _state.value.copy(
                        discoverNearby = users.filter { it.userId !in friendIds },
                    )
                }
        }
    }

    /** @deprecated Use [loadFriendLocations] or [loadDiversNearby]. */
    fun loadTrackingData(lat: Double?, lng: Double?) {
        loadFriendLocations(lat, lng)
    }

    fun createGroupTrip(
        name: String,
        description: String?,
        destination: String?,
        startDate: String,
        memberIds: List<String>,
        onSuccess: (GroupTripDto) -> Unit,
        onFailure: ((Throwable) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(groupTripsLoading = true)
            runCatching {
                repo.createGroupTrip(
                    CreateGroupTripBody(
                        name = name,
                        description = description,
                        destination = destination,
                        startDate = startDate,
                        memberUserIds = memberIds,
                    ),
                )
            }.onSuccess { trip ->
                _state.value = _state.value.copy(
                    groupTrips = listOf(trip) + _state.value.groupTrips,
                    groupTripsLoading = false,
                )
                onSuccess(trip)
            }.onFailure { error ->
                _state.value = _state.value.copy(groupTripsLoading = false)
                onFailure?.invoke(error)
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
                        discoverNearby = _state.value.discoverNearby.filterNot { it.userId == userId },
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
                return SocialViewModel(graph, SocialRepository(graph)) as T
            }
        }
    }
}
