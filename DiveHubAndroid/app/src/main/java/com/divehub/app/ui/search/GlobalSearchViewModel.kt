package com.divehub.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.SocialRepository
import com.divehub.app.data.remote.dto.ExploreDiveSite
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
    private val exploreRepo: ExploreRepository,
    private val socialRepo: SocialRepository,
) : ViewModel() {

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
            runCatching {
                val allSites = exploreRepo.getDiveSites(language = "en", page = 1, limit = 120)
                val sites = allSites.filter { site ->
                    site.name.lowercase(Locale.getDefault()).contains(qLower) ||
                        (site.region?.lowercase()?.contains(qLower) == true) ||
                        (site.country?.lowercase()?.contains(qLower) == true)
                }.take(40)
                val users = runCatching { socialRepo.searchUsers(raw) }.getOrElse { emptyList() }
                Pair(sites, users)
            }
                .onSuccess { (sites, users) ->
                    _state.value = _state.value.copy(loading = false, sites = sites, users = users)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Error")
                }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GlobalSearchViewModel(
                    ExploreRepository(graph),
                    SocialRepository(graph),
                ) as T
            }
        }
    }
}
