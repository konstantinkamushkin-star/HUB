package com.divehub.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AdminAffiliatedSitesRepository
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.data.remote.dto.DiveCenterBriefDto
import com.divehub.app.data.remote.dto.ExploreDiveSite
import com.divehub.app.data.remote.dto.ExploreItemKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminAffiliatedSitesUiState(
    val loading: Boolean = true,
    val centers: List<DiveCenterBriefDto> = emptyList(),
    val sites: List<ExploreDiveSite> = emptyList(),
    val error: String? = null,
)

class AdminAffiliatedSitesViewModel(
    private val graph: AppGraph,
    private val exploreRepo: ExploreRepository,
    private val tripsRepo: TripsRepository,
    private val repo: AdminAffiliatedSitesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminAffiliatedSitesUiState())
    val state: StateFlow<AdminAffiliatedSitesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            runCatching {
                val lang = graph.tokenStore.getAppLanguageTag().ifBlank { "en" }
                val centers = tripsRepo.listManagedDiveCenters()
                val sites = exploreRepo.getDiveSites(language = lang, page = 1, limit = 200)
                centers to sites.filter { it.kind == ExploreItemKind.DIVE_SITE }
            }
                .onSuccess { (centers, sites) ->
                    _state.value = _state.value.copy(loading = false, error = null, centers = centers, sites = sites)
                }
                .onFailure { e ->
                    _state.value = prev.copy(loading = false, error = e.message ?: "Error")
                }
        }
    }

    suspend fun loadCenterSites(centerId: String): Set<String> = repo.getCenterSites(centerId).toSet()

    fun saveCenterSites(centerId: String, siteIds: Set<String>) {
        viewModelScope.launch {
            repo.saveCenterSites(centerId, siteIds.toList())
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdminAffiliatedSitesViewModel(
                    graph = graph,
                    exploreRepo = ExploreRepository(graph),
                    tripsRepo = TripsRepository(graph),
                    repo = AdminAffiliatedSitesRepository(graph),
                ) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAffiliatedSitesRoute(graph: AppGraph, innerNav: NavController) {
    val vm: AdminAffiliatedSitesViewModel = viewModel(factory = AdminAffiliatedSitesViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var selectedCenterId by remember { mutableStateOf<String?>(null) }
    var centerExpanded by remember { mutableStateOf(false) }
    val selectedSiteIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(state.centers) {
        if (selectedCenterId == null) {
            selectedCenterId = state.centers.firstOrNull()?.id
        }
    }

    LaunchedEffect(selectedCenterId) {
        val centerId = selectedCenterId ?: return@LaunchedEffect
        selectedSiteIds.clear()
        vm.loadCenterSites(centerId).forEach { selectedSiteIds[it] = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_affiliated_sites_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh_list))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.sites.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null && state.sites.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.common_retry)) }
            }
            else -> PullToRefreshBox(
                isRefreshing = state.loading && state.sites.isNotEmpty(),
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.admin_affiliated_sites_center_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = centerExpanded,
                            onExpandedChange = { centerExpanded = !centerExpanded },
                        ) {
                            OutlinedTextField(
                                value = state.centers.firstOrNull { it.id == selectedCenterId }?.name.orEmpty(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.admin_affiliated_sites_select_center)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = centerExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            )
                            DropdownMenu(
                                expanded = centerExpanded,
                                onDismissRequest = { centerExpanded = false },
                            ) {
                                state.centers.forEach { center ->
                                    DropdownMenuItem(
                                        text = { Text(center.name) },
                                        onClick = {
                                            selectedCenterId = center.id
                                            centerExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (state.error != null) {
                        item {
                            Text(
                                state.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (selectedCenterId == null) {
                        item { Text(stringResource(R.string.admin_affiliated_sites_no_centers)) }
                    } else {
                        items(state.sites, key = { it.id }) { site ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selectedSiteIds[site.id] == true,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedSiteIds[site.id] = true else selectedSiteIds.remove(site.id)
                                        vm.saveCenterSites(selectedCenterId!!, selectedSiteIds.keys)
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(site.name, style = MaterialTheme.typography.bodyLarge)
                                    val sub = listOfNotNull(
                                        site.region.takeIf { it.isNotBlank() },
                                        site.country.takeIf { it.isNotBlank() },
                                    ).joinToString(", ")
                                    if (sub.isNotBlank()) {
                                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

