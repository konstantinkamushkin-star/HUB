package com.divehub.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.ChatRepository
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.data.remote.dto.TripListItemDto
import com.divehub.app.data.remote.dto.participantUserRows
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class TripsListUiState(
    val loading: Boolean = true,
    val trips: List<TripListItemDto> = emptyList(),
    val error: String? = null,
)

data class TripDetailUiState(
    val loading: Boolean = true,
    val trip: TripListItemDto? = null,
    val error: String? = null,
    val loggedIn: Boolean = false,
    val joinInProgress: Boolean = false,
    val joinSuccessMessage: String? = null,
    val joinError: String? = null,
    /** userId → displayName from `GET users/{id}`; absent key means still unresolved or failed. */
    val participantDisplayNames: Map<String, String> = emptyMap(),
    val participantsNamesLoading: Boolean = false,
    /** Same idea as iOS TripsManagementView: organizer or managed dive center. */
    val canManageTrip: Boolean = false,
    /** courseId → title; falls back to id if unknown. */
    val courseLabels: Map<String, String> = emptyMap(),
    val loadingCourseNames: Boolean = false,
    /** For resolving `/api/media/...` paths when showing trip photos. */
    val imageApiRoot: String = "",
)

class TripsListViewModel(
    private val repo: TripsRepository,
    private val organizerId: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(TripsListUiState())
    val state: StateFlow<TripsListUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            runCatching { repo.listTrips(organizerId = organizerId) }
                .onSuccess { trips ->
                    _state.value = TripsListUiState(loading = false, trips = trips, error = null)
                }
                .onFailure { e ->
                    _state.value = prev.copy(loading = false, error = e.message ?: "Error")
                }
        }
    }

    fun deleteTrip(tripId: String, onFinished: (Throwable?) -> Unit) {
        viewModelScope.launch {
            val err = runCatching { repo.deleteTrip(tripId) }.exceptionOrNull()
            if (err == null) {
                refresh()
            }
            onFinished(err)
        }
    }

    companion object {
        fun factory(graph: AppGraph, organizerId: String? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TripsListViewModel(TripsRepository(graph), organizerId) as T
            }
        }
    }
}

