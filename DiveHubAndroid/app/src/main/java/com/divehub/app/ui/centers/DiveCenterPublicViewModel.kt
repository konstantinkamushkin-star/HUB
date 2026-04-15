package com.divehub.app.ui.centers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.data.remote.dto.CourseListItemDto
import com.divehub.app.data.remote.dto.DiveCenterInstructorDto
import com.divehub.app.data.remote.dto.DiveCenterItemDto
import com.divehub.app.data.remote.dto.TripListItemDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class DiveCenterPublicUiState(
    val loading: Boolean = true,
    val center: DiveCenterItemDto? = null,
    val error: String? = null,
    val courses: List<CourseListItemDto> = emptyList(),
    val instructors: List<DiveCenterInstructorDto> = emptyList(),
    val upcomingTrips: List<TripListItemDto> = emptyList(),
    val pastTrips: List<TripListItemDto> = emptyList(),
    val imageApiRoot: String = "",
)

class DiveCenterPublicViewModel(
    private val graph: AppGraph,
    private val centerId: String,
) : ViewModel() {
    private val exploreRepo = ExploreRepository(graph)
    private val tripsRepo = TripsRepository(graph)

    private val _state = MutableStateFlow(DiveCenterPublicUiState())
    val state: StateFlow<DiveCenterPublicUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val root = graph.tokenStore.getRootBaseUrl()
            _state.update { DiveCenterPublicUiState(loading = true, imageApiRoot = root) }
            runCatching {
                coroutineScope {
                    val centerDef = async { exploreRepo.getDiveCenterById(centerId) }
                    val coursesDef = async { runCatching { tripsRepo.listCoursesForCenter(centerId) }.getOrElse { emptyList() } }
                    val instructorsDef = async { runCatching { exploreRepo.listDiveCenterInstructors(centerId) }.getOrElse { emptyList() } }
                    val tripsDef = async {
                        runCatching {
                            tripsRepo.listTrips(organizerId = centerId).filter { t ->
                                (t.organizerType ?: "").equals("dive_center", ignoreCase = true)
                            }
                        }.getOrElse { emptyList() }
                    }
                    val center = centerDef.await()
                    val trips = tripsDef.await()
                    val today = LocalDate.now(ZoneId.systemDefault())
                    val upcoming = trips.filter { t -> !isTripPast(t, today) }.sortedBy { it.startDate.orEmpty() }
                    val past = trips.filter { t -> isTripPast(t, today) }.sortedByDescending { it.startDate.orEmpty() }
                    _state.update {
                        it.copy(
                            loading = false,
                            center = center,
                            error = if (center == null) "not_found" else null,
                            courses = coursesDef.await(),
                            instructors = instructorsDef.await(),
                            upcomingTrips = upcoming,
                            pastTrips = past,
                        )
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Error", center = null)
                }
            }
        }
    }

    private fun parseLocalDate(iso: String?): LocalDate? {
        val head = iso?.trim()?.take(10)?.takeIf { it.length == 10 } ?: return null
        return runCatching { LocalDate.parse(head) }.getOrNull()
    }

    private fun tripEndOrStart(t: TripListItemDto): LocalDate? =
        parseLocalDate(t.endDate) ?: parseLocalDate(t.startDate)

    private fun isTripPast(t: TripListItemDto, today: LocalDate): Boolean {
        val end = tripEndOrStart(t) ?: return false
        return end.isBefore(today)
    }

    companion object {
        fun factory(graph: AppGraph, centerId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiveCenterPublicViewModel(graph, centerId) as T
            }
        }
    }
}
