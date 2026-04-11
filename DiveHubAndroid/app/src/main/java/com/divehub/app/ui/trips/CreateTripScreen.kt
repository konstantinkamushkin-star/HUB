package com.divehub.app.ui.trips

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.remote.dto.CourseListItemDto
import com.divehub.app.data.remote.dto.CreateTripRequestDto
import com.divehub.app.data.remote.dto.DiveCenterBriefDto
import com.divehub.app.data.remote.dto.UpdateTripRequestDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.util.absoluteMediaUrl
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private val dateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")

private fun isoDateToYmd(s: String?): String {
    val t = s?.trim().orEmpty()
    return if (t.length >= 10 && t[4] == '-' && t[7] == '-') t.take(10) else t
}

private fun cloneJsonObject(graph: AppGraph, src: JsonObject?): JsonObject? {
    if (src == null) return null
    return graph.gson.fromJson(graph.gson.toJson(src), JsonObject::class.java)
}

data class TripRoomDraft(
    val editingId: String?,
    val roomType: String,
    val roomCount: String,
    val divingPrice: String,
    val nonDivingPrice: String,
)

data class TripCabinDraft(
    val editingId: String?,
    val cabinType: String,
    val cabinCount: String,
    val divingPrice: String,
    val nonDivingPrice: String,
)

data class CreateTripUiState(
    val loadingCenters: Boolean = true,
    val centers: List<DiveCenterBriefDto> = emptyList(),
    val centersError: String? = null,
    val tripLoadError: String? = null,
    val selectedCenterId: String? = null,
    val tripTypeDaily: Boolean = true,
    val hotelLabel: String = "",
    val yachtLabel: String = "",
    val country: String = "",
    val region: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val description: String = "",
    val totalSpots: String = "8",
    val minimumCertificationLevel: String = "",
    val minimumDives: String = "",
    val nitroxAvailable: Boolean = false,
    val equipmentRentalAvailable: Boolean = false,
    val groupLeaderId: String? = null,
    val priceCurrency: String = "USD",
    /** Clone of server `priceDetails` for merge on save (currency updated from field). */
    val priceDetailsBaseline: JsonObject? = null,
    val programDays: List<TripProgramDayModel> = emptyList(),
    val programDayDraft: TripProgramDayDraft? = null,
    val additionalExpenses: List<TripExpenseEditRow> = emptyList(),
    val expenseDraft: TripExpenseFormDraft? = null,
    val countries: List<String> = emptyList(),
    val loadingCountries: Boolean = false,
    val importingTrip: Boolean = false,
    val importError: String? = null,
    /** API root (e.g. `http://10.0.2.2:3000`) for resolving uploaded relative paths in previews. */
    val imageApiRoot: String = "",
    val photoUploadInProgress: Boolean = false,
    val photoUploadError: String? = null,
    val photoUrlsText: String = "",
    val selectedCourseIds: Set<String> = emptySet(),
    val courses: List<CourseListItemDto> = emptyList(),
    val loadingCourses: Boolean = false,
    val coursesLoadError: String? = null,
    val roomPrices: List<TripRoomPriceRow> = emptyList(),
    val cabinPrices: List<TripCabinPriceRow> = emptyList(),
    val roomDraft: TripRoomDraft? = null,
    val cabinDraft: TripCabinDraft? = null,
    val instructors: List<UserDto> = emptyList(),
    val loadingInstructors: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    val createdTripId: String? = null,
    val editingTripId: String? = null,
    val saveSuccessPop: Boolean = false,
)

