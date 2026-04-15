package com.divehub.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.SocialRepository
import com.divehub.app.data.remote.dto.ExploreDiveSite
import com.divehub.app.data.remote.dto.ExploreItemKind
import com.divehub.app.data.remote.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class GlobalSearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val sites: List<ExploreDiveSite> = emptyList(),
    val users: List<UserDto> = emptyList(),
    val error: String? = null,
    val hasSearched: Boolean = false,
)

class GlobalSearchViewModel(
    private val graph: AppGraph,
) : ViewModel() {
    private val exploreRepo = ExploreRepository(graph)
    private val socialRepo = SocialRepository(graph)

    private val _state = MutableStateFlow(GlobalSearchUiState())
    val state: StateFlow<GlobalSearchUiState> = _state.asStateFlow()

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun search() {
        val raw = _state.value.query.trim()
        if (raw.length < 2) {
            _state.value = _state.value.copy(
                error = null,
                hasSearched = true,
                sites = emptyList(),
                users = emptyList(),
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, hasSearched = true)
            val qLower = raw.lowercase(Locale.getDefault())
            try {
                val lang = graph.tokenStore.getAppLanguageTag().trim().ifBlank { "en" }
                val diveSites = exploreRepo.getDiveSites(language = lang, page = 1, limit = 120)
                val centers = exploreRepo.getDiveCenters(limit = 120)
                val shops = exploreRepo.getShops(limit = 120)
                val merged = diveSites + centers + shops
                val sites = merged
                    .filter { place -> placeMatchesQuery(place, qLower) }
                    .distinctBy { "${it.kind.name}_${it.id}" }
                    .take(40)
                val users = runCatching { socialRepo.searchUsers(raw) }.getOrElse { emptyList() }
                _state.value = _state.value.copy(loading = false, error = null, sites = sites, users = users)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Error")
            }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GlobalSearchViewModel(graph) as T
            }
        }

        private fun placeMatchesQuery(site: ExploreDiveSite, qLower: String): Boolean {
            if (site.name.lowercase(Locale.getDefault()).contains(qLower)) return true
            if (site.region.lowercase(Locale.getDefault()).contains(qLower)) return true
            if (site.country.lowercase(Locale.getDefault()).contains(qLower)) return true
            if (site.kind == ExploreItemKind.DIVE_SITE) {
                if (site.diveType.lowercase(Locale.getDefault()).contains(qLower)) return true
                if (site.difficulty.lowercase(Locale.getDefault()).contains(qLower)) return true
            }
            return false
        }
    }
}
