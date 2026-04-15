package com.divehub.app.ui.achievements

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.LogbookRepository
import com.divehub.app.data.remote.dto.DiveLogDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

enum class AchievementIconKind {
    Waves,
    Depth,
    Night,
    Camera,
    Timer,
    Logbook,
}

data class AchievementUi(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val iconKind: AchievementIconKind,
)

data class AchievementsUiState(
    val loading: Boolean = true,
    val achievements: List<AchievementUi> = emptyList(),
    val error: String? = null,
)

class AchievementsViewModel(
    application: Application,
    private val graph: AppGraph,
) : AndroidViewModel(application) {

    private val logsRepo = LogbookRepository(graph)
    private val authRepo = AuthRepository(graph)
    private val _state = MutableStateFlow(AchievementsUiState())
    val state: StateFlow<AchievementsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            val res = getApplication<Application>().resources
            runCatching {
                val logs = logsRepo.list()
                val userTotal = authRepo.cachedUser()?.totalDives
                Triple(logs, userTotal, res)
            }
                .onSuccess { (logs, userTotal, res) ->
                    val list = buildAchievements(res, logs, userTotal)
                    _state.value = _state.value.copy(
                        loading = false,
                        error = null,
                        achievements = list,
                    )
                }
                .onFailure { e ->
                    _state.value = prev.copy(
                        loading = false,
                        error = e.message ?: "Error",
                    )
                }
        }
    }

    private fun buildAchievements(
        res: android.content.res.Resources,
        logs: List<DiveLogDto>,
        userTotalDives: Int?,
    ): List<AchievementUi> {
        val count = max(logs.size, userTotalDives ?: 0)
        val totalMinutes = logs.sumOf { it.duration }
        val maxDepth = logs.maxOfOrNull { it.maxDepth } ?: 0.0
        val anyPhoto = logs.any { !it.photoUrls.isNullOrEmpty() }
        val nightish = logs.any {
            val t = it.diveType?.lowercase().orEmpty()
            t.contains("night") || t.contains("ноч")
        }

        return listOf(
            AchievementUi(
                id = "first",
                title = res.getString(R.string.achievement_first_title),
                description = res.getString(R.string.achievement_first_desc),
                unlocked = count >= 1,
                iconKind = AchievementIconKind.Waves,
            ),
            AchievementUi(
                id = "ten",
                title = res.getString(R.string.achievement_ten_title),
                description = res.getString(R.string.achievement_ten_desc),
                unlocked = count >= 10,
                iconKind = AchievementIconKind.Logbook,
            ),
            AchievementUi(
                id = "deep30",
                title = res.getString(R.string.achievement_deep30_title),
                description = res.getString(R.string.achievement_deep30_desc),
                unlocked = maxDepth >= 30,
                iconKind = AchievementIconKind.Depth,
            ),
            AchievementUi(
                id = "deep40",
                title = res.getString(R.string.achievement_deep40_title),
                description = res.getString(R.string.achievement_deep40_desc),
                unlocked = maxDepth >= 40,
                iconKind = AchievementIconKind.Depth,
            ),
            AchievementUi(
                id = "bottom50h",
                title = res.getString(R.string.achievement_time_title),
                description = res.getString(R.string.achievement_time_desc),
                unlocked = totalMinutes >= 50 * 60,
                iconKind = AchievementIconKind.Timer,
            ),
            AchievementUi(
                id = "photos",
                title = res.getString(R.string.achievement_photo_title),
                description = res.getString(R.string.achievement_photo_desc),
                unlocked = anyPhoto,
                iconKind = AchievementIconKind.Camera,
            ),
            AchievementUi(
                id = "night",
                title = res.getString(R.string.achievement_night_title),
                description = res.getString(R.string.achievement_night_desc),
                unlocked = nightish,
                iconKind = AchievementIconKind.Night,
            ),
        )
    }

    companion object {
        fun factory(graph: AppGraph, app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AchievementsViewModel(app, graph) as T
            }
        }
    }
}