class CreateTripViewModel(
    private val graph: AppGraph,
    private val repo: TripsRepository,
    private val editingTripId: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(CreateTripUiState())
    val state: StateFlow<CreateTripUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val imageRoot = graph.tokenStore.getRootBaseUrl()
            val centersResult = runCatching { repo.listManagedDiveCenters() }
            if (centersResult.isFailure) {
                _state.value = CreateTripUiState(
                    loadingCenters = false,
                    centersError = centersResult.exceptionOrNull()?.message ?: "Error",
                    imageApiRoot = imageRoot,
                )
                return@launch
            }
            val list = centersResult.getOrThrow()
            val editId = editingTripId
            if (editId.isNullOrBlank()) {
                val firstId = list.firstOrNull()?.id
                _state.value = CreateTripUiState(
                    loadingCenters = false,
                    centers = list,
                    selectedCenterId = firstId,
                    imageApiRoot = imageRoot,
                )
                loadInstructors(firstId)
                loadCourses(firstId)
                loadCountries()
                return@launch
            }
            val tripResult = runCatching { repo.getTrip(editId) }
            if (tripResult.isFailure) {
                _state.value = CreateTripUiState(
                    loadingCenters = false,
                    centers = list,
                    tripLoadError = tripResult.exceptionOrNull()?.message ?: "Error",
                    imageApiRoot = imageRoot,
                )
                return@launch
            }
            val t = tripResult.getOrThrow()
            val orgId = t.organizerId?.takeIf { it.isNotBlank() } ?: list.firstOrNull()?.id
            val cur = t.priceDetails?.get("currency")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
            _state.value = CreateTripUiState(
                loadingCenters = false,
                centers = list,
                selectedCenterId = orgId,
                editingTripId = editId,
                tripTypeDaily = (t.tripType == "daily"),
                hotelLabel = t.hotelLabel.orEmpty(),
                yachtLabel = t.yachtLabel.orEmpty(),
                country = t.country.orEmpty(),
                region = t.region.orEmpty(),
                startDate = isoDateToYmd(t.startDate),
                endDate = isoDateToYmd(t.endDate),
                description = t.description.orEmpty(),
                totalSpots = (t.totalSpots ?: 8).toString(),
                minimumCertificationLevel = t.minimumCertificationLevel.orEmpty(),
                minimumDives = t.minimumDives?.takeIf { it > 0 }?.toString().orEmpty(),
                nitroxAvailable = t.nitroxAvailable == true,
                equipmentRentalAvailable = t.equipmentRentalAvailable == true,
                groupLeaderId = t.groupLeaderId,
                priceCurrency = cur.ifBlank { "USD" },
                priceDetailsBaseline = cloneJsonObject(graph, t.priceDetails),
                programDays = parseProgramDaysFromJson(t.programDays),
                additionalExpenses = parseTripExpensesFromJson(t.additionalExpenses),
                photoUrlsText = t.photos?.joinToString("\n").orEmpty(),
                selectedCourseIds = t.availableCourses?.toSet() ?: emptySet(),
                roomPrices = parseRoomPrices(t.priceDetails),
                cabinPrices = parseCabinPrices(t.priceDetails),
                imageApiRoot = imageRoot,
            )
            loadInstructors(orgId)
            loadCourses(orgId)
            loadCountries()
        }
    }

    private fun loadCountries() {
        viewModelScope.launch {
            _state.update { it.copy(loadingCountries = true) }
            val list = runCatching { ExploreRepository(graph).getCountries() }.getOrElse { emptyList() }
            _state.update {
                it.copy(
                    loadingCountries = false,
                    countries = list.distinct().sorted(),
                )
            }
        }
    }

    private fun loadCourses(centerId: String?) {
        if (centerId.isNullOrBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingCourses = true, coursesLoadError = null) }
            runCatching { repo.listCoursesForCenter(centerId) }
                .onSuccess { list ->
                    _state.update { it.copy(loadingCourses = false, courses = list) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loadingCourses = false,
                            courses = emptyList(),
                            coursesLoadError = e.message,
                        )
                    }
                }
        }
    }

    private fun loadInstructors(centerId: String?) {
        if (centerId.isNullOrBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingInstructors = true) }
            runCatching { repo.listInstructorsForCenter(centerId) }
                .onSuccess { list ->
                    _state.update { it.copy(loadingInstructors = false, instructors = list) }
                }
                .onFailure {
                    _state.update { it.copy(loadingInstructors = false, instructors = emptyList()) }
                }
        }
    }

    fun setSelectedCenter(id: String) {
        _state.update {
            it.copy(
                selectedCenterId = id,
                selectedCourseIds = emptySet(),
                courses = emptyList(),
            )
        }
        loadInstructors(id)
        loadCourses(id)
    }

    fun setTripTypeDaily(daily: Boolean) {
        _state.update { it.copy(tripTypeDaily = daily) }
    }

    fun setHotelLabel(v: String) {
        _state.update { it.copy(hotelLabel = v) }
    }

    fun setYachtLabel(v: String) {
        _state.update { it.copy(yachtLabel = v) }
    }

    fun setCountry(v: String) {
        _state.update { it.copy(country = v) }
    }

    fun setRegion(v: String) {
        _state.update { it.copy(region = v) }
    }

    fun setStartDate(v: String) {
        _state.update { it.copy(startDate = v) }
    }

    fun setEndDate(v: String) {
        _state.update { it.copy(endDate = v) }
    }

    fun setDescription(v: String) {
        _state.update { it.copy(description = v) }
    }

    fun setTotalSpots(v: String) {
        _state.update { it.copy(totalSpots = v) }
    }

    fun setMinCert(v: String) {
        _state.update { it.copy(minimumCertificationLevel = v) }
    }

    fun setMinDives(v: String) {
        _state.update { it.copy(minimumDives = v) }
    }

    fun setNitrox(v: Boolean) {
        _state.update { it.copy(nitroxAvailable = v) }
    }

    fun setEquipmentRental(v: Boolean) {
        _state.update { it.copy(equipmentRentalAvailable = v) }
    }

    fun setGroupLeader(id: String?) {
        _state.update { it.copy(groupLeaderId = id) }
    }

    fun setPriceCurrency(v: String) {
        _state.update { it.copy(priceCurrency = v) }
    }

    fun setPhotoUrlsText(v: String) {
        _state.update { it.copy(photoUrlsText = v) }
    }

    fun clearSaveError() {
        _state.update { it.copy(saveError = null) }
    }

    fun consumeCreatedTripId() {
        _state.update { it.copy(createdTripId = null) }
    }

    fun consumeSaveSuccessPop() {
        _state.update { it.copy(saveSuccessPop = false) }
    }

    fun toggleCourseSelection(courseId: String) {
        _state.update { s ->
            val next = s.selectedCourseIds.toMutableSet()
            if (!next.add(courseId)) next.remove(courseId)
            s.copy(selectedCourseIds = next)
        }
    }

    fun openNewRoomDraft() {
        _state.update {
            it.copy(
                roomDraft = TripRoomDraft(
                    editingId = null,
                    roomType = "",
                    roomCount = "1",
                    divingPrice = "0",
                    nonDivingPrice = "0",
                ),
            )
        }
    }

    fun openEditRoomDraft(row: TripRoomPriceRow) {
        _state.update {
            it.copy(
                roomDraft = TripRoomDraft(
                    editingId = row.id,
                    roomType = row.roomType,
                    roomCount = row.roomCount.toString(),
                    divingPrice = row.divingPrice.toString(),
                    nonDivingPrice = row.nonDivingPrice.toString(),
                ),
            )
        }
    }

    fun dismissRoomDraft() {
        _state.update { it.copy(roomDraft = null) }
    }

    fun updateRoomDraft(
        roomType: String? = null,
        roomCount: String? = null,
        divingPrice: String? = null,
        nonDivingPrice: String? = null,
    ) {
        _state.update { s ->
            val d = s.roomDraft ?: return@update s
            s.copy(
                roomDraft = d.copy(
                    roomType = roomType ?: d.roomType,
                    roomCount = roomCount ?: d.roomCount,
                    divingPrice = divingPrice ?: d.divingPrice,
                    nonDivingPrice = nonDivingPrice ?: d.nonDivingPrice,
                ),
            )
        }
    }

    fun saveRoomDraft() {
        val s = _state.value
        val d = s.roomDraft ?: return
        if (d.roomType.trim().isEmpty()) {
            _state.update { it.copy(saveError = "room_form") }
            return
        }
        val count = d.roomCount.trim().toIntOrNull()
        if (count == null || count < 1) {
            _state.update { it.copy(saveError = "room_form") }
            return
        }
        val div = d.divingPrice.trim().toDoubleOrNull()
        val non = d.nonDivingPrice.trim().toDoubleOrNull()
        if (div == null || non == null || div < 0 || non < 0) {
            _state.update { it.copy(saveError = "room_form") }
            return
        }
        val id = d.editingId ?: UUID.randomUUID().toString()
        val row = TripRoomPriceRow(id, d.roomType.trim(), count, div, non)
        _state.update { st ->
            val list = st.roomPrices.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = row else list.add(row)
            st.copy(roomPrices = list, roomDraft = null, saveError = null)
        }
    }

    fun removeRoomPrice(id: String) {
        _state.update { it.copy(roomPrices = it.roomPrices.filterNot { r -> r.id == id }) }
    }

    fun openNewCabinDraft() {
        _state.update {
            it.copy(
                cabinDraft = TripCabinDraft(
                    editingId = null,
                    cabinType = "",
                    cabinCount = "1",
                    divingPrice = "0",
                    nonDivingPrice = "0",
                ),
            )
        }
    }

    fun openEditCabinDraft(row: TripCabinPriceRow) {
        _state.update {
            it.copy(
                cabinDraft = TripCabinDraft(
                    editingId = row.id,
                    cabinType = row.cabinType,
                    cabinCount = row.cabinCount.toString(),
                    divingPrice = row.divingPrice.toString(),
                    nonDivingPrice = row.nonDivingPrice.toString(),
                ),
            )
        }
    }

    fun dismissCabinDraft() {
        _state.update { it.copy(cabinDraft = null) }
    }

    fun updateCabinDraft(
        cabinType: String? = null,
        cabinCount: String? = null,
        divingPrice: String? = null,
        nonDivingPrice: String? = null,
    ) {
        _state.update { s ->
            val d = s.cabinDraft ?: return@update s
            s.copy(
                cabinDraft = d.copy(
                    cabinType = cabinType ?: d.cabinType,
                    cabinCount = cabinCount ?: d.cabinCount,
                    divingPrice = divingPrice ?: d.divingPrice,
                    nonDivingPrice = nonDivingPrice ?: d.nonDivingPrice,
                ),
            )
        }
    }

    fun saveCabinDraft() {
        val s = _state.value
        val d = s.cabinDraft ?: return
        if (d.cabinType.trim().isEmpty()) {
            _state.update { it.copy(saveError = "cabin_form") }
            return
        }
        val count = d.cabinCount.trim().toIntOrNull()
        if (count == null || count < 1) {
            _state.update { it.copy(saveError = "cabin_form") }
            return
        }
        val div = d.divingPrice.trim().toDoubleOrNull()
        val non = d.nonDivingPrice.trim().toDoubleOrNull()
        if (div == null || non == null || div < 0 || non < 0) {
            _state.update { it.copy(saveError = "cabin_form") }
            return
        }
        val id = d.editingId ?: UUID.randomUUID().toString()
        val row = TripCabinPriceRow(id, d.cabinType.trim(), count, div, non)
        _state.update { st ->
            val list = st.cabinPrices.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = row else list.add(row)
            st.copy(cabinPrices = list, cabinDraft = null, saveError = null)
        }
    }

    fun removeCabinPrice(id: String) {
        _state.update { it.copy(cabinPrices = it.cabinPrices.filterNot { c -> c.id == id }) }
    }

    fun clearImportError() {
        _state.update { it.copy(importError = null) }
    }

    fun clearPhotoUploadError() {
        _state.update { it.copy(photoUploadError = null) }
    }

    fun uploadPhotosFromGallery(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(photoUploadInProgress = true, photoUploadError = null) }
            val root = graph.tokenStore.getRootBaseUrl()
            runCatching {
                for (uri in uris) {
                    val path = repo.uploadTripPhoto(context, uri)
                    val full = absoluteMediaUrl(root, path)
                    _state.update { s ->
                        val lines = s.photoUrlsText.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                        lines.add(full)
                        s.copy(photoUrlsText = lines.joinToString("\n"))
                    }
                }
            }.onFailure { e ->
                _state.update { it.copy(photoUploadError = e.message ?: "Upload failed") }
            }
            _state.update { it.copy(photoUploadInProgress = false) }
        }
    }

    fun importTripFromWebsite(url: String) {
        val centerId = _state.value.selectedCenterId ?: return
        val u = url.trim()
        if (u.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(importingTrip = true, importError = null) }
            runCatching { repo.importTripFromUrl(u, centerId) }
                .onSuccess { res ->
                    _state.update { it.copy(importingTrip = false, createdTripId = res.tripId) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(importingTrip = false, importError = e.message ?: "Error")
                    }
                }
        }
    }

    private fun emptyActivityDraft() = TripProgramActivityDraft(
        id = UUID.randomUUID().toString(),
        time = "09:00",
        activity = "",
        notes = "",
        diveSiteId = "",
        diveCenterId = "",
    )

    fun openNewProgramDay() {
        val s = _state.value
        val defaultDate = s.startDate.trim().ifBlank { "2099-01-01" }
        _state.update {
            it.copy(
                programDayDraft = TripProgramDayDraft(
                    editingId = null,
                    dateYmd = defaultDate,
                    description = "",
                    activities = listOf(emptyActivityDraft()),
                ),
            )
        }
    }

    fun openEditProgramDay(day: TripProgramDayModel) {
        _state.update {
            it.copy(
                programDayDraft = TripProgramDayDraft(
                    editingId = day.id,
                    dateYmd = day.dateYmd,
                    description = day.description,
                    activities = day.activities.map { a ->
                        TripProgramActivityDraft(
                            id = a.id,
                            time = a.time,
                            activity = a.activity,
                            notes = a.notes.orEmpty(),
                            diveSiteId = a.diveSiteId.orEmpty(),
                            diveCenterId = a.diveCenterId.orEmpty(),
                        )
                    }.ifEmpty { listOf(emptyActivityDraft()) },
                ),
            )
        }
    }

    fun dismissProgramDayDraft() {
        _state.update { it.copy(programDayDraft = null) }
    }

    fun updateProgramDayDraft(
        dateYmd: String? = null,
        description: String? = null,
    ) {
        _state.update { st ->
            val d = st.programDayDraft ?: return@update st
            st.copy(
                programDayDraft = d.copy(
                    dateYmd = dateYmd ?: d.dateYmd,
                    description = description ?: d.description,
                ),
            )
        }
    }

    fun addProgramActivityDraft() {
        _state.update { st ->
            val d = st.programDayDraft ?: return@update st
            st.copy(programDayDraft = d.copy(activities = d.activities + emptyActivityDraft()))
        }
    }

    fun removeProgramActivityDraft(index: Int) {
        _state.update { st ->
            val d = st.programDayDraft ?: return@update st
            val next = d.activities.filterIndexed { i, _ -> i != index }
            st.copy(
                programDayDraft = d.copy(
                    activities = next.ifEmpty { listOf(emptyActivityDraft()) },
                ),
            )
        }
    }

    fun updateProgramActivityDraft(
        index: Int,
        time: String? = null,
        activity: String? = null,
        notes: String? = null,
        diveSiteId: String? = null,
        diveCenterId: String? = null,
    ) {
        _state.update { st ->
            val d = st.programDayDraft ?: return@update st
            if (index !in d.activities.indices) return@update st
            val row = d.activities[index]
            val updated = row.copy(
                time = time ?: row.time,
                activity = activity ?: row.activity,
                notes = notes ?: row.notes,
                diveSiteId = diveSiteId ?: row.diveSiteId,
                diveCenterId = diveCenterId ?: row.diveCenterId,
            )
            val list = d.activities.toMutableList().also { it[index] = updated }
            st.copy(programDayDraft = d.copy(activities = list))
        }
    }

    fun saveProgramDayDraft() {
        val s = _state.value
        val d = s.programDayDraft ?: return
        if (!dateRegex.matches(d.dateYmd.trim())) {
            _state.update { it.copy(saveError = "program_dates") }
            return
        }
        val acts = d.activities.mapNotNull { a ->
            if (a.activity.isBlank() && a.time.isBlank()) return@mapNotNull null
            TripProgramActivityModel(
                id = a.id.ifBlank { UUID.randomUUID().toString() },
                time = a.time.trim(),
                activity = a.activity.trim(),
                diveSiteId = a.diveSiteId.trim().takeIf { it.isNotEmpty() },
                diveCenterId = a.diveCenterId.trim().takeIf { it.isNotEmpty() },
                notes = a.notes.trim().takeIf { it.isNotEmpty() },
            )
        }
        val id = d.editingId ?: UUID.randomUUID().toString()
        val model = TripProgramDayModel(
            id = id,
            dateYmd = d.dateYmd.trim(),
            description = d.description.trim(),
            activities = acts,
        )
        _state.update { st ->
            val list = st.programDays.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = model else list.add(model)
            list.sortBy { it.dateYmd }
            st.copy(programDays = list, programDayDraft = null, saveError = null)
        }
    }

    fun removeProgramDay(id: String) {
        _state.update { it.copy(programDays = it.programDays.filterNot { d -> d.id == id }) }
    }

    fun openNewExpenseDraft() {
        val cur = _state.value.priceCurrency.trim().ifBlank { "USD" }
        _state.update {
            it.copy(
                expenseDraft = TripExpenseFormDraft(
                    editingId = null,
                    expenseType = "other",
                    description = "",
                    cost = "0",
                    currency = cur,
                ),
            )
        }
    }

    fun openEditExpenseDraft(row: TripExpenseEditRow) {
        _state.update {
            it.copy(
                expenseDraft = TripExpenseFormDraft(
                    editingId = row.id,
                    expenseType = row.expenseType,
                    description = row.description,
                    cost = row.cost.toString(),
                    currency = row.currency,
                ),
            )
        }
    }

    fun dismissExpenseDraft() {
        _state.update { it.copy(expenseDraft = null) }
    }

    fun updateExpenseDraft(
        expenseType: String? = null,
        description: String? = null,
        cost: String? = null,
        currency: String? = null,
    ) {
        _state.update { st ->
            val d = st.expenseDraft ?: return@update st
            st.copy(
                expenseDraft = d.copy(
                    expenseType = expenseType ?: d.expenseType,
                    description = description ?: d.description,
                    cost = cost ?: d.cost,
                    currency = currency ?: d.currency,
                ),
            )
        }
    }

    fun saveExpenseDraft() {
        val s = _state.value
        val d = s.expenseDraft ?: return
        val cost = d.cost.trim().toDoubleOrNull()
        if (cost == null || cost < 0) {
            _state.update { it.copy(saveError = "expense_form") }
            return
        }
        val cur = d.currency.trim().ifBlank { "USD" }
        val id = d.editingId ?: UUID.randomUUID().toString()
        val row = TripExpenseEditRow(
            id = id,
            expenseType = d.expenseType.trim().ifBlank { "other" },
            description = d.description.trim(),
            cost = cost,
            currency = cur,
        )
        _state.update { st ->
            val list = st.additionalExpenses.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = row else list.add(row)
            st.copy(additionalExpenses = list, expenseDraft = null, saveError = null)
        }
    }

    fun removeExpense(id: String) {
        _state.update { it.copy(additionalExpenses = it.additionalExpenses.filterNot { e -> e.id == id }) }
    }

    private fun buildPriceDetails(s: CreateTripUiState): JsonObject {
        val base = cloneJsonObject(graph, s.priceDetailsBaseline) ?: JsonObject()
        base.remove("roomPrices")
        base.remove("yachtPrices")
        base.addProperty("currency", s.priceCurrency.trim().ifBlank { "USD" })
        if (s.tripTypeDaily) {
            val roomArr = JsonArray()
            s.roomPrices.forEach { r ->
                roomArr.add(
                    JsonObject().apply {
                        addProperty("id", r.id)
                        addProperty("roomType", r.roomType)
                        addProperty("roomCount", r.roomCount)
                        addProperty("divingPrice", r.divingPrice)
                        addProperty("nonDivingPrice", r.nonDivingPrice)
                    },
                )
            }
            base.add("roomPrices", roomArr)
        } else {
            val cabArr = JsonArray()
            s.cabinPrices.forEach { c ->
                cabArr.add(
                    JsonObject().apply {
                        addProperty("id", c.id)
                        addProperty("cabinType", c.cabinType)
                        addProperty("cabinCount", c.cabinCount)
                        addProperty("divingPrice", c.divingPrice)
                        addProperty("nonDivingPrice", c.nonDivingPrice)
                    },
                )
            }
            base.add("yachtPrices", cabArr)
        }
        return base
    }

    fun submit() {
        val s = _state.value
        val centerId = s.selectedCenterId
        if (centerId.isNullOrBlank()) {
            _state.update { it.copy(saveError = "no_center") }
            return
        }
        if (s.country.trim().length < 2) {
            _state.update { it.copy(saveError = "country") }
            return
        }
        if (!dateRegex.matches(s.startDate.trim()) || !dateRegex.matches(s.endDate.trim())) {
            _state.update { it.copy(saveError = "dates_format") }
            return
        }
        if (s.endDate.trim() < s.startDate.trim()) {
            _state.update { it.copy(saveError = "dates_order") }
            return
        }
        if (s.description.trim().length < 5) {
            _state.update { it.copy(saveError = "description") }
            return
        }
        val spots = s.totalSpots.trim().toIntOrNull()
        if (spots == null || spots < 1 || spots > 500) {
            _state.update { it.copy(saveError = "spots") }
            return
        }
        if (s.tripTypeDaily && s.hotelLabel.trim().isEmpty()) {
            _state.update { it.copy(saveError = "hotel") }
            return
        }
        if (!s.tripTypeDaily && s.yachtLabel.trim().isEmpty()) {
            _state.update { it.copy(saveError = "yacht") }
            return
        }
        for (day in s.programDays) {
            if (!dateRegex.matches(day.dateYmd.trim())) {
                _state.update { it.copy(saveError = "program_dates") }
                return
            }
        }
        for (e in s.additionalExpenses) {
            if (e.cost < 0) {
                _state.update { it.copy(saveError = "expense_form") }
                return
            }
        }
        val programArr = s.programDays.toProgramDaysJsonArray()
        val expensesArr = s.additionalExpenses.toAdditionalExpensesJsonArray()
        val minDives = s.minimumDives.trim().toIntOrNull()
        val editId = s.editingTripId
        val isDaily = s.tripTypeDaily
        val photoUrls = s.photoUrlsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val priceObj = buildPriceDetails(s)
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveError = null) }
            if (editId.isNullOrBlank()) {
                val body = CreateTripRequestDto(
                    diveCenterId = centerId,
                    tripType = if (isDaily) "daily" else "safari",
                    country = s.country.trim(),
                    region = s.region.trim().ifBlank { null },
                    startDate = s.startDate.trim(),
                    endDate = s.endDate.trim(),
                    description = s.description.trim(),
                    totalSpots = spots,
                    minimumCertificationLevel = s.minimumCertificationLevel.trim().ifBlank { null },
                    minimumDives = minDives,
                    nitroxAvailable = s.nitroxAvailable,
                    equipmentRentalAvailable = s.equipmentRentalAvailable,
                    hotelLabel = if (isDaily) s.hotelLabel.trim() else null,
                    yachtLabel = if (!isDaily) s.yachtLabel.trim() else null,
                    groupLeaderId = s.groupLeaderId,
                    programDays = programArr,
                    additionalExpenses = expensesArr,
                    priceDetails = priceObj,
                    photoUrls = photoUrls.ifEmpty { null },
                    availableCourseIds = s.selectedCourseIds.toList().ifEmpty { null },
                )
                runCatching { repo.createTrip(body) }
                    .onSuccess { res ->
                        _state.update { it.copy(saving = false, createdTripId = res.id) }
                    }
                    .onFailure { e ->
                        _state.update {
                            it.copy(saving = false, saveError = e.message ?: "Error")
                        }
                    }
            } else {
                val patch = UpdateTripRequestDto(
                    tripType = if (isDaily) "daily" else "safari",
                    country = s.country.trim(),
                    region = s.region.trim().ifBlank { null },
                    startDate = s.startDate.trim(),
                    endDate = s.endDate.trim(),
                    description = s.description.trim(),
                    totalSpots = spots,
                    minimumCertificationLevel = s.minimumCertificationLevel.trim().ifBlank { null },
                    minimumDives = minDives,
                    nitroxAvailable = s.nitroxAvailable,
                    equipmentRentalAvailable = s.equipmentRentalAvailable,
                    hotelLabel = if (isDaily) s.hotelLabel.trim() else null,
                    yachtLabel = if (!isDaily) s.yachtLabel.trim() else null,
                    groupLeaderId = s.groupLeaderId,
                    programDays = programArr,
                    additionalExpenses = expensesArr,
                    priceDetails = priceObj,
                    photoUrls = photoUrls.ifEmpty { null },
                    availableCourseIds = s.selectedCourseIds.toList().ifEmpty { null },
                )
                runCatching { repo.updateTrip(editId, patch) }
                    .onSuccess {
                        _state.update { it.copy(saving = false, saveSuccessPop = true) }
                    }
                    .onFailure { e ->
                        _state.update {
                            it.copy(saving = false, saveError = e.message ?: "Error")
                        }
                    }
            }
        }
    }

    companion object {
        fun factory(graph: AppGraph, editingTripId: String? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CreateTripViewModel(graph, TripsRepository(graph), editingTripId) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripRoute(graph: AppGraph, innerNav: NavController, editingTripId: String? = null) {
    val vm: CreateTripViewModel = viewModel(
        key = editingTripId ?: "trip_create",
        factory = CreateTripViewModel.factory(graph, editingTripId),
    )
    val state by vm.state.collectAsState()
    val isEdit = !state.editingTripId.isNullOrBlank()
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importUrlText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val pickTripPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12),
    ) { uris: List<Uri> ->
        vm.uploadPhotosFromGallery(context, uris)
    }

    state.roomDraft?.let { d ->
        AlertDialog(
            onDismissRequest = { vm.dismissRoomDraft() },
            title = {
                Text(
                    stringResource(
                        if (d.editingId == null) {
                            R.string.trip_create_add_room
                        } else {
                            R.string.trip_create_edit_room
                        },
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = d.roomType,
                        onValueChange = { vm.updateRoomDraft(roomType = it) },
                        label = { Text(stringResource(R.string.trip_create_dialog_room_type)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.roomCount,
                        onValueChange = { vm.updateRoomDraft(roomCount = it) },
                        label = { Text(stringResource(R.string.trip_create_dialog_room_count)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.divingPrice,
                        onValueChange = { vm.updateRoomDraft(divingPrice = it) },
                        label = { Text(stringResource(R.string.trip_create_dialog_diving_price)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.nonDivingPrice,
                        onValueChange = { vm.updateRoomDraft(nonDivingPrice = it) },
                        label = { Text(stringResource(R.string.trip_create_dialog_nondiving_price)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.saveRoomDraft() }) {
                    Text(stringResource(R.string.trip_create_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissRoomDraft() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    state.cabinDraft?.let { d ->
        AlertDialog(
            onDismissRequest = { vm.dismissCabinDraft() },
            title = {
                Text(
                    stringResource(
                        if (d.editingId == null) {
                            R.string.trip_create_add_cabin
                        } else {
                            R.string.trip_create_edit_cabin
                        },
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = d.cabinType,
                        onValueChange = { vm.updateCabinDraft(cabinType = it) },
                        label = { Text(stringResource(R.string.trip_create_dialog_cabin_type)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.cabinCount,
                        onValueChange = { vm.updateCabinDraft(cabinCount = it) },
                        label = { Text(stringResource(R.string.trip_create_dialog_cabin_count)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.divingPrice,
                        onValueChange = { vm.updateCabinDraft(divingPrice = it) },
                        label = { Text(stringResource(R.string.trip_create_dialog_diving_price)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.nonDivingPrice,
                        onValueChange = { vm.updateCabinDraft(nonDivingPrice = it) },
                        label = { Text(stringResource(R.string.trip_create_dialog_nondiving_price)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.saveCabinDraft() }) {
                    Text(stringResource(R.string.trip_create_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissCabinDraft() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    state.programDayDraft?.let { d ->
        val progScroll = rememberScrollState()
        AlertDialog(
            onDismissRequest = { vm.dismissProgramDayDraft() },
            title = {
                Text(
                    stringResource(
                        if (d.editingId == null) {
                            R.string.trip_create_add_day
                        } else {
                            R.string.trip_create_edit_day
                        },
                    ),
                )
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(progScroll),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = d.dateYmd,
                        onValueChange = { vm.updateProgramDayDraft(dateYmd = it) },
                        label = { Text(stringResource(R.string.trip_create_program_dialog_date)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.description,
                        onValueChange = { vm.updateProgramDayDraft(description = it) },
                        label = { Text(stringResource(R.string.trip_create_program_dialog_desc)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.trip_create_activities_header),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    d.activities.forEachIndexed { idx, a ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            ),
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = { vm.removeProgramActivityDraft(idx) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                                    }
                                }
                                OutlinedTextField(
                                    value = a.time,
                                    onValueChange = { vm.updateProgramActivityDraft(idx, time = it) },
                                    label = { Text(stringResource(R.string.trip_create_activity_time)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = a.activity,
                                    onValueChange = { vm.updateProgramActivityDraft(idx, activity = it) },
                                    label = { Text(stringResource(R.string.trip_create_activity_title)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = a.notes,
                                    onValueChange = { vm.updateProgramActivityDraft(idx, notes = it) },
                                    label = { Text(stringResource(R.string.trip_create_activity_notes)) },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = a.diveSiteId,
                                    onValueChange = { vm.updateProgramActivityDraft(idx, diveSiteId = it) },
                                    label = { Text(stringResource(R.string.trip_create_activity_dive_site_id)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = a.diveCenterId,
                                    onValueChange = { vm.updateProgramActivityDraft(idx, diveCenterId = it) },
                                    label = { Text(stringResource(R.string.trip_create_activity_dive_center_id)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    TextButton(onClick = { vm.addProgramActivityDraft() }) {
                        Text(stringResource(R.string.trip_create_add_activity))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.saveProgramDayDraft() }) {
                    Text(stringResource(R.string.trip_create_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissProgramDayDraft() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    state.expenseDraft?.let { d ->
        AlertDialog(
            onDismissRequest = { vm.dismissExpenseDraft() },
            title = {
                Text(
                    stringResource(
                        if (d.editingId == null) {
                            R.string.trip_create_add_expense
                        } else {
                            R.string.trip_create_edit_expense
                        },
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.trip_create_expense_type_label), style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TRIP_EXPENSE_TYPES.forEach { code ->
                            FilterChip(
                                selected = d.expenseType == code,
                                onClick = { vm.updateExpenseDraft(expenseType = code) },
                                label = { Text(createTripExpenseTypeLabel(code)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = d.description,
                        onValueChange = { vm.updateExpenseDraft(description = it) },
                        label = { Text(stringResource(R.string.trip_create_expense_description)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.cost,
                        onValueChange = { vm.updateExpenseDraft(cost = it) },
                        label = { Text(stringResource(R.string.trip_create_expense_cost)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.currency,
                        onValueChange = { vm.updateExpenseDraft(currency = it) },
                        label = { Text(stringResource(R.string.trip_create_price_currency)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.saveExpenseDraft() }) {
                    Text(stringResource(R.string.trip_create_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissExpenseDraft() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.importingTrip) {
                    showImportDialog = false
                    importUrlText = ""
                    vm.clearImportError()
                }
            },
            title = { Text(stringResource(R.string.trip_create_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.trip_create_import_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = importUrlText,
                        onValueChange = {
                            importUrlText = it
                            vm.clearImportError()
                        },
                        label = { Text(stringResource(R.string.trip_create_import_hint)) },
                        singleLine = false,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.importingTrip,
                    )
                    state.importError?.takeIf { it.isNotBlank() }?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.importingTrip) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.importTripFromWebsite(importUrlText)
                    },
                    enabled = importUrlText.trim().isNotEmpty() && !state.importingTrip,
                ) {
                    Text(stringResource(R.string.trip_create_import_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        importUrlText = ""
                        vm.clearImportError()
                    },
                    enabled = !state.importingTrip,
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    LaunchedEffect(state.createdTripId) {
        val id = state.createdTripId ?: return@LaunchedEffect
        showImportDialog = false
        importUrlText = ""
        vm.clearImportError()
        innerNav.navigate(InnerRoutes.tripDetail(id)) {
            popUpTo(InnerRoutes.TripCreate) { inclusive = true }
        }
        vm.consumeCreatedTripId()
    }

    LaunchedEffect(state.saveSuccessPop) {
        if (state.saveSuccessPop) {
            innerNav.popBackStack()
            vm.consumeSaveSuccessPop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEdit) R.string.trip_edit_title else R.string.trip_create_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (!isEdit && state.selectedCenterId != null) {
                        TextButton(
                            onClick = { showImportDialog = true },
                            enabled = !state.importingTrip,
                        ) {
                            Text(stringResource(R.string.trip_create_import_short))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loadingCenters -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            state.centersError != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.centersError ?: stringResource(R.string.common_error))
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { innerNav.popBackStack() }) {
                    Text(stringResource(R.string.common_back))
                }
            }
            state.tripLoadError != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.tripLoadError ?: stringResource(R.string.common_error))
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { innerNav.popBackStack() }) {
                    Text(stringResource(R.string.common_back))
                }
            }
            state.centers.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.trip_create_no_centers),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { innerNav.popBackStack() }) {
                    Text(stringResource(R.string.common_back))
                }
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.trip_create_center_label), style = MaterialTheme.typography.labelLarge)
                if (isEdit) {
                    Text(
                        stringResource(R.string.trip_edit_center_locked_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.centers.forEach { c ->
                        FilterChip(
                            selected = state.selectedCenterId == c.id,
                            onClick = { if (!isEdit) vm.setSelectedCenter(c.id) },
                            enabled = !isEdit,
                            label = { Text(c.name) },
                        )
                    }
                }
                Text(stringResource(R.string.trip_create_type_label), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.tripTypeDaily,
                        onClick = { vm.setTripTypeDaily(true) },
                        label = { Text(stringResource(R.string.trip_create_type_daily)) },
                    )
                    FilterChip(
                        selected = !state.tripTypeDaily,
                        onClick = { vm.setTripTypeDaily(false) },
                        label = { Text(stringResource(R.string.trip_create_type_safari)) },
                    )
                }
                if (state.tripTypeDaily) {
                    OutlinedTextField(
                        value = state.hotelLabel,
                        onValueChange = vm::setHotelLabel,
                        label = { Text(stringResource(R.string.trip_create_hotel_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = state.yachtLabel,
                        onValueChange = vm::setYachtLabel,
                        label = { Text(stringResource(R.string.trip_create_yacht_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.loadingCountries) {
                    Text(
                        stringResource(R.string.trip_create_countries_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.countries.isNotEmpty()) {
                    val countryFiltered = remember(state.country, state.countries) {
                        val q = state.country.trim()
                        state.countries.asSequence()
                            .filter { c -> q.isEmpty() || c.contains(q, ignoreCase = true) }
                            .take(120)
                            .toList()
                    }
                    OutlinedTextField(
                        value = state.country,
                        onValueChange = {
                            vm.setCountry(it)
                            countryMenuExpanded = true
                        },
                        label = { Text(stringResource(R.string.trip_create_country)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (countryMenuExpanded && countryFiltered.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                countryFiltered.forEach { c ->
                                    TextButton(
                                        onClick = {
                                            vm.setCountry(c)
                                            countryMenuExpanded = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(c, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = state.country,
                        onValueChange = vm::setCountry,
                        label = { Text(stringResource(R.string.trip_create_country)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = state.region,
                    onValueChange = vm::setRegion,
                    label = { Text(stringResource(R.string.trip_create_region)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.startDate,
                    onValueChange = vm::setStartDate,
                    label = { Text(stringResource(R.string.trip_create_start_date)) },
                    placeholder = { Text("2026-06-01") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.endDate,
                    onValueChange = vm::setEndDate,
                    label = { Text(stringResource(R.string.trip_create_end_date)) },
                    placeholder = { Text("2026-06-07") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = vm::setDescription,
                    label = { Text(stringResource(R.string.trip_create_description)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.totalSpots,
                    onValueChange = vm::setTotalSpots,
                    label = { Text(stringResource(R.string.trip_create_total_spots)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.minimumCertificationLevel,
                    onValueChange = vm::setMinCert,
                    label = { Text(stringResource(R.string.trip_create_min_cert)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.minimumDives,
                    onValueChange = vm::setMinDives,
                    label = { Text(stringResource(R.string.trip_create_min_dives)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.nitroxAvailable,
                        onClick = { vm.setNitrox(!state.nitroxAvailable) },
                        label = { Text(stringResource(R.string.trip_create_nitrox)) },
                    )
                    FilterChip(
                        selected = state.equipmentRentalAvailable,
                        onClick = { vm.setEquipmentRental(!state.equipmentRentalAvailable) },
                        label = { Text(stringResource(R.string.trip_create_equipment)) },
                    )
                }
                Text(stringResource(R.string.trip_create_group_leader), style = MaterialTheme.typography.labelLarge)
                when {
                    state.loadingInstructors -> CircularProgressIndicator(Modifier.size(24.dp))
                    state.instructors.isEmpty() -> Text(
                        stringResource(R.string.trip_create_no_instructors),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.groupLeaderId == null,
                                onClick = { vm.setGroupLeader(null) },
                                label = { Text(stringResource(R.string.trip_create_group_leader_none)) },
                            )
                            state.instructors.forEach { u ->
                                FilterChip(
                                    selected = state.groupLeaderId == u.id,
                                    onClick = { vm.setGroupLeader(u.id) },
                                    label = { Text(u.displayName()) },
                                )
                            }
                        }
                    }
                }
                Text(stringResource(R.string.trip_create_courses_label), style = MaterialTheme.typography.labelLarge)
                when {
                    state.loadingCourses -> CircularProgressIndicator(Modifier.size(24.dp))
                    state.courses.isEmpty() && state.coursesLoadError == null ->
                        Text(
                            stringResource(R.string.trip_create_courses_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.courses.forEach { c ->
                                FilterChip(
                                    selected = state.selectedCourseIds.contains(c.id),
                                    onClick = { vm.toggleCourseSelection(c.id) },
                                    label = { Text(c.name) },
                                )
                            }
                        }
                    }
                }
                state.coursesLoadError?.takeIf { it.isNotBlank() }?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.tripTypeDaily) {
                    Text(stringResource(R.string.trip_create_room_prices), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { vm.openNewRoomDraft() }) {
                        Text(stringResource(R.string.trip_create_add_room))
                    }
                    val spotsCap = state.totalSpots.trim().toIntOrNull() ?: 0
                    val roomsTotal = state.roomPrices.sumOf { it.roomCount }
                    if (spotsCap > 0 && roomsTotal > spotsCap) {
                        Text(
                            stringResource(R.string.trip_create_room_count_warning, roomsTotal, spotsCap),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    state.roomPrices.forEach { r ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.roomType, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(
                                            R.string.trip_create_room_line,
                                            r.roomCount,
                                            r.divingPrice,
                                            r.nonDivingPrice,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.openEditRoomDraft(r) }) {
                                    Text(stringResource(R.string.trip_create_edit))
                                }
                                IconButton(onClick = { vm.removeRoomPrice(r.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                                }
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.trip_create_cabin_prices), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { vm.openNewCabinDraft() }) {
                        Text(stringResource(R.string.trip_create_add_cabin))
                    }
                    val spotsCap = state.totalSpots.trim().toIntOrNull() ?: 0
                    val cabinsTotal = state.cabinPrices.sumOf { it.cabinCount }
                    if (spotsCap > 0 && cabinsTotal > spotsCap) {
                        Text(
                            stringResource(R.string.trip_create_cabin_count_warning, cabinsTotal, spotsCap),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    state.cabinPrices.forEach { c ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.cabinType, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(
                                            R.string.trip_create_cabin_line,
                                            c.cabinCount,
                                            c.divingPrice,
                                            c.nonDivingPrice,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.openEditCabinDraft(c) }) {
                                    Text(stringResource(R.string.trip_create_edit))
                                }
                                IconButton(onClick = { vm.removeCabinPrice(c.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = state.priceCurrency,
                    onValueChange = vm::setPriceCurrency,
                    label = { Text(stringResource(R.string.trip_create_price_currency)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.trip_create_program_section), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.trip_create_program_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { vm.openNewProgramDay() }) {
                    Text(stringResource(R.string.trip_create_add_day))
                }
                state.programDays.forEach { day ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(day.dateYmd, style = MaterialTheme.typography.titleSmall)
                                if (day.description.isNotBlank()) {
                                    Text(
                                        day.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    stringResource(R.string.trip_create_program_activity_count, day.activities.size),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { vm.openEditProgramDay(day) }) {
                                Text(stringResource(R.string.trip_create_edit))
                            }
                            IconButton(onClick = { vm.removeProgramDay(day.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                            }
                        }
                    }
                }
                Text(stringResource(R.string.trip_create_expenses_section), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { vm.openNewExpenseDraft() }) {
                    Text(stringResource(R.string.trip_create_add_expense))
                }
                state.additionalExpenses.forEach { ex ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    createTripExpenseTypeLabel(ex.expenseType),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(
                                        R.string.trip_create_expense_line,
                                        ex.description.ifBlank { "—" },
                                        String.format(Locale.US, "%.2f", ex.cost),
                                        ex.currency,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { vm.openEditExpenseDraft(ex) }) {
                                Text(stringResource(R.string.trip_create_edit))
                            }
                            IconButton(onClick = { vm.removeExpense(ex.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                            }
                        }
                    }
                }
                Text(stringResource(R.string.trip_create_photo_section), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.trip_create_photo_urls_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            pickTripPhotos.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = !state.photoUploadInProgress,
                    ) {
                        Text(stringResource(R.string.trip_create_photo_pick))
                    }
                    if (state.photoUploadInProgress) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                state.photoUploadError?.takeIf { it.isNotBlank() }?.let { pe ->
                    Text(pe, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.clearPhotoUploadError() }) {
                        Text(stringResource(R.string.common_close))
                    }
                }
                val photoLines = remember(state.photoUrlsText) {
                    state.photoUrlsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                }
                if (photoLines.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photoLines) { raw ->
                            AsyncImage(
                                model = absoluteMediaUrl(state.imageApiRoot, raw),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(88.dp)
                                    .height(72.dp),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.photoUrlsText,
                    onValueChange = vm::setPhotoUrlsText,
                    label = { Text(stringResource(R.string.trip_create_photo_urls)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.saveError?.let { key ->
                    val msg = when (key) {
                        "no_center" -> stringResource(R.string.trip_create_err_no_center)
                        "country" -> stringResource(R.string.trip_create_err_country)
                        "dates_format" -> stringResource(R.string.trip_create_err_dates_format)
                        "dates_order" -> stringResource(R.string.trip_create_err_dates_order)
                        "description" -> stringResource(R.string.trip_create_err_description)
                        "spots" -> stringResource(R.string.trip_create_err_spots)
                        "hotel" -> stringResource(R.string.trip_create_err_hotel)
                        "yacht" -> stringResource(R.string.trip_create_err_yacht)
                        "program_dates" -> stringResource(R.string.trip_create_err_program_dates)
                        "expense_form" -> stringResource(R.string.trip_create_err_expense_form)
                        "room_form" -> stringResource(R.string.trip_create_err_room_form)
                        "cabin_form" -> stringResource(R.string.trip_create_err_cabin_form)
                        else -> key
                    }
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.clearSaveError() }) {
                        Text(stringResource(R.string.common_close))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.submit() },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            stringResource(
                                if (isEdit) R.string.trip_edit_submit else R.string.trip_create_submit,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun createTripExpenseTypeLabel(code: String): String = when (code) {
    "flight" -> stringResource(R.string.trip_expense_type_flight)
    "transfer" -> stringResource(R.string.trip_expense_type_transfer)
    "nutrition" -> stringResource(R.string.trip_expense_type_nutrition)
    "reserve" -> stringResource(R.string.trip_expense_type_reserve)
    "other" -> stringResource(R.string.trip_expense_type_other)
    else -> code
}
