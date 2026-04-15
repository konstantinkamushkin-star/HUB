package com.divehub.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.remote.dto.ExploreDiveSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ExploreViewMode { LIST, MAP }
enum class ExploreCategory { DIVE_SITES, DIVE_CENTERS, SHOPS }
enum class ExploreSort { RELEVANCE, RATING_DESC, DEPTH_ASC, NAME_ASC }

data class ExploreUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val allSites: List<ExploreDiveSite> = emptyList(),
    val filteredSites: List<ExploreDiveSite> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: ExploreCategory = ExploreCategory.DIVE_SITES,
    val selectedDiveType: String? = null,
    val selectedDifficulty: String? = null,
    val viewMode: ExploreViewMode = ExploreViewMode.MAP,
    val selectedSort: ExploreSort = ExploreSort.RELEVANCE,
)

class ExploreViewModel(
    private val repo: ExploreRepository,
    private val graph: AppGraph,
) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    private suspend fun resolvedLanguage(): String =
        graph.tokenStore.getAppLanguageTag().ifBlank { "en" }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val lang = resolvedLanguage()
                when (_state.value.selectedCategory) {
                    ExploreCategory.DIVE_SITES -> repo.getDiveSites(language = lang, page = 1, limit = 120)
                    // Backend popular endpoints validate limit max 100
                    ExploreCategory.DIVE_CENTERS -> repo.getDiveCenters(limit = 100)
                    ExploreCategory.SHOPS -> repo.getShops(limit = 100)
                }
            }
                .onSuccess { sites ->
                    _state.value = _state.value.copy(
                        loading = false,
                        allSites = sites,
                        filteredSites = applyFilters(
                            sites = sites,
                            query = _state.value.searchQuery,
                            diveType = _state.value.selectedDiveType,
                            difficulty = _state.value.selectedDifficulty,
                            sort = _state.value.selectedSort,
                        ),
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Load error")
                }
        }
    }

    fun setCategory(category: ExploreCategory) {
        if (_state.value.selectedCategory == category) return
        _state.value = _state.value.copy(
            selectedCategory = category,
            selectedDiveType = null,
            selectedDifficulty = null,
            searchQuery = "",
            filteredSites = emptyList(),
            allSites = emptyList(),
        )
        refresh()
    }

    fun setSearch(query: String) {
        val all = _state.value.allSites
        _state.value = _state.value.copy(
            searchQuery = query,
            filteredSites = applyFilters(
                sites = all,
                query = query,
                diveType = _state.value.selectedDiveType,
                difficulty = _state.value.selectedDifficulty,
                sort = _state.value.selectedSort,
            ),
        )
    }

    fun setDiveTypeFilter(value: String?) {
        val all = _state.value.allSites
        _state.value = _state.value.copy(
            selectedDiveType = value,
            filteredSites = applyFilters(
                sites = all,
                query = _state.value.searchQuery,
                diveType = value,
                difficulty = _state.value.selectedDifficulty,
                sort = _state.value.selectedSort,
            ),
        )
    }

    fun setDifficultyFilter(value: String?) {
        val all = _state.value.allSites
        _state.value = _state.value.copy(
            selectedDifficulty = value,
            filteredSites = applyFilters(
                sites = all,
                query = _state.value.searchQuery,
                diveType = _state.value.selectedDiveType,
                difficulty = value,
                sort = _state.value.selectedSort,
            ),
        )
    }

    fun setSort(sort: ExploreSort) {
        val all = _state.value.allSites
        _state.value = _state.value.copy(
            selectedSort = sort,
            filteredSites = applyFilters(
                sites = all,
                query = _state.value.searchQuery,
                diveType = _state.value.selectedDiveType,
                difficulty = _state.value.selectedDifficulty,
                sort = sort,
            ),
        )
    }

    fun clearFilters() {
        val all = _state.value.allSites
        _state.value = _state.value.copy(
            selectedDiveType = null,
            selectedDifficulty = null,
            searchQuery = "",
            selectedSort = ExploreSort.RELEVANCE,
            filteredSites = applyFilters(
                sites = all,
                query = "",
                diveType = null,
                difficulty = null,
                sort = ExploreSort.RELEVANCE,
            ),
        )
    }

    fun setViewMode(mode: ExploreViewMode) {
        _state.value = _state.value.copy(viewMode = mode)
    }

    private fun applyFilters(
        sites: List<ExploreDiveSite>,
        query: String,
        diveType: String?,
        difficulty: String?,
        sort: ExploreSort,
    ): List<ExploreDiveSite> {
        var out = sites
        if (!diveType.isNullOrBlank()) {
            out = out.filter { it.diveType.equals(diveType, ignoreCase = true) }
        }
        if (!difficulty.isNullOrBlank()) {
            out = out.filter { it.difficulty.equals(difficulty, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            val q = query.trim()
            out = out.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.description.contains(q, ignoreCase = true) ||
                    it.country.contains(q, ignoreCase = true) ||
                    it.region.contains(q, ignoreCase = true)
            }
        }
        return when (sort) {
            ExploreSort.RELEVANCE -> out
            ExploreSort.RATING_DESC -> out.sortedByDescending { it.rating }
            ExploreSort.DEPTH_ASC -> out.sortedBy { it.depthMax }
            ExploreSort.NAME_ASC -> out.sortedBy { it.name.lowercase() }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ExploreViewModel(ExploreRepository(graph), graph) as T
            }
        }
    }
}
