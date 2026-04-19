package com.divehub.app.ui.inventory

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.InventoryRepository
import com.divehub.app.data.remote.dto.InventoryItemLocal
import com.divehub.app.data.remote.dto.MaintenanceTicketEventLocal
import com.divehub.app.data.remote.dto.MaintenanceTicketLocal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private enum class InventoryTab { DASHBOARD, LIST, MAINTENANCE, REPORTS }
private data class ItemDraft(
    val id: String? = null,
    val name: String = "",
    val category: String = "other",
    val size: String = "",
    val location: String = "",
    val condition: String = "good",
    val notes: String = "",
)
private data class CheckoutDraft(
    val itemId: String,
    val itemName: String,
    val issuedToName: String = "",
    val dueAt: String = "",
    val notes: String = "",
    val handedOffBy: String = "",
)

private data class InspectionDraft(
    val itemId: String,
    val itemName: String,
    val title: String = "",
    val description: String = "",
    val priority: String = "medium",
    val checkVisual: Boolean = false,
    val checkPressure: Boolean = false,
    val checkSanitized: Boolean = false,
    val signedBy: String = "",
)

data class InventoryUiState(
    val loading: Boolean = true,
    val items: List<InventoryItemLocal> = emptyList(),
    val tickets: List<MaintenanceTicketLocal> = emptyList(),
    val error: String? = null,
)

