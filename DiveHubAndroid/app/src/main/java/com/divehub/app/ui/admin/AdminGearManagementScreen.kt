package com.divehub.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.divehub.app.data.AdminGearRepository
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.remote.dto.AdminGearItemLocal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val STATUS_ALL = "all"

data class AdminGearUiState(
    val loading: Boolean = true,
    val items: List<AdminGearItemLocal> = emptyList(),
    val error: String? = null,
)

class AdminGearViewModel(
    private val repo: AdminGearRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminGearUiState())
    val state: StateFlow<AdminGearUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            val centerId = auth.cachedUser()?.diveCenterId?.trim().orEmpty()
            runCatching {
                if (centerId.isNotBlank()) repo.syncFromRemoteOrCache(centerId) else repo.loadAll()
            }
                .onSuccess { list ->
                    _state.value = _state.value.copy(loading = false, error = null, items = list)
                }
                .onFailure { e ->
                    _state.value = prev.copy(loading = false, error = e.message ?: "Error")
                }
        }
    }

    fun setStatus(itemId: String, status: String) {
        viewModelScope.launch {
            val centerId = auth.cachedUser()?.diveCenterId?.trim().orEmpty()
            if (centerId.isNotBlank()) {
                runCatching { repo.patchStatusRemote(itemId, status, centerId) }
                    .onSuccess { refresh() }
                    .onFailure { e ->
                        _state.update { it.copy(error = e.message ?: "Error") }
                    }
            } else {
                _state.update { st ->
                    st.copy(items = st.items.map { if (it.id == itemId) it.copy(status = status) else it })
                }
                repo.saveAll(_state.value.items)
            }
        }
    }

    fun addItem(name: String, category: String, manufacturer: String?) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val centerId = auth.cachedUser()?.diveCenterId?.trim().orEmpty()
            if (centerId.isNotBlank()) {
                runCatching {
                    repo.createRemote(centerId, cleanName, category, manufacturer)
                }.onSuccess { refresh() }
                    .onFailure { e ->
                        _state.update { it.copy(error = e.message ?: "Error") }
                    }
            } else {
                val item = AdminGearItemLocal(
                    id = UUID.randomUUID().toString(),
                    name = cleanName,
                    category = category.trim().ifBlank { "other" },
                    manufacturer = manufacturer?.trim()?.takeIf { it.isNotEmpty() },
                    status = "available",
                )
                _state.update { st -> st.copy(items = listOf(item) + st.items) }
                repo.saveAll(_state.value.items)
            }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdminGearViewModel(AdminGearRepository(graph), AuthRepository(graph)) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminGearManagementRoute(graph: AppGraph, innerNav: NavController) {
    val vm: AdminGearViewModel = viewModel(factory = AdminGearViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var statusFilter by remember { mutableStateOf(STATUS_ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }

    val visible = remember(state.items, statusFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        state.items.filter { item ->
            val passStatus = statusFilter == STATUS_ALL || item.status == statusFilter
            val passSearch = q.isBlank() || listOf(
                item.name,
                item.category,
                item.manufacturer.orEmpty(),
                item.id,
            ).joinToString(" ").lowercase().contains(q)
            passStatus && passSearch
        }
    }
    val kpiAvailable = visible.count { it.status == "available" }
    val kpiIssued = visible.count { it.status == "issued" }
    val kpiMaintenance = visible.count { it.status == "maintenance" }
    val kpiScrapped = visible.count { it.status == "scrapped" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_gear_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
                    }
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.admin_gear_add))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null && state.items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.common_retry)) }
            }
            else -> PullToRefreshBox(
                isRefreshing = state.loading && state.items.isNotEmpty(),
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
                            stringResource(R.string.admin_gear_local_only_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.admin_gear_search_label)) },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.admin_gear_kpi_line,
                                visible.size,
                                kpiAvailable,
                                kpiIssued,
                                kpiMaintenance,
                                kpiScrapped,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.admin_gear_filter_status),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(STATUS_ALL, "available", "issued", "maintenance", "scrapped").forEach { key ->
                                FilterChip(
                                    selected = statusFilter == key,
                                    onClick = { statusFilter = key },
                                    label = {
                                        Text(
                                            when (key) {
                                                STATUS_ALL -> stringResource(R.string.admin_gear_status_all)
                                                "available" -> stringResource(R.string.admin_gear_status_available)
                                                "issued" -> stringResource(R.string.admin_gear_status_issued)
                                                "maintenance" -> stringResource(R.string.admin_gear_status_maintenance)
                                                else -> stringResource(R.string.admin_gear_status_scrapped)
                                            },
                                        )
                                    },
                                )
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
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.admin_gear_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(visible, key = { it.id }) { item ->
                            GearItemRow(item = item, onSetStatus = { vm.setStatus(item.id, it) })
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddGearSheet(
            onDismiss = { showAddSheet = false },
            onSubmit = { name, category, manufacturer ->
                vm.addItem(name, category, manufacturer)
                showAddSheet = false
            },
        )
    }
}

@Composable
private fun GearItemRow(
    item: AdminGearItemLocal,
    onSetStatus: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(item.category.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                item.manufacturer?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .background(statusColor(item.status), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = statusLabel(item.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
                TextButton(onClick = { menu = true }) {
                    Text(stringResource(R.string.admin_gear_change_status))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf("available", "issued", "maintenance", "scrapped").forEach { status ->
                        DropdownMenuItem(
                            text = { Text(statusLabel(status)) },
                            onClick = {
                                menu = false
                                onSetStatus(status)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGearSheet(
    onDismiss: () -> Unit,
    onSubmit: (name: String, category: String, manufacturer: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var manufacturer by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.admin_gear_add), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.admin_gear_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text(stringResource(R.string.admin_gear_category)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = manufacturer,
                onValueChange = { manufacturer = it },
                label = { Text(stringResource(R.string.admin_gear_manufacturer)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(onClick = { onSubmit(name, category, manufacturer) }, enabled = name.trim().isNotEmpty()) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    "available" -> stringResource(R.string.admin_gear_status_available)
    "issued" -> stringResource(R.string.admin_gear_status_issued)
    "maintenance" -> stringResource(R.string.admin_gear_status_maintenance)
    "scrapped" -> stringResource(R.string.admin_gear_status_scrapped)
    else -> status
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "available" -> Color(0xFF2E7D32)
    "issued" -> Color(0xFF1565C0)
    "maintenance" -> Color(0xFFF57C00)
    "scrapped" -> Color(0xFFC62828)
    else -> Color(0xFF616161)
}

