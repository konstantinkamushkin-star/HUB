package com.divehub.app.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.BookingRepository
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.remote.dto.BookingCreateDto
import com.divehub.app.data.remote.dto.BookingGearRentalDto
import com.divehub.app.data.remote.dto.BookingParticipantDto
import com.divehub.app.data.remote.dto.BookingPaymentDto
import com.divehub.app.data.remote.dto.ExploreDiveSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

data class MockBookingService(
    val id: String,
    val name: String,
    val priceUsd: Double,
    val durationMin: Int,
    /** Extra line (e.g. course level) shown before price/duration. */
    val subtitleExtra: String? = null,
)

data class MockGear(
    val id: String,
    val name: String,
    val size: String,
    val price: Double,
)

data class BookingWizardUiState(
    val step: Int = 0,
    val totalSteps: Int = 8,
    val centers: List<ExploreDiveSite> = emptyList(),
    val sites: List<ExploreDiveSite> = emptyList(),
    val centersLoading: Boolean = false,
    val sitesLoading: Boolean = false,
    val selectedCenterId: String? = null,
    val selectedServiceId: String? = null,
    val selectedDateMillis: Long? = null,
    val selectedTime: String = "09:00",
    val selectedInstructorId: String? = null,
    val selectedDiveSiteId: String? = null,
    val services: List<MockBookingService> = emptyList(),
    val gearCatalog: List<MockGear> = emptyList(),
    val selectedGearIds: Set<String> = emptySet(),
    val participants: List<BookingParticipantDto> = emptyList(),
    val paymentOnline: Boolean = true,
    val submitLoading: Boolean = false,
    val submitError: String? = null,
    val submitSuccess: Boolean = false,
    val participantDraftName: String = "",
    val participantDraftEmail: String = "",
    /** Optional message for the dive center (iOS booking / `CourseBookingView` notes). */
    val notes: String = "",
    /** Set when opening the wizard from a dive center course sheet (iOS `CourseBookingView`). */
    val courseContextSummary: String? = null,
)