class TripDetailViewModel(
    private val graph: AppGraph,
    private val repo: TripsRepository,
    private val tripId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(TripDetailUiState())
    val state: StateFlow<TripDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val token = graph.tokenStore.getAccessToken()
            _state.value = _state.value.copy(loggedIn = !token.isNullOrBlank())
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            val imageRoot = graph.tokenStore.getRootBaseUrl()
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    participantDisplayNames = emptyMap(),
                    participantsNamesLoading = false,
                    canManageTrip = false,
                    courseLabels = emptyMap(),
                    loadingCourseNames = false,
                    imageApiRoot = imageRoot,
                )
            }
            runCatching { repo.getTrip(tripId) }
                .onSuccess { trip ->
                    _state.update { it.copy(loading = false, trip = trip) }
                    resolveCanManage(trip)
                    resolveParticipantDisplayNames(trip)
                    resolveCourseLabels(trip)
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Error") }
                }
        }
    }

    private fun resolveCanManage(trip: TripListItemDto) {
        viewModelScope.launch {
            val me = AuthRepository(graph).cachedUser()
            if (me == null) {
                _state.update { it.copy(canManageTrip = false) }
                return@launch
            }
            val oid = trip.organizerId.orEmpty()
            if (oid.isNotEmpty() && oid == me.id) {
                _state.update { it.copy(canManageTrip = true) }
                return@launch
            }
            val ot = (trip.organizerType ?: "").lowercase()
            if (ot == "dive_center" && oid.isNotEmpty()) {
                val managed = runCatching { repo.listManagedDiveCenters() }.getOrElse { emptyList() }
                _state.update {
                    it.copy(canManageTrip = managed.any { c -> c.id == oid })
                }
                return@launch
            }
            _state.update { it.copy(canManageTrip = false) }
        }
    }

    private fun resolveCourseLabels(trip: TripListItemDto) {
        val ids = trip.availableCourses.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) {
            _state.update { it.copy(courseLabels = emptyMap(), loadingCourseNames = false) }
            return
        }
        val ot = trip.organizerType?.lowercase().orEmpty()
        val oid = trip.organizerId?.trim().orEmpty()
        if (ot != "dive_center" || oid.isEmpty()) {
            _state.update { it.copy(courseLabels = ids.associateWith { id -> id }, loadingCourseNames = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loadingCourseNames = true) }
            val courses = runCatching { repo.listCoursesForCenter(oid) }.getOrElse { emptyList() }
            val byId = courses.associateBy { c -> c.id }
            val labels = ids.associateWith { id -> byId[id]?.name?.trim()?.takeIf { it.isNotEmpty() } ?: id }
            _state.update { it.copy(courseLabels = labels, loadingCourseNames = false) }
        }
    }

    private suspend fun resolveParticipantDisplayNames(trip: TripListItemDto) {
        val ids = trip.participantUserRows().map { it.first }.distinct()
        if (ids.isEmpty()) {
            _state.update { it.copy(participantDisplayNames = emptyMap(), participantsNamesLoading = false) }
            return
        }
        _state.update { it.copy(participantsNamesLoading = true) }
        val map = coroutineScope {
            ids.map { id ->
                async {
                    val name = runCatching { graph.usersApi().getUser(id).displayName() }.getOrNull()
                    if (name.isNullOrBlank()) id to id else id to name
                }
            }.awaitAll().toMap()
        }
        _state.update { it.copy(participantDisplayNames = map, participantsNamesLoading = false) }
    }

    fun joinTrip() {
        viewModelScope.launch {
            if (!graph.tokenStore.getAccessToken().isNullOrBlank()) {
                _state.value = _state.value.copy(loggedIn = true)
            } else {
                _state.value = _state.value.copy(joinError = "login_required")
                return@launch
            }
            val t = _state.value.trip ?: return@launch
            val total = t.totalSpots ?: 0
            val booked = t.bookedSpots ?: 0
            if (total <= 0 || booked >= total) {
                _state.value = _state.value.copy(joinError = "no_spots")
                return@launch
            }
            _state.value = _state.value.copy(
                joinInProgress = true,
                joinError = null,
                joinSuccessMessage = null,
            )
            runCatching { repo.joinTrip(tripId) }
                .onSuccess { res ->
                    _state.value = _state.value.copy(
                        joinInProgress = false,
                        joinSuccessMessage = "${res.bookedSpots}/${res.totalSpots}",
                    )
                    schedulePostJoinOrganizerChat(t)
                    load()
                }
                .onFailure { e ->
                    val msg = when (e) {
                        is HttpException -> e.response()?.errorBody()?.string()?.take(200) ?: e.message
                        else -> e.message
                    }
                    _state.value = _state.value.copy(
                        joinInProgress = false,
                        joinError = msg ?: "Error",
                    )
                }
        }
    }

    fun clearJoinFeedback() {
        _state.value = _state.value.copy(joinError = null, joinSuccessMessage = null)
    }

    /**
     * iOS `TripBookingView.createChatForBooking`: open organizer conversation and send an intro message.
     * Best-effort; failures are ignored (same as iOS `print` on error).
     */
    private fun schedulePostJoinOrganizerChat(trip: TripListItemDto) {
        val orgId = trip.organizerId?.trim().orEmpty()
        if (orgId.isEmpty()) return
        viewModelScope.launch {
            val peerType = when (trip.organizerType?.trim()?.lowercase().orEmpty()) {
                "dive_center" -> "dive_center"
                else -> "user"
            }
            val place = listOfNotNull(trip.region?.trim(), trip.country?.trim())
                .filter { it.isNotEmpty() }
                .joinToString(", ")
                .ifBlank { trip.tripType?.trim().orEmpty().ifBlank { trip.id } }
            val intro = graph.application.getString(R.string.trip_booking_chat_intro, place)
            runCatching {
                val chatRepo = ChatRepository(graph)
                val conv = chatRepo.openConversation(peerId = orgId, peerType = peerType)
                chatRepo.sendText(conv.id, intro)
            }
        }
    }

    companion object {
        fun factory(graph: AppGraph, tripId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TripDetailViewModel(graph, TripsRepository(graph), tripId) as T
            }
        }
    }
}
