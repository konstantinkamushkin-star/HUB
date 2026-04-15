package com.divehub.app.ui.inventory

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.divehub.app.data.InventoryRepository
import com.divehub.app.data.remote.dto.InventoryItemLocal
import com.divehub.app.data.remote.dto.MaintenanceTicketLocal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

private enum class InventoryTab { DASHBOARD, LIST, MAINTENANCE, REPORTS }

data class InventoryUiState(
    val loading: Boolean = true,
    val items: List<InventoryItemLocal> = emptyList(),
    val tickets: List<MaintenanceTicketLocal> = emptyList(),
    val error: String? = null,
)

class InventoryViewModel(
    private val repo: InventoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InventoryUiState())
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            runCatching {
                repo.loadItems() to repo.loadTickets()
            }.onSuccess { (items, tickets) ->
                _state.value = _state.value.copy(loading = false, error = null, items = items, tickets = tickets)
            }.onFailure { e ->
                _state.value = prev.copy(loading = false, error = e.message ?: "Error")
            }
        }
    }

    fun addItem(name: String, category: String, size: String?, location: String?) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val next = InventoryItemLocal(
                id = UUID.randomUUID().toString(),
                name = clean,
                category = category.trim().ifBlank { "other" },
                status = "available",
                condition = "good",
                size = size?.trim()?.takeIf { it.isNotEmpty() },
                location = location?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = Instant.now().toString(),
            )
            _state.update { it.copy(items = listOf(next) + it.items) }
            repo.saveItems(_state.value.items)
        }
    }

    fun checkout(itemId: String) {
        viewModelScope.launch {
            _state.update { st -> st.copy(items = st.items.map { if (it.id == itemId) it.copy(status = "issued") else it }) }
            repo.saveItems(_state.value.items)
        }
    }

    fun markMaintenance(itemId: String, title: String) {
        viewModelScope.launch {
            val item = _state.value.items.firstOrNull { it.id == itemId } ?: return@launch
            _state.update { st ->
                st.copy(
                    items = st.items.map { if (it.id == itemId) it.copy(status = "maintenance", condition = "needs_service") else it },
                    tickets = listOf(
                        MaintenanceTicketLocal(
                            id = UUID.randomUUID().toString(),
                            itemId = item.id,
                            itemName = item.name,
                            title = title.ifBlank { "Service ticket" },
                            status = "open",
                            priority = "medium",
                            createdAt = Instant.now().toString(),
                        ),
                    ) + st.tickets,
                )
            }
            repo.saveItems(_state.value.items)
            repo.saveTickets(_state.value.tickets)
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return InventoryViewModel(InventoryRepository(graph)) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryRoute(graph: AppGraph, innerNav: NavController) {
    val vm: InventoryViewModel = viewModel(factory = InventoryViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(InventoryTab.DASHBOARD) }
    var showAdd by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var categoryDraft by remember { mutableStateOf("other") }
    var sizeDraft by remember { mutableStateOf("") }
    var locationDraft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh_list))
                    }
                    IconButton(onClick = { showAdd = !showAdd }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.inventory_add_item))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(selected = tab == InventoryTab.DASHBOARD, onClick = { tab = InventoryTab.DASHBOARD }, text = { Text(stringResource(R.string.inventory_tab_dashboard)) })
                Tab(selected = tab == InventoryTab.LIST, onClick = { tab = InventoryTab.LIST }, text = { Text(stringResource(R.string.inventory_tab_list)) })
                Tab(selected = tab == InventoryTab.MAINTENANCE, onClick = { tab = InventoryTab.MAINTENANCE }, text = { Text(stringResource(R.string.inventory_tab_maintenance)) })
                Tab(selected = tab == InventoryTab.REPORTS, onClick = { tab = InventoryTab.REPORTS }, text = { Text(stringResource(R.string.inventory_tab_reports)) })
            }

            if (showAdd) {
                Card(Modifier.fillMaxWidth().padding(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.inventory_add_item), fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it }, label = { Text(stringResource(R.string.inventory_field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = categoryDraft, onValueChange = { categoryDraft = it }, label = { Text(stringResource(R.string.inventory_field_category)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = sizeDraft, onValueChange = { sizeDraft = it }, label = { Text(stringResource(R.string.inventory_field_size)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = locationDraft, onValueChange = { locationDraft = it }, label = { Text(stringResource(R.string.inventory_field_location)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.common_cancel)) }
                            TextButton(
                                onClick = {
                                    vm.addItem(nameDraft, categoryDraft, sizeDraft, locationDraft)
                                    showAdd = false
                                    nameDraft = ""
                                    categoryDraft = "other"
                                    sizeDraft = ""
                                    locationDraft = ""
                                },
                                enabled = nameDraft.trim().isNotEmpty(),
                            ) { Text(stringResource(R.string.common_ok)) }
                        }
                    }
                }
            }

            when {
                state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.common_retry)) }
                    }
                }
                else -> PullToRefreshBox(
                    isRefreshing = state.loading && state.items.isNotEmpty(),
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (tab) {
                        InventoryTab.DASHBOARD -> InventoryDashboardTab(state)
                        InventoryTab.LIST -> InventoryListTab(state = state, onCheckout = vm::checkout, onMaintenance = vm::markMaintenance)
                        InventoryTab.MAINTENANCE -> InventoryMaintenanceTab(state)
                        InventoryTab.REPORTS -> InventoryReportsTab(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryDashboardTab(state: InventoryUiState) {
    val total = state.items.size
    val available = state.items.count { it.status == "available" }
    val issued = state.items.count { it.status == "issued" }
    val maintenance = state.items.count { it.status == "maintenance" }
    val openTickets = state.tickets.count { it.status == "open" }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { KpiCard(stringResource(R.string.inventory_kpi_total), total.toString()) }
        item { KpiCard(stringResource(R.string.inventory_kpi_available), available.toString()) }
        item { KpiCard(stringResource(R.string.inventory_kpi_issued), issued.toString()) }
        item { KpiCard(stringResource(R.string.inventory_kpi_maintenance), maintenance.toString()) }
        item { KpiCard(stringResource(R.string.inventory_kpi_tickets_open), openTickets.toString()) }
    }
}

@Composable
private fun InventoryListTab(
    state: InventoryUiState,
    onCheckout: (String) -> Unit,
    onMaintenance: (String, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.items.isEmpty()) {
            item { Text(stringResource(R.string.inventory_empty_items)) }
        } else {
            items(state.items, key = { it.id }) { item ->
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${item.category} • ${item.status}", style = MaterialTheme.typography.bodySmall)
                        item.location?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onCheckout(item.id) }, enabled = item.status != "issued") {
                                Text(stringResource(R.string.inventory_action_checkout))
                            }
                            TextButton(onClick = { onMaintenance(item.id, "Inspection / service required") }) {
                                Text(stringResource(R.string.inventory_action_inspect))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryMaintenanceTab(state: InventoryUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.tickets.isEmpty()) {
            item { Text(stringResource(R.string.inventory_empty_tickets)) }
        } else {
            items(state.tickets, key = { it.id }) { ticket ->
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(ticket.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(ticket.itemName, style = MaterialTheme.typography.bodySmall)
                        Text("${ticket.priority} • ${ticket.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryReportsTab(state: InventoryUiState) {
    val byCategory = state.items.groupBy { it.category }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { KpiCard(stringResource(R.string.inventory_reports_total_items), state.items.size.toString()) }
        item { KpiCard(stringResource(R.string.inventory_reports_total_tickets), state.tickets.size.toString()) }
        item { Text(stringResource(R.string.inventory_reports_by_category), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
        if (byCategory.isEmpty()) {
            item { Text(stringResource(R.string.inventory_empty_items)) }
        } else {
            items(byCategory, key = { it.first }) { (category, count) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(category)
                    Text(count.toString(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