class BookingWizardViewModel(
    private val graph: AppGraph,
    centerPref: String?,
    sitePref: String?,
    instructorPref: String?,
    coursePref: String?,
) : ViewModel() {

    private val exploreRepo = ExploreRepository(graph)
    private val bookingRepo = BookingRepository(graph)
    private val authRepo = AuthRepository(graph)
    private val tripsRepo = TripsRepository(graph)

    private val courseIdPref: String? =
        coursePref?.takeIf { it.isNotBlank() && it != "-" }

    private val _state = MutableStateFlow(
        BookingWizardUiState(
            selectedCenterId = centerPref?.takeIf { it.isNotBlank() && it != "-" },
            selectedDiveSiteId = sitePref?.takeIf { it.isNotBlank() && it != "-" },
            selectedInstructorId = instructorPref?.takeIf { it.isNotBlank() && it != "-" },
            services = mockServices(),
            gearCatalog = mockGear(),
        ),
    )
    val state: StateFlow<BookingWizardUiState> = _state.asStateFlow()

    init {
        refreshCenters()
        refreshSites()
        prefetchCourseService()
    }

    /** Merge selected dive center course into the mock service list and pre-select it. */
    private fun prefetchCourseService() {
        val courseId = courseIdPref ?: return
        val centerId = _state.value.selectedCenterId ?: return
        viewModelScope.launch {
            val courses = runCatching { tripsRepo.listCoursesForCenter(centerId) }.getOrElse { emptyList() }
            val match = courses.find { it.id == courseId } ?: return@launch
            val levelLine = match.level?.trim()?.takeIf { it.isNotBlank() }
            val summary = listOfNotNull(match.name, levelLine).joinToString(" · ")
            val row = MockBookingService(
                id = match.id,
                name = match.name,
                priceUsd = 0.0,
                durationMin = 0,
                subtitleExtra = levelLine,
            )
            _state.update { st ->
                val merged = st.services.filter { it.id != row.id } + row
                st.copy(
                    services = merged,
                    selectedServiceId = row.id,
                    courseContextSummary = summary,
                )
            }
        }
    }

    fun refreshCenters() {
        viewModelScope.launch {
            _state.update { it.copy(centersLoading = true) }
            val lang = graph.tokenStore.getAppLanguageTag().ifBlank { "en" }
            val list = runCatching { exploreRepo.getDiveCenters(limit = 120) }.getOrElse { emptyList() }
            _state.update { it.copy(centers = list, centersLoading = false) }
        }
    }

    fun refreshSites() {
        viewModelScope.launch {
            _state.update { it.copy(sitesLoading = true) }
            val lang = graph.tokenStore.getAppLanguageTag().ifBlank { "en" }
            val list = runCatching { exploreRepo.getDiveSites(language = lang, limit = 200) }.getOrElse { emptyList() }
            _state.update { it.copy(sites = list, sitesLoading = false) }
        }
    }

    fun setStep(s: Int) {
        _state.update { it.copy(step = s.coerceIn(0, it.totalSteps - 1)) }
    }

    fun next() {
        _state.update { it.copy(step = (it.step + 1).coerceAtMost(it.totalSteps - 1)) }
    }

    fun back() {
        _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    }

    fun selectCenter(id: String?) {
        _state.update { it.copy(selectedCenterId = id) }
    }

    fun selectService(id: String?) {
        _state.update { it.copy(selectedServiceId = id) }
    }

    fun setDateMillis(ms: Long?) {
        _state.update { it.copy(selectedDateMillis = ms) }
    }

    fun setTime(t: String) {
        _state.update { it.copy(selectedTime = t) }
    }

    fun selectInstructor(id: String?) {
        _state.update { it.copy(selectedInstructorId = id) }
    }

    fun selectDiveSite(id: String?) {
        _state.update { it.copy(selectedDiveSiteId = id) }
    }

    fun toggleGear(id: String) {
        _state.update {
            val next = it.selectedGearIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            it.copy(selectedGearIds = next)
        }
    }

    fun setParticipantDraft(name: String, email: String) {
        _state.update { it.copy(participantDraftName = name, participantDraftEmail = email) }
    }

    fun addParticipant() {
        _state.update { st ->
            val name = st.participantDraftName.trim()
            if (name.isEmpty()) return@update st
            val email = st.participantDraftEmail.trim().takeIf { it.isNotBlank() }
            val p = BookingParticipantDto(
                id = UUID.randomUUID().toString(),
                name = name,
                email = email,
                phoneNumber = null,
                certificationLevel = null,
                isFriend = false,
                friendUserId = null,
            )
            st.copy(
                participants = st.participants + p,
                participantDraftName = "",
                participantDraftEmail = "",
            )
        }
    }

    fun removeParticipant(id: String) {
        _state.update { it.copy(participants = it.participants.filter { p -> p.id != id }) }
    }

    fun setPaymentOnline(online: Boolean) {
        _state.update { it.copy(paymentOnline = online) }
    }

    fun setNotes(text: String) {
        _state.update { it.copy(notes = text) }
    }

    fun canProceed(): Boolean {
        val s = _state.value
        return when (s.step) {
            0 -> !s.selectedCenterId.isNullOrBlank()
            1 -> !s.selectedServiceId.isNullOrBlank()
            2 -> s.selectedDateMillis != null
            3, 4, 5, 6 -> true
            7 -> true
            else -> true
        }
    }

    fun clearSubmitError() {
        _state.update { it.copy(submitError = null) }
    }

    fun acknowledgeSubmitSuccess() {
        _state.update { it.copy(submitSuccess = false) }
    }

    fun submit() {
        viewModelScope.launch {
            val st = _state.value
            val app = graph.application
            val user = authRepo.cachedUser() ?: run {
                _state.update { it.copy(submitError = app.getString(R.string.booking_error_not_signed_in)) }
                return@launch
            }
            val centerId = st.selectedCenterId ?: run {
                _state.update { it.copy(submitError = app.getString(R.string.booking_error_select_center)) }
                return@launch
            }
            val serviceId = st.selectedServiceId ?: run {
                _state.update { it.copy(submitError = app.getString(R.string.booking_error_select_service)) }
                return@launch
            }
            val dateMs = st.selectedDateMillis ?: run {
                _state.update { it.copy(submitError = app.getString(R.string.booking_error_select_date)) }
                return@launch
            }
            val dateStr = Instant.ofEpochMilli(dateMs).atZone(ZoneOffset.UTC).toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            val nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            val gear = st.gearCatalog.filter { st.selectedGearIds.contains(it.id) }.map { g ->
                BookingGearRentalDto(
                    id = UUID.randomUUID().toString(),
                    gearItemId = g.id,
                    gearName = g.name,
                    size = g.size,
                    quantity = 1,
                    price = g.price,
                )
            }
            var participants = st.participants
            if (participants.isEmpty()) {
                participants = listOf(
                    BookingParticipantDto(
                        id = UUID.randomUUID().toString(),
                        name = user.displayName(),
                        email = user.email,
                        phoneNumber = user.phone,
                        certificationLevel = user.certificationLevel,
                        isFriend = false,
                        friendUserId = null,
                    ),
                )
            }
            val body = BookingCreateDto(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                diveCenterId = centerId,
                serviceId = serviceId,
                diveSiteId = st.selectedDiveSiteId,
                instructorId = st.selectedInstructorId,
                date = dateStr,
                startTime = st.selectedTime,
                participants = participants,
                gearRental = gear.takeIf { it.isNotEmpty() },
                payment = BookingPaymentDto(
                    method = if (st.paymentOnline) "online" else "on_site",
                    amount = 0.0,
                    currency = "USD",
                    status = "pending",
                    transactionId = null,
                    paidAt = null,
                ),
                status = "pending",
                notes = st.notes.trim().takeIf { it.isNotEmpty() },
                createdAt = nowIso,
                updatedAt = nowIso,
            )
            _state.update { it.copy(submitLoading = true, submitError = null) }
            val res = bookingRepo.create(body)
            res.onSuccess {
                _state.update { it.copy(submitLoading = false, submitSuccess = true) }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        submitLoading = false,
                        submitError = e.message ?: e::class.java.simpleName,
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            graph: AppGraph,
            centerId: String?,
            siteId: String?,
            instructorId: String?,
            courseId: String? = null,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BookingWizardViewModel(graph, centerId, siteId, instructorId, courseId) as T
            }
        }

        private fun mockServices() = listOf(
            MockBookingService("1", "Fun Dive", 50.0, 120),
            MockBookingService("2", "Discover Scuba", 100.0, 180),
            MockBookingService("3", "Open Water Course", 400.0, 1440),
        )

        private fun mockGear() = listOf(
            MockGear("g1", "BCD", "M", 15.0),
            MockGear("g2", "Regulator", "—", 20.0),
            MockGear("g3", "Wetsuit 5mm", "L", 18.0),
        )
    }
}