class InventoryViewModel(
    private val repo: InventoryRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InventoryUiState())
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    private suspend fun centerId(): String =
        auth.cachedUser()?.diveCenterId?.trim().orEmpty()

    fun refresh() {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            val c = centerId()
            runCatching {
                if (c.isNotBlank()) repo.syncFromRemoteOrCache(c)
                repo.loadItems() to repo.loadTickets()
            }.onSuccess { (items, tickets) ->
                _state.value = _state.value.copy(loading = false, error = null, items = items, tickets = tickets)
            }.onFailure { e ->
                _state.value = prev.copy(loading = false, error = e.message ?: "Error")
            }
        }
    }

    fun addItem(
        name: String,
        category: String,
        size: String?,
        location: String?,
        condition: String,
        notes: String?,
    ) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val cond = condition.trim().lowercase().ifBlank { "good" }.let { c ->
            if (c == "needs_service") "needs_service" else "good"
        }
        viewModelScope.launch {
            val c = centerId()
            val next = InventoryItemLocal(
                id = if (c.isNotBlank()) "" else UUID.randomUUID().toString(),
                name = clean,
                category = category.trim().ifBlank { "other" },
                status = "available",
                condition = cond,
                size = size?.trim()?.takeIf { it.isNotEmpty() },
                location = location?.trim()?.takeIf { it.isNotEmpty() },
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = Instant.now().toString(),
            )
            if (c.isNotBlank()) {
                runCatching { repo.upsertItemRemote(c, next) }
                    .onSuccess { refresh() }
                    .onFailure { e -> _state.update { it.copy(error = e.message ?: "Error") } }
            } else {
                _state.update { it.copy(items = listOf(next) + it.items) }
                repo.saveItems(_state.value.items)
            }
        }
    }

    fun updateItem(
        itemId: String,
        name: String,
        category: String,
        size: String?,
        location: String?,
        condition: String,
        notes: String?,
    ) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val cond = condition.trim().lowercase().ifBlank { "good" }.let { c ->
            if (c == "needs_service") "needs_service" else "good"
        }
        viewModelScope.launch {
            val c = centerId()
            val cur = _state.value.items.firstOrNull { it.id == itemId } ?: return@launch
            val updated = cur.copy(
                name = clean,
                category = category.trim().ifBlank { "other" },
                size = size?.trim()?.takeIf { it.isNotEmpty() },
                location = location?.trim()?.takeIf { it.isNotEmpty() },
                condition = cond,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            )
            if (c.isNotBlank()) {
                runCatching { repo.upsertItemRemote(c, updated) }
                    .onSuccess { refresh() }
                    .onFailure { e -> _state.update { it.copy(error = e.message ?: "Error") } }
            } else {
                _state.update { st ->
                    st.copy(items = st.items.map { item -> if (item.id != itemId) item else updated })
                }
                repo.saveItems(_state.value.items)
            }
        }
    }

    fun checkout(
        itemId: String,
        issuedToName: String,
        dueAt: String?,
        notes: String?,
        handedOffBy: String,
    ) {
        val assignee = issuedToName.trim()
        val staff = handedOffBy.trim()
        if (assignee.isEmpty() || staff.isEmpty()) return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val c = centerId()
            val cur = _state.value.items.firstOrNull { it.id == itemId } ?: return@launch
            val updated = cur.copy(
                status = "issued",
                issuedToName = assignee,
                dueAt = dueAt?.trim()?.takeIf { v -> v.isNotEmpty() },
                checkoutNotes = notes?.trim()?.takeIf { v -> v.isNotEmpty() },
                checkoutHandedOffBy = staff,
                checkoutHandedOffAt = now,
            )
            if (c.isNotBlank()) {
                runCatching { repo.upsertItemRemote(c, updated) }
                    .onSuccess { refresh() }
                    .onFailure { e -> _state.update { it.copy(error = e.message ?: "Error") } }
            } else {
                _state.update { st ->
                    st.copy(items = st.items.map { if (it.id == itemId) updated else it })
                }
                repo.saveItems(_state.value.items)
            }
        }
    }

    fun checkIn(itemId: String) {
        viewModelScope.launch {
            val c = centerId()
            if (c.isNotBlank()) {
                runCatching { repo.checkInItemRemote(c, itemId) }
                    .onSuccess { refresh() }
                    .onFailure { e -> _state.update { it.copy(error = e.message ?: "Error") } }
            } else {
                repo.checkInItem(itemId)
                _state.update { st -> st.copy(items = repo.loadItems()) }
            }
        }
    }

    fun markMaintenance(
        itemId: String,
        title: String,
        description: String?,
        priority: String,
        checklist: List<String>,
        signedBy: String?,
    ) {
        viewModelScope.launch {
            val item = _state.value.items.firstOrNull { it.id == itemId } ?: return@launch
            val normalizedPriority = when (priority.trim().lowercase()) {
                "low" -> "low"
                "high" -> "high"
                else -> "medium"
            }
            val now = Instant.now().toString()
            val c = centerId()
            if (c.isNotBlank()) {
                val itemUpdated = item.copy(status = "maintenance", condition = "needs_service")
                runCatching {
                    val saved = repo.upsertItemRemote(c, itemUpdated)
                    val ticket = MaintenanceTicketLocal(
                        id = "",
                        itemId = saved.id,
                        itemName = saved.name,
                        title = title.ifBlank { "Service ticket" },
                        status = "open",
                        priority = normalizedPriority,
                        description = description?.trim()?.takeIf { d -> d.isNotEmpty() },
                        checklist = checklist,
                        signedBy = signedBy?.trim()?.takeIf { it.isNotEmpty() },
                        signedAt = signedBy?.trim()?.takeIf { it.isNotEmpty() }?.let { now },
                        createdAt = now,
                        events = listOf(
                            MaintenanceTicketEventLocal(
                                type = "opened",
                                at = now,
                                note = listOfNotNull(
                                    description?.trim()?.takeIf { d -> d.isNotEmpty() },
                                    checklist.takeIf { it.isNotEmpty() }?.joinToString(", "),
                                ).joinToString(" | ").takeIf { it.isNotBlank() },
                            ),
                        ),
                    )
                    repo.upsertTicketRemote(c, ticket)
                }.onSuccess { refresh() }
                    .onFailure { e -> _state.update { it.copy(error = e.message ?: "Error") } }
            } else {
                _state.update { st ->
                    st.copy(
                        items = st.items.map {
                            if (it.id == itemId) it.copy(status = "maintenance", condition = "needs_service") else it
                        },
                        tickets = listOf(
                            MaintenanceTicketLocal(
                                id = UUID.randomUUID().toString(),
                                itemId = item.id,
                                itemName = item.name,
                                title = title.ifBlank { "Service ticket" },
                                status = "open",
                                priority = normalizedPriority,
                                description = description?.trim()?.takeIf { d -> d.isNotEmpty() },
                                checklist = checklist,
                                signedBy = signedBy?.trim()?.takeIf { it.isNotEmpty() },
                                signedAt = signedBy?.trim()?.takeIf { it.isNotEmpty() }?.let { now },
                                createdAt = now,
                                events = listOf(
                                    MaintenanceTicketEventLocal(
                                        type = "opened",
                                        at = now,
                                        note = listOfNotNull(
                                            description?.trim()?.takeIf { d -> d.isNotEmpty() },
                                            checklist.takeIf { it.isNotEmpty() }?.joinToString(", "),
                                        ).joinToString(" | ").takeIf { it.isNotBlank() },
                                    ),
                                ),
                            ),
                        ) + st.tickets,
                    )
                }
                repo.saveItems(_state.value.items)
                repo.saveTickets(_state.value.tickets)
            }
        }
    }

    fun updateTicketStatus(ticketId: String, status: String) {
        viewModelScope.launch {
            val ticket = _state.value.tickets.firstOrNull { it.id == ticketId } ?: return@launch
            val now = Instant.now().toString()
            val c = centerId()
            val nextTicket = ticket.copy(
                status = status,
                startedAt = if (status == "in_progress" && ticket.startedAt == null) now else ticket.startedAt,
                completedAt = if (status == "completed") now else ticket.completedAt,
                events = ticket.events + MaintenanceTicketEventLocal(
                    type = when (status) {
                        "in_progress" -> "started"
                        "completed" -> "completed"
                        else -> "status_changed"
                    },
                    at = now,
                    note = status,
                ),
            )
            val restoreItem = status == "completed" || status == "closed"
            val nextItems = if (!restoreItem) {
                _state.value.items
            } else {
                _state.value.items.map { item ->
                    if (item.id == ticket.itemId) item.copy(status = "available", condition = "good") else item
                }
            }
            if (c.isNotBlank()) {
                runCatching {
                    if (restoreItem) {
                        val it = nextItems.firstOrNull { it.id == ticket.itemId }
                        if (it != null) repo.upsertItemRemote(c, it)
                    }
                    repo.upsertTicketRemote(c, nextTicket)
                }.onSuccess { refresh() }
                    .onFailure { e -> _state.update { it.copy(error = e.message ?: "Error") } }
            } else {
                _state.update { st ->
                    st.copy(items = nextItems, tickets = st.tickets.map { t -> if (t.id != ticketId) t else nextTicket })
                }
                repo.saveItems(_state.value.items)
                repo.saveTickets(_state.value.tickets)
            }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return InventoryViewModel(InventoryRepository(graph), AuthRepository(graph)) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryRoute(graph: AppGraph, innerNav: NavController) {
    val vm: InventoryViewModel = viewModel(factory = InventoryViewModel.factory(graph))
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(InventoryTab.DASHBOARD) }
    var editorDraft by remember { mutableStateOf<ItemDraft?>(null) }
    var checkoutDraft by remember { mutableStateOf<CheckoutDraft?>(null) }
    var inspectionDraft by remember { mutableStateOf<InspectionDraft?>(null) }

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
                    if (tab == InventoryTab.REPORTS && (state.items.isNotEmpty() || state.tickets.isNotEmpty())) {
                        IconButton(
                            onClick = {
                                shareInventoryReport(
                                    context = context,
                                    subject = context.getString(R.string.inventory_reports_export_subject),
                                    body = buildInventoryReportText(state),
                                )
                            },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.inventory_reports_export_share))
                        }
                    }
                    IconButton(onClick = { editorDraft = ItemDraft() }) {
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
                        InventoryTab.LIST -> InventoryListTab(
                            state = state,
                            onCheckout = { item ->
                                checkoutDraft = CheckoutDraft(
                                    itemId = item.id,
                                    itemName = item.name,
                                    issuedToName = item.issuedToName.orEmpty(),
                                    dueAt = item.dueAt.orEmpty(),
                                    notes = item.checkoutNotes.orEmpty(),
                                    handedOffBy = item.checkoutHandedOffBy.orEmpty(),
                                )
                            },
                            onCheckIn = vm::checkIn,
                            onMaintenance = { item ->
                                inspectionDraft = InspectionDraft(itemId = item.id, itemName = item.name, title = "Inspection / service required")
                            },
                            onEdit = { item ->
                                editorDraft = ItemDraft(
                                    id = item.id,
                                    name = item.name,
                                    category = item.category,
                                    size = item.size.orEmpty(),
                                    location = item.location.orEmpty(),
                                    condition = item.condition.ifBlank { "good" },
                                    notes = item.notes.orEmpty(),
                                )
                            },
                            onOpenDetails = { item ->
                                innerNav.navigate(com.divehub.app.ui.navigation.InnerRoutes.inventoryItemDetail(item.id))
                            },
                        )
                        InventoryTab.MAINTENANCE -> InventoryMaintenanceTab(
                            state = state,
                            onSetInProgress = { vm.updateTicketStatus(it, "in_progress") },
                            onSetCompleted = { vm.updateTicketStatus(it, "completed") },
                            onOpenItem = { itemId ->
                                innerNav.navigate(com.divehub.app.ui.navigation.InnerRoutes.inventoryItemDetail(itemId))
                            },
                            onOpenTicket = { ticketId ->
                                innerNav.navigate(com.divehub.app.ui.navigation.InnerRoutes.inventoryTicketDetail(ticketId))
                            },
                        )
                        InventoryTab.REPORTS -> InventoryReportsTab(state)
                    }
                }
            }
        }
    }

    val activeDraft = editorDraft
    if (activeDraft != null) {
        ItemEditorSheet(
            initial = activeDraft,
            onDismiss = { editorDraft = null },
            onSubmit = { draft ->
                if (draft.id == null) {
                    vm.addItem(
                        draft.name,
                        draft.category,
                        draft.size,
                        draft.location,
                        draft.condition,
                        draft.notes,
                    )
                } else {
                    vm.updateItem(
                        draft.id,
                        draft.name,
                        draft.category,
                        draft.size,
                        draft.location,
                        draft.condition,
                        draft.notes,
                    )
                }
                editorDraft = null
            },
        )
    }

    val activeCheckout = checkoutDraft
    if (activeCheckout != null) {
        CheckoutSheet(
            initial = activeCheckout,
            onDismiss = { checkoutDraft = null },
            onSubmit = { d ->
                vm.checkout(
                    itemId = d.itemId,
                    issuedToName = d.issuedToName,
                    dueAt = d.dueAt,
                    notes = d.notes,
                    handedOffBy = d.handedOffBy,
                )
                checkoutDraft = null
            },
        )
    }

    val activeInspection = inspectionDraft
    if (activeInspection != null) {
        InspectionSheet(
            initial = activeInspection,
            onDismiss = { inspectionDraft = null },
            onSubmit = { d ->
                vm.markMaintenance(
                    itemId = d.itemId,
                    title = d.title,
                    description = d.description,
                    priority = d.priority,
                    checklist = listOfNotNull(
                        if (d.checkVisual) context.getString(R.string.inventory_check_visual) else null,
                        if (d.checkPressure) context.getString(R.string.inventory_check_pressure) else null,
                        if (d.checkSanitized) context.getString(R.string.inventory_check_sanitized) else null,
                    ),
                    signedBy = d.signedBy,
                )
                inspectionDraft = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryItemDetailRoute(
    graph: AppGraph,
    innerNav: NavController,
    itemId: String,
) {
    val scope = rememberCoroutineScope()
    var loading by remember(itemId) { mutableStateOf(true) }
    var error by remember(itemId) { mutableStateOf<String?>(null) }
    var item by remember(itemId) { mutableStateOf<InventoryItemLocal?>(null) }
    var tickets by remember(itemId) { mutableStateOf<List<MaintenanceTicketLocal>>(emptyList()) }
    var showDeleteDialog by remember(itemId) { mutableStateOf(false) }

    suspend fun loadData() {
        loading = true
        error = null
        val repo = InventoryRepository(graph)
        runCatching {
            val centerId = AuthRepository(graph).cachedUser()?.diveCenterId?.trim().orEmpty()
            if (centerId.isNotBlank()) repo.syncFromRemoteOrCache(centerId)
            val items = repo.loadItems()
            val related = repo.loadTickets().filter { it.itemId == itemId }
            items.firstOrNull { it.id == itemId } to related
        }.onSuccess { (found, related) ->
            item = found
            tickets = related
            loading = false
            error = null
        }.onFailure { e ->
            loading = false
            error = e.message ?: "Error"
        }
    }

    LaunchedEffect(itemId) { loadData() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.inventory_item_detail_title)) },
                    navigationIcon = {
                        IconButton(onClick = { innerNav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        val i = item
                        if (!loading && error == null && i != null) {
                            if (i.status == "issued") {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val repo = InventoryRepository(graph)
                                            val c = AuthRepository(graph).cachedUser()?.diveCenterId?.trim().orEmpty()
                                            if (c.isNotBlank()) repo.checkInItemRemote(c, itemId) else repo.checkInItem(itemId)
                                            loadData()
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.inventory_action_checkin))
                                }
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.inventory_item_delete_cd),
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                error != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(error ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error) }
                item == null -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.inventory_item_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> {
                    val current = item ?: return@Scaffold
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(current.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${current.category} • ${inventoryStatusLabel(current.status)} • ${inventoryConditionLabel(current.condition)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    current.size?.takeIf { it.isNotBlank() }?.let {
                                        Text("${stringResource(R.string.inventory_field_size)}: $it", style = MaterialTheme.typography.bodySmall)
                                    }
                                    current.location?.takeIf { it.isNotBlank() }?.let {
                                        Text("${stringResource(R.string.inventory_field_location)}: $it", style = MaterialTheme.typography.bodySmall)
                                    }
                                    current.notes?.takeIf { it.isNotBlank() }?.let { n ->
                                        Text(
                                            stringResource(R.string.inventory_field_notes),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(n, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    current.issuedToName?.takeIf { it.isNotBlank() }?.let {
                                        Text(stringResource(R.string.inventory_issued_to_line, it), style = MaterialTheme.typography.bodySmall)
                                    }
                                    current.dueAt?.takeIf { it.isNotBlank() }?.let {
                                        Text(stringResource(R.string.inventory_due_at_line, it), style = MaterialTheme.typography.bodySmall)
                                    }
                                    current.checkoutNotes?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            stringResource(R.string.inventory_checkout_notes_line, it),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    current.checkoutHandedOffBy?.takeIf { it.isNotBlank() }?.let { staff ->
                                        Text(
                                            stringResource(R.string.inventory_checkout_handed_off_line, staff),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    current.checkoutHandedOffAt?.takeIf { it.isNotBlank() }?.let { at ->
                                        Text(
                                            stringResource(R.string.inventory_checkout_handed_off_at_line, at),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.inventory_created_at_line, current.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.inventory_item_tickets_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (tickets.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.inventory_empty_tickets),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(tickets, key = { it.id }) { ticket ->
                                Card(
                                    modifier = Modifier.clickable {
                                        innerNav.navigate(com.divehub.app.ui.navigation.InnerRoutes.inventoryTicketDetail(ticket.id))
                                    },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(ticket.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${ticketPriorityLabel(ticket.priority)} • ${ticketStatusLabel(ticket.status)}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            stringResource(R.string.inventory_created_at_line, ticket.createdAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        ticket.description?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (ticket.events.isNotEmpty()) {
                                            Text(
                                                stringResource(R.string.inventory_ticket_timeline_preview_count, ticket.events.size),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            ticket.events.sortedByDescending { it.at }.take(2).forEach { ev ->
                                                Text(
                                                    "• ${ticketEventLabel(ev.type)} — ${ev.at}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                ev.note?.takeIf { n -> n.isNotBlank() }?.let { note ->
                                                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
        }

        if (showDeleteDialog && item != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.inventory_item_delete_title)) },
                text = { Text(stringResource(R.string.inventory_item_delete_message, item!!.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val repo = InventoryRepository(graph)
                                val c = AuthRepository(graph).cachedUser()?.diveCenterId?.trim().orEmpty()
                                if (c.isNotBlank()) repo.deleteItemRemote(c, itemId)
                                else repo.deleteItemAndRelatedTickets(itemId)
                                showDeleteDialog = false
                                innerNav.popBackStack()
                            }
                        },
                    ) { Text(stringResource(R.string.common_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryTicketDetailRoute(
    graph: AppGraph,
    innerNav: NavController,
    ticketId: String,
) {
    val scope = rememberCoroutineScope()
    var loading by remember(ticketId) { mutableStateOf(true) }
    var error by remember(ticketId) { mutableStateOf<String?>(null) }
    var ticket by remember(ticketId) { mutableStateOf<MaintenanceTicketLocal?>(null) }
    var item by remember(ticketId) { mutableStateOf<InventoryItemLocal?>(null) }

    suspend fun reload() {
        val repo = InventoryRepository(graph)
        runCatching {
            val centerId = AuthRepository(graph).cachedUser()?.diveCenterId?.trim().orEmpty()
            if (centerId.isNotBlank()) repo.syncFromRemoteOrCache(centerId)
            val tickets = repo.loadTickets()
            val items = repo.loadItems()
            val foundTicket = tickets.firstOrNull { it.id == ticketId }
            val foundItem = foundTicket?.let { t -> items.firstOrNull { it.id == t.itemId } }
            foundTicket to foundItem
        }.onSuccess { (t, i) ->
            ticket = t
            item = i
            loading = false
            error = null
        }.onFailure { e ->
            loading = false
            error = e.message ?: "Error"
        }
    }

    LaunchedEffect(ticketId) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_ticket_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            error != null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(error ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error) }
            ticket == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.inventory_ticket_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> {
                val current = ticket ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(current.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${ticketStatusLabel(current.status)} • ${ticketPriorityLabel(current.priority)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    stringResource(R.string.inventory_created_at_line, current.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                current.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                if (current.checklist.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.inventory_ticket_checklist_title),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    current.checklist.forEach { entry ->
                                        Text("• $entry", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                current.signedBy?.takeIf { it.isNotBlank() }?.let { signer ->
                                    Text(
                                        stringResource(R.string.inventory_ticket_signed_by_line, signer),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    stringResource(R.string.inventory_ticket_item_section),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (item == null) {
                                    Text(
                                        stringResource(R.string.inventory_item_not_found),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    item?.let { i ->
                                        Text(i.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${i.category} • ${inventoryStatusLabel(i.status)}", style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = {
                                            innerNav.navigate(com.divehub.app.ui.navigation.InnerRoutes.inventoryItemDetail(i.id))
                                        }) {
                                            Text(stringResource(R.string.inventory_open_item))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    stringResource(R.string.inventory_ticket_timeline_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                current.startedAt?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        stringResource(R.string.inventory_ticket_started_at_line, it),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                current.completedAt?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        stringResource(R.string.inventory_ticket_completed_at_line, it),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (current.events.isEmpty()) {
                                    Text(
                                        stringResource(R.string.inventory_ticket_timeline_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    current.events.sortedByDescending { it.at }.forEach { ev ->
                                        Text(
                                            "• ${ticketEventLabel(ev.type)} — ${ev.at}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        ev.note?.takeIf { n -> n.isNotBlank() }?.let { note ->
                                            Text(
                                                note,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (current.status == "open") {
                                TextButton(onClick = {
                                    scope.launch {
                                        val repo = InventoryRepository(graph)
                                        val tickets = repo.loadTickets()
                                        val items = repo.loadItems()
                                        val now = Instant.now().toString()
                                        val nextTickets = tickets.map { t ->
                                            if (t.id != ticketId) t else t.copy(
                                                status = "in_progress",
                                                startedAt = t.startedAt ?: now,
                                                events = t.events + MaintenanceTicketEventLocal(
                                                    type = "started",
                                                    at = now,
                                                    note = "in_progress",
                                                ),
                                            )
                                        }
                                        repo.saveTickets(nextTickets)
                                        repo.saveItems(items)
                                        reload()
                                    }
                                }) { Text(stringResource(R.string.inventory_ticket_start)) }
                            }
                            if (current.status == "open" || current.status == "in_progress") {
                                TextButton(onClick = {
                                    scope.launch {
                                        val repo = InventoryRepository(graph)
                                        val tickets = repo.loadTickets()
                                        val items = repo.loadItems()
                                        val now = Instant.now().toString()
                                        val target = tickets.firstOrNull { it.id == ticketId }
                                        val nextTickets = tickets.map { t ->
                                            if (t.id != ticketId) t else t.copy(
                                                status = "completed",
                                                completedAt = now,
                                                events = t.events + MaintenanceTicketEventLocal(
                                                    type = "completed",
                                                    at = now,
                                                    note = "completed",
                                                ),
                                            )
                                        }
                                        val nextItems = if (target == null) items else items.map { i ->
                                            if (i.id == target.itemId) i.copy(status = "available", condition = "good") else i
                                        }
                                        repo.saveTickets(nextTickets)
                                        repo.saveItems(nextItems)
                                        reload()
                                    }
                                }) { Text(stringResource(R.string.inventory_ticket_complete)) }
                            }
                        }
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
    val inProgressTickets = state.tickets.count { it.status == "in_progress" }
    val completedTickets = state.tickets.count { it.status == "completed" }
    val highOpenTickets = state.tickets.count {
        it.status == "open" && it.priority.equals("high", ignoreCase = true)
    }
    val issuedWithoutDue = state.items.count {
        it.status == "issued" && it.dueAt.isNullOrBlank()
    }
    val lowPriorityTickets = state.tickets.count { it.priority.equals("low", ignoreCase = true) }
    val mediumPriorityTickets = state.tickets.count { it.priority.equals("medium", ignoreCase = true) }
    val highPriorityTickets = state.tickets.count { it.priority.equals("high", ignoreCase = true) }
    val itemsTrend7d = remember(state.items) {
        buildDailyTrend7d(state.items.mapNotNull { parseIsoInstantOrNull(it.createdAt) })
    }
    val ticketsTrend7d = remember(state.tickets) {
        buildDailyTrend7d(state.tickets.mapNotNull { parseIsoInstantOrNull(it.createdAt) })
    }
    val trendMax = remember(itemsTrend7d, ticketsTrend7d) {
        maxOf(
            itemsTrend7d.maxOfOrNull { it.count } ?: 0,
            ticketsTrend7d.maxOfOrNull { it.count } ?: 0,
            1,
        )
    }
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
        item {
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.inventory_dashboard_status_breakdown),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.inventory_dashboard_status_line, available, issued, maintenance),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.inventory_dashboard_tickets_line, openTickets, inProgressTickets, completedTickets),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (total > 0) {
            item {
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.inventory_dashboard_items_chart_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        DashboardProgressRow(
                            label = stringResource(R.string.inventory_filter_status_available),
                            value = available,
                            total = total,
                        )
                        DashboardProgressRow(
                            label = stringResource(R.string.inventory_filter_status_issued),
                            value = issued,
                            total = total,
                        )
                        DashboardProgressRow(
                            label = stringResource(R.string.inventory_filter_status_maintenance),
                            value = maintenance,
                            total = total,
                        )
                    }
                }
            }
        }
        if (state.tickets.isNotEmpty()) {
            val ticketTotal = state.tickets.size
            item {
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.inventory_dashboard_ticket_priority_chart_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        DashboardProgressRow(
                            label = stringResource(R.string.inventory_ticket_priority_low),
                            value = lowPriorityTickets,
                            total = ticketTotal,
                        )
                        DashboardProgressRow(
                            label = stringResource(R.string.inventory_ticket_priority_medium),
                            value = mediumPriorityTickets,
                            total = ticketTotal,
                        )
                        DashboardProgressRow(
                            label = stringResource(R.string.inventory_ticket_priority_high),
                            value = highPriorityTickets,
                            total = ticketTotal,
                        )
                    }
                }
            }
        }
        item {
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.inventory_reports_trend_7d),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.inventory_reports_trend_items_7d),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    itemsTrend7d.forEach { entry ->
                        DashboardProgressRow(
                            label = entry.date.toString(),
                            value = entry.count,
                            total = trendMax,
                        )
                    }
                    Text(
                        stringResource(R.string.inventory_reports_trend_tickets_7d),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ticketsTrend7d.forEach { entry ->
                        DashboardProgressRow(
                            label = entry.date.toString(),
                            value = entry.count,
                            total = trendMax,
                        )
                    }
                }
            }
        }
        if (highOpenTickets > 0 || issuedWithoutDue > 0) {
            item {
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.inventory_dashboard_alerts_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (highOpenTickets > 0) {
                            Text(
                                stringResource(R.string.inventory_dashboard_alert_high_tickets, highOpenTickets),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (issuedWithoutDue > 0) {
                            Text(
                                stringResource(R.string.inventory_dashboard_alert_no_due, issuedWithoutDue),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardProgressRow(label: String, value: Int, total: Int) {
    val fraction = if (total <= 0) 0f else (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$value/$total", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun InventoryListTab(
    state: InventoryUiState,
    onCheckout: (InventoryItemLocal) -> Unit,
    onCheckIn: (String) -> Unit,
    onMaintenance: (InventoryItemLocal) -> Unit,
    onEdit: (InventoryItemLocal) -> Unit,
    onOpenDetails: (InventoryItemLocal) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var categoryFilter by remember { mutableStateOf("all") }
    var conditionFilter by remember { mutableStateOf("all") }
    var dueFilter by remember { mutableStateOf("all") }
    var sortFilter by remember { mutableStateOf("recent") }
    val categories = remember(state.items) {
        state.items
            .map { it.category.trim().ifBlank { "other" } }
            .distinct()
            .sorted()
    }
    val normalizedQuery = searchQuery.trim().lowercase()
    val filteredItems = state.items.filter { item ->
        val normalizedStatus = item.status.trim().lowercase()
        val statusMatches = statusFilter == "all" || normalizedStatus == statusFilter
        val normalizedCategory = item.category.trim().ifBlank { "other" }
        val categoryMatches = categoryFilter == "all" || normalizedCategory == categoryFilter
        val normalizedCondition = item.condition.trim().lowercase()
        val conditionMatches = conditionFilter == "all" || normalizedCondition == conditionFilter
        val dueDate = parseIsoInstantOrNull(item.dueAt)
        val dueMatches = when (dueFilter) {
            "all" -> true
            "with_due" -> !item.dueAt.isNullOrBlank()
            "no_due" -> item.dueAt.isNullOrBlank()
            "overdue" -> item.status == "issued" && dueDate != null && dueDate.isBefore(Instant.now())
            else -> true
        }
        val searchableFields = listOf(
            item.name,
            normalizedCategory,
            normalizedStatus,
            normalizedCondition,
            item.size.orEmpty(),
            item.location.orEmpty(),
            item.issuedToName.orEmpty(),
            item.checkoutHandedOffBy.orEmpty(),
            item.notes.orEmpty(),
        )
        val searchMatches = normalizedQuery.isEmpty() || searchableFields.any { field ->
            field.lowercase().contains(normalizedQuery)
        }
        statusMatches && categoryMatches && conditionMatches && dueMatches && searchMatches
    }.let { items ->
        when (sortFilter) {
            "oldest" -> items.sortedBy { parseIsoInstantOrNull(it.createdAt) ?: Instant.EPOCH }
            "name_az" -> items.sortedBy { it.name.lowercase() }
            "name_za" -> items.sortedByDescending { it.name.lowercase() }
            else -> items.sortedByDescending { parseIsoInstantOrNull(it.createdAt) ?: Instant.EPOCH }
        }
    }
    val issuedCount = filteredItems.count { it.status == "issued" }
    val maintenanceCount = filteredItems.count { it.status == "maintenance" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.inventory_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            val filtersActive = searchQuery.isNotBlank() ||
                statusFilter != "all" ||
                categoryFilter != "all" ||
                conditionFilter != "all" ||
                dueFilter != "all" ||
                sortFilter != "recent"
            if (filtersActive) {
                TextButton(
                    onClick = {
                        searchQuery = ""
                        statusFilter = "all"
                        categoryFilter = "all"
                        conditionFilter = "all"
                        dueFilter = "all"
                        sortFilter = "recent"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.inventory_reset_filters))
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = statusFilter == "all",
                    onClick = { statusFilter = "all" },
                    label = { Text(stringResource(R.string.inventory_filter_status_all)) },
                )
                FilterChip(
                    selected = statusFilter == "available",
                    onClick = { statusFilter = "available" },
                    label = { Text(stringResource(R.string.inventory_filter_status_available)) },
                )
                FilterChip(
                    selected = statusFilter == "issued",
                    onClick = { statusFilter = "issued" },
                    label = { Text(stringResource(R.string.inventory_filter_status_issued)) },
                )
                FilterChip(
                    selected = statusFilter == "maintenance",
                    onClick = { statusFilter = "maintenance" },
                    label = { Text(stringResource(R.string.inventory_filter_status_maintenance)) },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = categoryFilter == "all",
                    onClick = { categoryFilter = "all" },
                    label = { Text(stringResource(R.string.inventory_filter_category_all)) },
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = categoryFilter == category,
                        onClick = { categoryFilter = category },
                        label = { Text(category) },
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = conditionFilter == "all",
                    onClick = { conditionFilter = "all" },
                    label = { Text(stringResource(R.string.inventory_filter_condition_all)) },
                )
                FilterChip(
                    selected = conditionFilter == "good",
                    onClick = { conditionFilter = "good" },
                    label = { Text(stringResource(R.string.inventory_filter_condition_good)) },
                )
                FilterChip(
                    selected = conditionFilter == "needs_service",
                    onClick = { conditionFilter = "needs_service" },
                    label = { Text(stringResource(R.string.inventory_filter_condition_needs_service)) },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = dueFilter == "all",
                    onClick = { dueFilter = "all" },
                    label = { Text(stringResource(R.string.inventory_filter_due_all)) },
                )
                FilterChip(
                    selected = dueFilter == "with_due",
                    onClick = { dueFilter = "with_due" },
                    label = { Text(stringResource(R.string.inventory_filter_due_with_due)) },
                )
                FilterChip(
                    selected = dueFilter == "no_due",
                    onClick = { dueFilter = "no_due" },
                    label = { Text(stringResource(R.string.inventory_filter_due_no_due)) },
                )
                FilterChip(
                    selected = dueFilter == "overdue",
                    onClick = { dueFilter = "overdue" },
                    label = { Text(stringResource(R.string.inventory_filter_due_overdue)) },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = sortFilter == "recent",
                    onClick = { sortFilter = "recent" },
                    label = { Text(stringResource(R.string.inventory_sort_recent)) },
                )
                FilterChip(
                    selected = sortFilter == "oldest",
                    onClick = { sortFilter = "oldest" },
                    label = { Text(stringResource(R.string.inventory_sort_oldest)) },
                )
                FilterChip(
                    selected = sortFilter == "name_az",
                    onClick = { sortFilter = "name_az" },
                    label = { Text(stringResource(R.string.inventory_sort_name_az)) },
                )
                FilterChip(
                    selected = sortFilter == "name_za",
                    onClick = { sortFilter = "name_za" },
                    label = { Text(stringResource(R.string.inventory_sort_name_za)) },
                )
            }
        }
        item {
            Text(
                text = stringResource(
                    R.string.inventory_list_kpi,
                    filteredItems.size,
                    issuedCount,
                    maintenanceCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.items.isEmpty()) {
            item { Text(stringResource(R.string.inventory_empty_items)) }
        } else if (filteredItems.isEmpty()) {
            item { Text(stringResource(R.string.inventory_empty_filter)) }
        } else {
            items(filteredItems, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.clickable { onOpenDetails(item) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${item.category} • ${inventoryStatusLabel(item.status)} • ${inventoryConditionLabel(item.condition)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        item.size?.takeIf { it.isNotBlank() }?.let {
                            Text("${stringResource(R.string.inventory_field_size)}: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        item.location?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (item.status == "issued") {
                            item.issuedToName?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    stringResource(R.string.inventory_issued_to_line, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item.dueAt?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    stringResource(R.string.inventory_due_at_line, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item.checkoutHandedOffBy?.takeIf { it.isNotBlank() }?.let { staff ->
                                Text(
                                    stringResource(R.string.inventory_checkout_handed_off_line, staff),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onEdit(item) }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Text(stringResource(R.string.inventory_action_edit))
                            }
                            if (item.status == "issued") {
                                TextButton(onClick = { onCheckIn(item.id) }) {
                                    Text(stringResource(R.string.inventory_action_checkin))
                                }
                            } else {
                                TextButton(onClick = { onCheckout(item) }) {
                                    Text(stringResource(R.string.inventory_action_checkout))
                                }
                            }
                            TextButton(onClick = { onMaintenance(item) }) {
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
private fun inventoryStatusLabel(status: String): String {
    return when (status.trim().lowercase()) {
        "available" -> stringResource(R.string.inventory_filter_status_available)
        "issued" -> stringResource(R.string.inventory_filter_status_issued)
        "maintenance" -> stringResource(R.string.inventory_filter_status_maintenance)
        else -> status
    }
}

@Composable
private fun inventoryConditionLabel(condition: String): String = when (condition.trim().lowercase()) {
    "good" -> stringResource(R.string.inventory_filter_condition_good)
    "needs_service" -> stringResource(R.string.inventory_filter_condition_needs_service)
    else -> condition
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemEditorSheet(
    initial: ItemDraft,
    onDismiss: () -> Unit,
    onSubmit: (ItemDraft) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var category by remember(initial.id) { mutableStateOf(initial.category) }
    var size by remember(initial.id) { mutableStateOf(initial.size) }
    var location by remember(initial.id) { mutableStateOf(initial.location) }
    var condition by remember(initial.id) { mutableStateOf(initial.condition.ifBlank { "good" }) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (initial.id == null) stringResource(R.string.inventory_add_item) else stringResource(R.string.inventory_edit_item),
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.inventory_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text(stringResource(R.string.inventory_field_category)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = size,
                onValueChange = { size = it },
                label = { Text(stringResource(R.string.inventory_field_size)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text(stringResource(R.string.inventory_field_location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.inventory_field_condition),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = condition.equals("good", ignoreCase = true),
                    onClick = { condition = "good" },
                    label = { Text(stringResource(R.string.inventory_filter_condition_good)) },
                )
                FilterChip(
                    selected = condition.equals("needs_service", ignoreCase = true),
                    onClick = { condition = "needs_service" },
                    label = { Text(stringResource(R.string.inventory_filter_condition_needs_service)) },
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.inventory_field_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = {
                        onSubmit(
                            ItemDraft(
                                id = initial.id,
                                name = name,
                                category = category,
                                size = size,
                                location = location,
                                condition = condition,
                                notes = notes,
                            ),
                        )
                    },
                    enabled = name.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutSheet(
    initial: CheckoutDraft,
    onDismiss: () -> Unit,
    onSubmit: (CheckoutDraft) -> Unit,
) {
    var issuedTo by remember(initial.itemId) { mutableStateOf(initial.issuedToName) }
    var dueAt by remember(initial.itemId) { mutableStateOf(initial.dueAt) }
    var notes by remember(initial.itemId) { mutableStateOf(initial.notes) }
    var handedOffBy by remember(initial.itemId) { mutableStateOf(initial.handedOffBy) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.inventory_checkout_title, initial.itemName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = issuedTo,
                onValueChange = { issuedTo = it },
                label = { Text(stringResource(R.string.inventory_checkout_issued_to)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dueAt,
                onValueChange = { dueAt = it },
                label = { Text(stringResource(R.string.inventory_checkout_due_at)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.inventory_checkout_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = handedOffBy,
                onValueChange = { handedOffBy = it },
                label = { Text(stringResource(R.string.inventory_checkout_handed_off_by)) },
                supportingText = { Text(stringResource(R.string.inventory_checkout_handed_off_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = {
                        onSubmit(
                            initial.copy(
                                issuedToName = issuedTo,
                                dueAt = dueAt,
                                notes = notes,
                                handedOffBy = handedOffBy,
                            ),
                        )
                    },
                    enabled = issuedTo.trim().isNotEmpty() && handedOffBy.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.inventory_action_checkout))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectionSheet(
    initial: InspectionDraft,
    onDismiss: () -> Unit,
    onSubmit: (InspectionDraft) -> Unit,
) {
    var title by remember(initial.itemId) { mutableStateOf(initial.title) }
    var description by remember(initial.itemId) { mutableStateOf(initial.description) }
    var priority by remember(initial.itemId) {
        mutableStateOf(
            when (initial.priority.trim().lowercase()) {
                "low" -> "low"
                "high" -> "high"
                else -> "medium"
            },
        )
    }
    var checkVisual by remember(initial.itemId) { mutableStateOf(initial.checkVisual) }
    var checkPressure by remember(initial.itemId) { mutableStateOf(initial.checkPressure) }
    var checkSanitized by remember(initial.itemId) { mutableStateOf(initial.checkSanitized) }
    var signedBy by remember(initial.itemId) { mutableStateOf(initial.signedBy) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.inventory_inspection_title, initial.itemName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.inventory_inspection_ticket_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.inventory_inspection_ticket_description)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.inventory_inspection_priority),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = priority == "low",
                    onClick = { priority = "low" },
                    label = { Text(stringResource(R.string.inventory_ticket_priority_low)) },
                )
                FilterChip(
                    selected = priority == "medium",
                    onClick = { priority = "medium" },
                    label = { Text(stringResource(R.string.inventory_ticket_priority_medium)) },
                )
                FilterChip(
                    selected = priority == "high",
                    onClick = { priority = "high" },
                    label = { Text(stringResource(R.string.inventory_ticket_priority_high)) },
                )
            }
            Text(
                stringResource(R.string.inventory_inspection_checklist_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checkVisual, onCheckedChange = { checkVisual = it })
                Text(stringResource(R.string.inventory_check_visual))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checkPressure, onCheckedChange = { checkPressure = it })
                Text(stringResource(R.string.inventory_check_pressure))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checkSanitized, onCheckedChange = { checkSanitized = it })
                Text(stringResource(R.string.inventory_check_sanitized))
            }
            OutlinedTextField(
                value = signedBy,
                onValueChange = { signedBy = it },
                label = { Text(stringResource(R.string.inventory_inspection_signed_by)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = {
                        onSubmit(
                            initial.copy(
                                title = title,
                                description = description,
                                priority = priority,
                                checkVisual = checkVisual,
                                checkPressure = checkPressure,
                                checkSanitized = checkSanitized,
                                signedBy = signedBy,
                            ),
                        )
                    },
                    enabled = title.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.inventory_action_inspect))
                }
            }
        }
    }
}

@Composable
private fun InventoryMaintenanceTab(
    state: InventoryUiState,
    onSetInProgress: (String) -> Unit,
    onSetCompleted: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenTicket: (String) -> Unit,
) {
    var statusFilter by remember { mutableStateOf("all") }
    var priorityFilter by remember { mutableStateOf("all") }
    val filteredTickets = remember(state.tickets, statusFilter, priorityFilter) {
        state.tickets.filter { ticket ->
            val statusOk = statusFilter == "all" || ticket.status.equals(statusFilter, ignoreCase = true)
            val normalizedPriority = ticket.priority.trim().lowercase().ifBlank { "medium" }
            val priorityOk = priorityFilter == "all" || normalizedPriority == priorityFilter
            statusOk && priorityOk
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("all", "open", "in_progress", "completed").forEach { status ->
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = { statusFilter = status },
                        label = { Text(ticketStatusLabel(status)) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = priorityFilter == "all",
                    onClick = { priorityFilter = "all" },
                    label = { Text(stringResource(R.string.inventory_maintenance_filter_priority_all)) },
                )
                listOf("low", "medium", "high").forEach { pr ->
                    FilterChip(
                        selected = priorityFilter == pr,
                        onClick = { priorityFilter = pr },
                        label = { Text(ticketPriorityLabel(pr)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (statusFilter != "all" || priorityFilter != "all") {
                TextButton(
                    onClick = {
                        statusFilter = "all"
                        priorityFilter = "all"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.inventory_reset_filters))
                }
            }
            Text(
                stringResource(
                    R.string.inventory_maintenance_kpi,
                    filteredTickets.size,
                    filteredTickets.count { it.status == "open" },
                    filteredTickets.count { it.status == "in_progress" },
                    filteredTickets.count { it.status == "completed" },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.tickets.isEmpty()) {
            item { Text(stringResource(R.string.inventory_empty_tickets)) }
        } else if (filteredTickets.isEmpty()) {
            item { Text(stringResource(R.string.inventory_empty_filter)) }
        } else {
            items(filteredTickets, key = { it.id }) { ticket ->
                Card(
                    modifier = Modifier.clickable { onOpenTicket(ticket.id) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(ticket.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(ticket.itemName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${ticketPriorityLabel(ticket.priority)} • ${ticketStatusLabel(ticket.status)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.inventory_created_at_line, ticket.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ticket.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onOpenItem(ticket.itemId) }) {
                                Text(stringResource(R.string.inventory_open_item))
                            }
                            if (ticket.status == "open") {
                                TextButton(onClick = { onSetInProgress(ticket.id) }) {
                                    Text(stringResource(R.string.inventory_ticket_start))
                                }
                            }
                            if (ticket.status == "open" || ticket.status == "in_progress") {
                                TextButton(onClick = { onSetCompleted(ticket.id) }) {
                                    Text(stringResource(R.string.inventory_ticket_complete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ticketStatusLabel(status: String): String = when (status.lowercase()) {
    "all" -> stringResource(R.string.inventory_ticket_filter_all)
    "open" -> stringResource(R.string.inventory_ticket_status_open)
    "in_progress" -> stringResource(R.string.inventory_ticket_status_in_progress)
    "completed" -> stringResource(R.string.inventory_ticket_status_completed)
    else -> status
}

@Composable
private fun ticketEventLabel(type: String): String = when (type.lowercase()) {
    "opened" -> stringResource(R.string.inventory_ticket_event_opened)
    "started" -> stringResource(R.string.inventory_ticket_event_started)
    "completed" -> stringResource(R.string.inventory_ticket_event_completed)
    else -> type
}

@Composable
private fun ticketPriorityLabel(priority: String): String = when (priority.trim().lowercase()) {
    "low" -> stringResource(R.string.inventory_ticket_priority_low)
    "high" -> stringResource(R.string.inventory_ticket_priority_high)
    "medium" -> stringResource(R.string.inventory_ticket_priority_medium)
    else -> priority
}

@Composable
private fun InventoryReportsTab(state: InventoryUiState) {
    val context = LocalContext.current
    val now = remember { Instant.now() }
    val itemsLast7d = state.items.count { item ->
        parseIsoInstantOrNull(item.createdAt)?.isAfter(now.minusSeconds(7L * 24L * 3600L)) == true
    }
    val ticketsLast7d = state.tickets.count { ticket ->
        parseIsoInstantOrNull(ticket.createdAt)?.isAfter(now.minusSeconds(7L * 24L * 3600L)) == true
    }
    val itemsLast30d = state.items.count { item ->
        parseIsoInstantOrNull(item.createdAt)?.isAfter(now.minusSeconds(30L * 24L * 3600L)) == true
    }
    val ticketsLast30d = state.tickets.count { ticket ->
        parseIsoInstantOrNull(ticket.createdAt)?.isAfter(now.minusSeconds(30L * 24L * 3600L)) == true
    }
    val issuedWithPastDue = state.items.count { item ->
        item.status == "issued" && parseIsoInstantOrNull(item.dueAt).let { due -> due != null && due.isBefore(now) }
    }
    val byCategory = state.items.groupBy { it.category }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
    val byItemStatus = state.items
        .groupBy { it.status.trim().lowercase() }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
    val ticketByStatus = state.tickets
        .groupBy { it.status.trim().lowercase() }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
    val ticketByPriority = state.tickets
        .groupBy { it.priority.trim().lowercase().ifBlank { "medium" } }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
    val itemsTrend7d = remember(state.items) {
        buildDailyTrend7d(state.items.mapNotNull { parseIsoInstantOrNull(it.createdAt) })
    }
    val ticketsTrend7d = remember(state.tickets) {
        buildDailyTrend7d(state.tickets.mapNotNull { parseIsoInstantOrNull(it.createdAt) })
    }
    val trendMax = remember(itemsTrend7d, ticketsTrend7d) {
        maxOf(
            itemsTrend7d.maxOfOrNull { it.count } ?: 0,
            ticketsTrend7d.maxOfOrNull { it.count } ?: 0,
            1,
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { KpiCard(stringResource(R.string.inventory_reports_total_items), state.items.size.toString()) }
        item { KpiCard(stringResource(R.string.inventory_reports_total_tickets), state.tickets.size.toString()) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val reportText = buildInventoryReportText(state)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.inventory_reports_export_subject))
                            putExtra(Intent.EXTRA_TEXT, reportText)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                sendIntent,
                                context.getString(R.string.inventory_reports_export_share),
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.inventory_reports_export_share))
                }
                TextButton(
                    onClick = {
                        val reportCsv = buildInventoryReportCsv(state)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.inventory_reports_export_subject))
                            putExtra(Intent.EXTRA_TEXT, reportCsv)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                sendIntent,
                                context.getString(R.string.inventory_reports_export_share_csv),
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.inventory_reports_export_share_csv))
                }
            }
        }
        item {
            Text(
                stringResource(R.string.inventory_reports_recent_activity),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.inventory_reports_recent_items_7d))
                Text(itemsLast7d.toString(), fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.inventory_reports_recent_tickets_7d))
                Text(ticketsLast7d.toString(), fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.inventory_reports_recent_items_30d))
                Text(itemsLast30d.toString(), fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.inventory_reports_recent_tickets_30d))
                Text(ticketsLast30d.toString(), fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.inventory_reports_overdue_issued))
                Text(
                    issuedWithPastDue.toString(),
                    fontWeight = FontWeight.SemiBold,
                    color = if (issuedWithPastDue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.inventory_reports_trend_7d),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Text(
                stringResource(R.string.inventory_reports_trend_items_7d),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(itemsTrend7d, key = { "items_${it.date}" }) { entry ->
            DashboardProgressRow(
                label = entry.date.toString(),
                value = entry.count,
                total = trendMax,
            )
        }
        item {
            Text(
                stringResource(R.string.inventory_reports_trend_tickets_7d),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(ticketsTrend7d, key = { "tickets_${it.date}" }) { entry ->
            DashboardProgressRow(
                label = entry.date.toString(),
                value = entry.count,
                total = trendMax,
            )
        }
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
        item {
            Text(
                stringResource(R.string.inventory_reports_by_item_status),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (byItemStatus.isEmpty()) {
            item { Text(stringResource(R.string.inventory_empty_items)) }
        } else {
            items(byItemStatus, key = { it.first }) { (status, count) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(inventoryStatusLabel(status))
                    Text(count.toString(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Text(
                stringResource(R.string.inventory_reports_tickets_by_status),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (ticketByStatus.isEmpty()) {
            item { Text(stringResource(R.string.inventory_empty_tickets)) }
        } else {
            items(ticketByStatus, key = { it.first }) { (status, count) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ticketStatusLabel(status))
                    Text(count.toString(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Text(
                stringResource(R.string.inventory_reports_tickets_by_priority),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (ticketByPriority.isEmpty()) {
            item { Text(stringResource(R.string.inventory_empty_tickets)) }
        } else {
            items(ticketByPriority, key = { it.first }) { (priority, count) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ticketPriorityLabel(priority))
                    Text(count.toString(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun parseIsoInstantOrNull(value: String?): Instant? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return runCatching { Instant.parse(raw) }.getOrNull()
}

private fun shareInventoryReport(context: Context, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}

private fun buildInventoryReportText(state: InventoryUiState): String {
    val byCategory = state.items.groupBy { it.category.trim().ifBlank { "other" } }
    val byItemStatus = state.items.groupBy { it.status.trim().ifBlank { "unknown" } }
    val byTicketStatus = state.tickets.groupBy { it.status.trim().ifBlank { "unknown" } }
    val byTicketPriority = state.tickets.groupBy { it.priority.trim().ifBlank { "medium" } }
    val overdueIssued = state.items.count { item ->
        item.status == "issued" && parseIsoInstantOrNull(item.dueAt)?.isBefore(Instant.now()) == true
    }
    val itemTrend7d = buildDailyTrend7d(state.items.mapNotNull { parseIsoInstantOrNull(it.createdAt) })
    val ticketTrend7d = buildDailyTrend7d(state.tickets.mapNotNull { parseIsoInstantOrNull(it.createdAt) })

    fun section(title: String, rows: List<Pair<String, Int>>): String {
        if (rows.isEmpty()) return "$title: n/a"
        return buildString {
            appendLine("$title:")
            rows.forEach { (name, count) -> appendLine("- $name: $count") }
        }.trimEnd()
    }

    return buildString {
        appendLine("DiveHub Inventory Report")
        appendLine("Generated: ${Instant.now()}")
        appendLine()
        appendLine("Totals:")
        appendLine("- Items: ${state.items.size}")
        appendLine("- Tickets: ${state.tickets.size}")
        appendLine("- Overdue issued: $overdueIssued")
        appendLine()
        appendLine("7-day trend:")
        itemTrend7d.forEach { appendLine("- Items ${it.date}: ${it.count}") }
        ticketTrend7d.forEach { appendLine("- Tickets ${it.date}: ${it.count}") }
        appendLine()
        appendLine(
            section(
                title = "By item status",
                rows = byItemStatus.mapValues { it.value.size }.toList().sortedByDescending { it.second },
            ),
        )
        appendLine()
        appendLine(
            section(
                title = "By category",
                rows = byCategory.mapValues { it.value.size }.toList().sortedByDescending { it.second },
            ),
        )
        appendLine()
        appendLine(
            section(
                title = "Tickets by status",
                rows = byTicketStatus.mapValues { it.value.size }.toList().sortedByDescending { it.second },
            ),
        )
        appendLine()
        append(
            section(
                title = "Tickets by priority",
                rows = byTicketPriority.mapValues { it.value.size }.toList().sortedByDescending { it.second },
            ),
        )
    }.trim()
}

private fun buildInventoryReportCsv(state: InventoryUiState): String {
    val header = "section,key,value"
    val rows = mutableListOf<String>()
    rows += "totals,items,${state.items.size}"
    rows += "totals,tickets,${state.tickets.size}"
    val overdueIssued = state.items.count { item ->
        item.status == "issued" && parseIsoInstantOrNull(item.dueAt)?.isBefore(Instant.now()) == true
    }
    rows += "totals,overdue_issued,$overdueIssued"
    buildDailyTrend7d(state.items.mapNotNull { parseIsoInstantOrNull(it.createdAt) })
        .forEach { rows += "trend_items_7d,${it.date},${it.count}" }
    buildDailyTrend7d(state.tickets.mapNotNull { parseIsoInstantOrNull(it.createdAt) })
        .forEach { rows += "trend_tickets_7d,${it.date},${it.count}" }
    state.items
        .groupBy { it.category.trim().ifBlank { "other" } }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
        .forEach { (k, v) -> rows += "by_category,${k.csvEscape()},$v" }
    state.items
        .groupBy { it.status.trim().ifBlank { "unknown" } }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
        .forEach { (k, v) -> rows += "by_item_status,${k.csvEscape()},$v" }
    state.tickets
        .groupBy { it.status.trim().ifBlank { "unknown" } }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
        .forEach { (k, v) -> rows += "tickets_by_status,${k.csvEscape()},$v" }
    state.tickets
        .groupBy { it.priority.trim().ifBlank { "medium" } }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
        .forEach { (k, v) -> rows += "tickets_by_priority,${k.csvEscape()},$v" }
    return buildString {
        appendLine(header)
        rows.forEach { appendLine(it) }
    }.trim()
}

private data class DailyTrendEntry(
    val date: LocalDate,
    val count: Int,
)

private fun buildDailyTrend7d(instants: List<Instant>, now: Instant = Instant.now()): List<DailyTrendEntry> {
    val today = now.atZone(ZoneOffset.UTC).toLocalDate()
    val countsByDate = instants
        .map { it.atZone(ZoneOffset.UTC).toLocalDate() }
        .groupingBy { it }
        .eachCount()
    return (6 downTo 0).map { daysAgo ->
        val day = today.minusDays(daysAgo.toLong())
        DailyTrendEntry(date = day, count = countsByDate[day] ?: 0)
    }
}

private fun String.csvEscape(): String = buildString {
    append('"')
    append(this@csvEscape.replace("\"", "\"\""))
    append('"')
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

