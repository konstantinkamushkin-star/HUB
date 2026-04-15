package com.divehub.app.ui.logbook

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.LogbookRepository
import com.divehub.app.data.remote.dto.DiveLogDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LogbookStats(
    val totalDives: Int = 0,
    val totalBottomTime: Int = 0,
    val deepestDive: Double = 0.0,
)

data class LogbookUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val logs: List<DiveLogDto> = emptyList(),
    val stats: LogbookStats = LogbookStats(),
    val imageApiRoot: String = "",
)

class LogbookViewModel(
    private val graph: AppGraph,
    private val repo: LogbookRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LogbookUiState())
    val state: StateFlow<LogbookUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val imgRoot = graph.tokenStore.getRootBaseUrl()
            _state.value = _state.value.copy(loading = true, error = null, imageApiRoot = imgRoot)
            runCatching { repo.list() }
                .onSuccess { logs ->
                    val sorted = logs.sortedByDescending { it.date }
                    _state.value = LogbookUiState(
                        loading = false,
                        error = null,
                        logs = sorted,
                        stats = LogbookStats(
                            totalDives = sorted.size,
                            totalBottomTime = sorted.sumOf { it.duration },
                            deepestDive = sorted.maxOfOrNull { it.maxDepth } ?: 0.0,
                        ),
                        imageApiRoot = imgRoot,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Load error",
                        imageApiRoot = imgRoot,
                    )
                }
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
                return LogbookViewModel(graph, LogbookRepository(graph)) as T
            }
        }
    }
}
