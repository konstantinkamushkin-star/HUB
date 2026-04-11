package com.divehub.app.ui.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.LogbookRepository
import com.divehub.app.data.remote.dto.DiveLogDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MilestoneUi(
    val title: String,
    val description: String,
    val dateLabel: String?,
)

data class DiveStatisticsUi(
    val totalDives: Int,
    val totalBottomTimeMinutes: Int,
    val deepestDiveMeters: Double,
    val longestDiveMinutes: Int,
    val averageDepthMeters: Double?,
    val diveByMonth: List<Pair<String, Int>>,
    val diveByType: List<Pair<String, Int>>,
    val milestones: List<MilestoneUi>,
)

data class StatisticsUiState(
    val loading: Boolean = true,
    val stats: DiveStatisticsUi? = null,
    val error: String? = null,
)

class StatisticsViewModel(
    application: Application,
    private val repo: LogbookRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(StatisticsUiState())
    val state: StateFlow<StatisticsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = StatisticsUiState(loading = true)
            val res = getApplication<Application>().resources
            runCatching { repo.list() }
                .onSuccess { logs ->
                    _state.value = StatisticsUiState(loading = false, stats = computeStats(res, logs))
                }
                .onFailure { e ->
                    _state.value = StatisticsUiState(loading = false, error = e.message ?: "Error")
                }
        }
    }

    private fun computeStats(res: android.content.res.Resources, logs: List<DiveLogDto>): DiveStatisticsUi {
        if (logs.isEmpty()) {
            return DiveStatisticsUi(
                totalDives = 0,
                totalBottomTimeMinutes = 0,
                deepestDiveMeters = 0.0,
                longestDiveMinutes = 0,
                averageDepthMeters = null,
                diveByMonth = emptyList(),
                diveByType = emptyList(),
                milestones = emptyList(),
            )
        }
        val sortedByDate = logs.sortedBy { it.date }
        val totalMin = logs.sumOf { it.duration }
        val deepest = logs.maxOf { it.maxDepth }
        val longest = logs.maxOf { it.duration }
        val avgDepth = logs.map { it.maxDepth }.average().takeIf { !it.isNaN() }

        val typeUnknown = res.getString(R.string.stats_type_unknown)
        val byMonth = logs
            .groupBy { it.date.take(7).ifBlank { "?" } }
            .mapValues { it.value.size }
            .entries
            .sortedBy { it.key }
            .map { it.key to it.value }

        val byType = logs
            .groupBy { (it.diveType?.trim()?.ifBlank { null }) ?: typeUnknown }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        val milestones = buildList {
            add(
                MilestoneUi(
                    title = res.getString(R.string.stats_milestone_first_title),
                    description = res.getString(R.string.stats_milestone_first_desc),
                    dateLabel = sortedByDate.first().date,
                ),
            )
            if (logs.size >= 10) {
                add(
                    MilestoneUi(
                        title = res.getString(R.string.stats_milestone_ten_title),
                        description = res.getString(R.string.stats_milestone_ten_desc),
                        dateLabel = null,
                    ),
                )
            }
            val deepestLog = logs.maxByOrNull { it.maxDepth }
            if (deepestLog != null && deepest > 0) {
                add(
                    MilestoneUi(
                        title = res.getString(R.string.stats_milestone_depth_title),
                        description = res.getString(R.string.stats_milestone_depth_desc, deepestLog.maxDepth.roundToInt()),
                        dateLabel = deepestLog.date,
                    ),
                )
            }
        }

        return DiveStatisticsUi(
            totalDives = logs.size,
            totalBottomTimeMinutes = totalMin,
            deepestDiveMeters = deepest,
            longestDiveMinutes = longest,
            averageDepthMeters = avgDepth,
            diveByMonth = byMonth,
            diveByType = byType,
            milestones = milestones,
        )
    }

    companion object {
        fun factory(graph: AppGraph, app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StatisticsViewModel(app, LogbookRepository(graph)) as T
            }
        }
    }
}
