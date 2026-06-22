package com.divehub.app.ui.logbook

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.FeedRepository
import com.divehub.app.data.LogbookRepository
import com.divehub.app.data.remote.dto.DiveLogDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

data class LogbookStats(
    val totalDives: Int = 0,
    val totalBottomTime: Int = 0,
    val deepestDive: Double = 0.0,
)

enum class LogbookSortOption {
    NEWEST_FIRST,
    OLDEST_FIRST,
    DEPTH,
    DURATION,
    ALPHABET,
}

data class LogbookUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val logs: List<DiveLogDto> = emptyList(),
    val searchText: String = "",
    val sortOption: LogbookSortOption = LogbookSortOption.NEWEST_FIRST,
    val stats: LogbookStats = LogbookStats(),
    val imageApiRoot: String = "",
    val displayedLogs: List<DiveLogDto> = emptyList(),
)

class LogbookViewModel(
    private val graph: AppGraph,
    private val repo: LogbookRepository,
    private val feedRepo: FeedRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LogbookUiState())
    val state: StateFlow<LogbookUiState> = _state.asStateFlow()

    init { refresh() }

    fun setSearchText(text: String) {
        _state.update { it.copy(searchText = text, displayedLogs = computeDisplayed(it.logs, text, it.sortOption)) }
    }

    fun setSortOption(option: LogbookSortOption) {
        _state.update { it.copy(sortOption = option, displayedLogs = computeDisplayed(it.logs, it.searchText, option)) }
    }

    private fun computeDisplayed(
        logs: List<DiveLogDto>,
        searchText: String,
        sortOption: LogbookSortOption,
    ): List<DiveLogDto> {
        val q = searchText.trim().lowercase(Locale.ROOT)
        val filtered = if (q.isEmpty()) {
            logs
        } else {
            logs.filter { log ->
                val haystack = listOfNotNull(
                    log.locationName,
                    log.notes,
                    log.current,
                    log.diveType,
                    log.fishSpecies?.joinToString(" "),
                ).joinToString(" ").lowercase(Locale.ROOT)
                haystack.contains(q)
            }
        }
        return when (sortOption) {
            LogbookSortOption.NEWEST_FIRST -> filtered.sortedByDescending { it.date }
            LogbookSortOption.OLDEST_FIRST -> filtered.sortedBy { it.date }
            LogbookSortOption.DEPTH -> filtered.sortedByDescending { it.maxDepth }
            LogbookSortOption.DURATION -> filtered.sortedByDescending { it.duration }
            LogbookSortOption.ALPHABET -> filtered.sortedBy { log -> displayTitle(log).lowercase(Locale.ROOT) }
        }
    }

    fun displayTitle(log: DiveLogDto): String {
        val name = log.locationName?.trim().orEmpty()
        if (name.isNotEmpty()) return name
        return "Dive 1"
    }

    fun refresh() {
        viewModelScope.launch {
            val imgRoot = graph.tokenStore.getRootBaseUrl()
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null, imageApiRoot = imgRoot)
            runCatching { repo.list() }
                .onSuccess { logs ->
                    val sorted = logs.sortedByDescending { it.date }
                    _state.value = prev.copy(
                        loading = false,
                        error = null,
                        logs = sorted,
                        displayedLogs = computeDisplayed(sorted, prev.searchText, prev.sortOption),
                        stats = LogbookStats(
                            totalDives = sorted.size,
                            totalBottomTime = sorted.sumOf { it.duration },
                            deepestDive = sorted.maxOfOrNull { it.maxDepth } ?: 0.0,
                        ),
                        imageApiRoot = imgRoot,
                    )
                }
                .onFailure { e ->
                    _state.value = prev.copy(
                        loading = false,
                        error = e.message ?: "Load error",
                        imageApiRoot = imgRoot,
                    )
                }
        }
    }

    fun deleteLog(logId: String) {
        _state.update { s ->
            val updated = s.logs.filterNot { it.id == logId }
            s.copy(
                logs = updated,
                displayedLogs = computeDisplayed(updated, s.searchText, s.sortOption),
                stats = LogbookStats(
                    totalDives = updated.size,
                    totalBottomTime = updated.sumOf { it.duration },
                    deepestDive = updated.maxOfOrNull { it.maxDepth } ?: 0.0,
                ),
            )
        }
    }

    fun shareDiveToFeed(
        log: DiveLogDto,
        shareText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                feedRepo.create(
                    content = shareText.ifBlank { null },
                    type = "dive_log",
                    photos = log.photoUrls.orEmpty(),
                    diveLogId = log.id,
                )
            }.onSuccess { onSuccess() }
                .onFailure { e -> onError(e.message ?: "Share failed") }
        }
    }

    fun addDive(
        context: Context,
        date: LocalDate,
        durationMin: Int,
        maxDepth: Double,
        avgDepth: Double?,
        temp: Double?,
        visibility: Double?,
        current: String?,
        diveType: String?,
        notes: String?,
        photoUris: List<Uri>,
        startTime: String? = null,
        locationName: String? = null,
        diveSiteId: String? = null,
        diveCenterId: String? = null,
        fishSpecies: List<String> = emptyList(),
        publishToFeed: Boolean = false,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repo.create(
                    date = date,
                    durationMin = durationMin,
                    maxDepth = maxDepth,
                    avgDepth = avgDepth,
                    temp = temp,
                    visibility = visibility,
                    current = current,
                    diveType = diveType,
                    notes = notes,
                    photoUris = photoUris,
                    context = context,
                    startTime = startTime,
                    locationName = locationName,
                    diveSiteId = diveSiteId,
                    diveCenterId = diveCenterId,
                    fishSpecies = fishSpecies,
                    isPublished = publishToFeed,
                )
            }.onSuccess {
                refresh()
                onDone()
            }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LogbookViewModel(
                    graph,
                    LogbookRepository(graph),
                    FeedRepository(graph),
                ) as T
            }
        }
    }
}
