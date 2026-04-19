package com.divehub.app.ui.admin

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.divehub.app.data.AdminShopsDraftsRepository
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.remote.dto.AdminShopDraftLocal
import com.divehub.app.data.remote.dto.ExploreDiveSite
import com.divehub.app.data.remote.dto.ExploreItemKind
import com.divehub.app.data.remote.dto.toExploreDraftShop
import com.divehub.app.ui.navigation.InnerRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

private sealed class AdminShopSheet {
    data object Add : AdminShopSheet()
    data class Edit(val shop: ExploreDiveSite) : AdminShopSheet()
}

data class AdminShopsUiState(
    val loading: Boolean = true,
    val remoteShops: List<ExploreDiveSite> = emptyList(),
    val draftShops: List<ExploreDiveSite> = emptyList(),
    val error: String? = null,
)

class AdminShopsViewModel(
    private val repo: ExploreRepository,
    private val draftsRepo: AdminShopsDraftsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminShopsUiState())
    val state: StateFlow<AdminShopsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            val drafts = runCatching { draftsRepo.loadDrafts().map { it.toExploreDraftShop() } }.getOrElse { emptyList() }
            runCatching { repo.getShops(limit = 200).filter { it.kind == ExploreItemKind.SHOP } }
                .onSuccess { remote ->
                    _state.value = AdminShopsUiState(
                        loading = false,
                        error = null,
                        remoteShops = remote,
                        draftShops = drafts,
                    )
                }
                .onFailure { e ->
                    _state.value = prev.copy(
                        loading = false,
                        error = e.message ?: "Error",
                        draftShops = drafts,
                    )
                }
        }
    }

    fun saveDraft(id: String?, name: String, country: String, region: String) {
        viewModelScope.launch {
            val nextId = id?.takeIf { it.startsWith(LOCAL_SHOP_DRAFT_PREFIX) }
                ?: "$LOCAL_SHOP_DRAFT_PREFIX${UUID.randomUUID()}"
            draftsRepo.upsertDraft(
                AdminShopDraftLocal(
                    id = nextId,
                    name = name.trim(),
                    country = country.trim(),
                    region = region.trim(),
                ),
            )
            refreshDraftsOnly()
        }
    }

    fun deleteDraft(id: String) {
        viewModelScope.launch {
            draftsRepo.deleteDraft(id)
            refreshDraftsOnly()
        }
    }

    private suspend fun refreshDraftsOnly() {
        val drafts = draftsRepo.loadDrafts().map { it.toExploreDraftShop() }
        _state.value = _state.value.copy(draftShops = drafts)
    }

    companion object {
        const val LOCAL_SHOP_DRAFT_PREFIX = "local-shop-"

        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdminShopsViewModel(
                    ExploreRepository(graph),
                    AdminShopsDraftsRepository(graph),
                ) as T
            }
        }
    }
}

private fun ExploreDiveSite.isLocalShopDraft(): Boolean =
    id.startsWith(AdminShopsViewModel.LOCAL_SHOP_DRAFT_PREFIX)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShopsManagementRoute(graph: AppGraph, innerNav: NavController) {
    val vm: AdminShopsViewModel = viewModel(factory = AdminShopsViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<AdminShopSheet?>(null) }

    val allShops = remember(state.remoteShops, state.draftShops) {
        state.draftShops + state.remoteShops
    }

    val shops = remember(allShops, query) {
        val q = query.trim()
        if (q.isEmpty()) allShops
        else allShops.filter { s ->
            s.name.contains(q, ignoreCase = true) ||
                s.country.contains(q, ignoreCase = true) ||
                s.region.contains(q, ignoreCase = true) ||
                s.id.contains(q, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_shops_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { sheet = AdminShopSheet.Add }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.admin_shops_add_draft))
                    }
                    IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.remoteShops.isEmpty() && state.draftShops.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null && state.remoteShops.isEmpty() && state.draftShops.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.common_retry)) }
            }
            else -> PullToRefreshBox(
                isRefreshing = state.loading && (state.remoteShops.isNotEmpty() || state.draftShops.isNotEmpty()),
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(stringResource(R.string.admin_shops_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Text(
                            stringResource(
                                R.string.admin_shops_kpi,
                                state.remoteShops.size,
                                state.draftShops.size,
                                shops.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                    if (shops.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.admin_shops_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(shops, key = { it.id }) { shop ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (shop.isLocalShopDraft()) {
                                                    sheet = AdminShopSheet.Edit(shop)
                                                } else {
                                                    innerNav.navigate(InnerRoutes.shopPublic(shop.id))
                                                }
                                            }
                                            .padding(10.dp),
                                    ) {
                                        if (shop.isLocalShopDraft()) {
                                            Text(
                                                stringResource(R.string.admin_shops_draft_badge),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.tertiary,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                        }
                                        Text(shop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        val sub = listOfNotNull(
                                            shop.region.takeIf { it.isNotBlank() },
                                            shop.country.takeIf { it.isNotBlank() },
                                        ).joinToString(", ")
                                        if (sub.isNotBlank()) {
                                            Text(sub, style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (shop.rating > 0.0) {
                                            Text(
                                                stringResource(R.string.shop_rating_line, shop.rating, shop.reviewCount),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (!shop.isLocalShopDraft()) {
                                            Text(
                                                stringResource(R.string.admin_shops_tap_public_hint),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (shop.isLocalShopDraft()) {
                                        IconButton(
                                            onClick = { vm.deleteDraft(shop.id) },
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.admin_shops_delete_draft),
                                            )
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

    val activeSheet = sheet
    if (activeSheet != null) {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            key(activeSheet) {
                val (initialId, initialName, initialCountry, initialRegion) = when (activeSheet) {
                    AdminShopSheet.Add -> DraftSheetInitial(null, "", "", "")
                    is AdminShopSheet.Edit -> DraftSheetInitial(
                        activeSheet.shop.id,
                        activeSheet.shop.name,
                        activeSheet.shop.country,
                        activeSheet.shop.region,
                    )
                }
                var name by remember { mutableStateOf(initialName) }
                var country by remember { mutableStateOf(initialCountry) }
                var region by remember { mutableStateOf(initialRegion) }
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        when (activeSheet) {
                            AdminShopSheet.Add -> stringResource(R.string.admin_shops_add_draft)
                            is AdminShopSheet.Edit -> stringResource(R.string.admin_shops_edit_draft)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.admin_shops_field_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text(stringResource(R.string.admin_shops_field_country)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it },
                        label = { Text(stringResource(R.string.admin_shops_field_region)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { sheet = null }) { Text(stringResource(R.string.common_cancel)) }
                        TextButton(
                            onClick = {
                                vm.saveDraft(initialId, name, country, region)
                                sheet = null
                            },
                            enabled = name.trim().isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.common_save))
                        }
                    }
                }
            }
        }
    }
}

private data class DraftSheetInitial(val id: String?, val name: String, val country: String, val region: String)
