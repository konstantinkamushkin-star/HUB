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
import com.divehub.app.data.remote.dto.BookingGearOption
import com.divehub.app.data.remote.dto.BookingGearRentalDto
import com.divehub.app.data.remote.dto.BookingParticipantDto
import com.divehub.app.data.remote.dto.BookingPaymentDto
import com.divehub.app.data.remote.dto.PaymentIntentRequestDto
import com.divehub.app.data.remote.dto.BookingServiceOption
import com.divehub.app.data.remote.dto.ExploreDiveSite
import com.divehub.app.data.remote.dto.toBookingServiceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

data class BookingConfirmationSummary(
    val bookingId: String,
    val centerName: String,
    val serviceName: String,
    val date: String,
    val time: String,
    /** API values: `online`, `on_site`, `google_pay` (iOS also uses `apple_pay`). */
    val paymentMethod: String,
    val participantCount: Int,
    val gearSummary: String?,
    val notes: String?,
)

data class BookingWizardUiState(
    val step: Int = 0,
    val totalSteps: Int = 8,
    val centers: List<ExploreDiveSite> = emptyList(),
    val sites: List<ExploreDiveSite> = emptyList(),
    val centersLoading: Boolean = false,
    val sitesLoading: Boolean = false,
    val servicesLoading: Boolean = false,
    val servicesError: String? = null,
    val selectedCenterId: String? = null,
    val selectedServiceId: String? = null,
    val selectedDateMillis: Long? = null,
    val selectedTime: String = "09:00",
    val selectedInstructorId: String? = null,
    val selectedDiveSiteId: String? = null,
    val services: List<BookingServiceOption> = emptyList(),
    val gearCatalog: List<BookingGearOption> = emptyList(),
    val selectedGearIds: Set<String> = emptySet(),
    val participants: List<BookingParticipantDto> = emptyList(),
    /** `online` | `on_site` | `google_pay` — aligned with backend / iOS `Booking.Payment.method`. */
    val paymentMethod: String = "online",
    val submitLoading: Boolean = false,
    val submitError: String? = null,
    val submitSuccess: Boolean = false,
    val confirmationSummary: BookingConfirmationSummary? = null,
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
        ),
    )
    val state: StateFlow<BookingWizardUiState> = _state.asStateFlow()

    init {
        refreshCenters()
        refreshSites()
        val cid = centerPref?.takeIf { it.isNotBlank() && it != "-" }
        if (!cid.isNullOrBlank()) {
            loadServicesForCenter(cid)
        }
    }

    /** Merge selected dive center course into the service list and pre-select it (runs after catalog load). */
    private fun mergeCourseServiceIfNeeded() {
        val courseId = courseIdPref ?: return
        val centerId = _state.value.selectedCenterId ?: return
        viewModelScope.launch {
            val courses = runCatching { tripsRepo.listCoursesForCenter(centerId) }.getOrElse { emptyList() }
            val match = courses.find { it.id == courseId } ?: return@launch
            val levelLine = match.level?.trim()?.takeIf { it.isNotBlank() }
            val summary = listOfNotNull(match.name, levelLine).joinToString(" · ")
            val row = BookingServiceOption(
                id = match.id,
                name = match.name,
                priceAmount = 0.0,
                currency = "USD",
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

    fun retryLoadServices() {
        val id = _state.value.selectedCenterId ?: return
        loadServicesForCenter(id)
    }

    private fun loadServicesForCenter(centerId: String) {
        viewModelScope.launch {
            _state.update { it.copy(servicesLoading = true, servicesError = null) }
            val result = runCatching {
                graph.centerServicesApi().listByCenter(centerId).map { it.toBookingServiceOption() }
            }
            result.fold(
                onSuccess = { list ->
                    _state.update { it.copy(services = list, servicesLoading = false, servicesError = null) }
                    mergeCourseServiceIfNeeded()
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            services = emptyList(),
                            servicesLoading = false,
                            servicesError = e.message ?: e::class.java.simpleName,
                        )
                    }
                    mergeCourseServiceIfNeeded()
                },
            )
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
        _state.update {
            it.copy(
                selectedCenterId = id,
                selectedServiceId = null,
                selectedGearIds = emptySet(),
                services = emptyList(),
                servicesError = null,
            )
        }
        id?.takeIf { it.isNotBlank() }?.let { loadServicesForCenter(it) }
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

    fun setPaymentMethod(method: String) {
        val m = when (method.lowercase()) {
            "on_site", "onsite" -> "on_site"
            "google_pay", "googlepay" -> "google_pay"
            else -> "online"
        }
        _state.update { it.copy(paymentMethod = m) }
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
        _state.update { it.copy(submitSuccess = false, confirmationSummary = null) }
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
            val selectedService = st.services.find { it.id == serviceId }
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
            val gearTotal = gear.sumOf { it.price }
            val payAmount = (selectedService?.priceAmount ?: 0.0) + gearTotal
            val payCurrency = selectedService?.currency?.trim()?.takeIf { it.isNotEmpty() } ?: "USD"
            val payMethod = st.paymentMethod.trim().lowercase().let { m ->
                when (m) {
                    "on_site", "google_pay" -> m
                    else -> "online"
                }
            }
            var transactionId: String? = null
            if (payMethod == "online" || payMethod == "google_pay") {
                transactionId = runCatching {
                    graph.bookingApi().createPaymentIntent(
                        PaymentIntentRequestDto(
                            diveCenterId = centerId,
                            amount = payAmount,
                            currency = payCurrency,
                        ),
                    ).clientSecret
                }.getOrNull()
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
                    method = payMethod,
                    amount = payAmount,
                    currency = payCurrency,
                    status = "pending",
                    transactionId = transactionId,
                    paidAt = null,
                ),
                status = "pending",
                notes = st.notes.trim().takeIf { it.isNotEmpty() },
                createdAt = nowIso,
                updatedAt = nowIso,
            )
            _state.update { it.copy(submitLoading = true, submitError = null) }
            val res = bookingRepo.create(body)
            res.onSuccess { created ->
                val centerName = st.centers.find { it.id == created.diveCenterId }?.name ?: created.diveCenterId
                val serviceName = st.services.find { it.id == created.serviceId }?.name ?: created.serviceId
                val gearSummary = st.gearCatalog
                    .filter { g -> st.selectedGearIds.contains(g.id) }
                    .joinToString(", ") { it.name }
                    .takeIf { it.isNotBlank() }
                val summary = BookingConfirmationSummary(
                    bookingId = created.id,
                    centerName = centerName,
                    serviceName = serviceName,
                    date = created.date,
                    time = created.startTime,
                    paymentMethod = payMethod,
                    participantCount = created.participants.size,
                    gearSummary = gearSummary,
                    notes = created.notes?.trim()?.takeIf { it.isNotEmpty() },
                )
                _state.update {
                    it.copy(submitLoading = false, submitSuccess = true, confirmationSummary = summary)
                }
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

    }
}
