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
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.ui.navigation.InnerRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterInstructorsRoute(
    graph: AppGraph,
    centerId: String,
    innerNav: NavController,
) {
    val vm: CenterInstructorsViewModel = viewModel(
        key = centerId,
        factory = CenterInstructorsViewModel.factory(graph, centerId),
    )
    val state by vm.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var assignSheetOpen by remember { mutableStateOf(false) }
    val filteredInstructors = remember(state.instructors, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            state.instructors
        } else {
            state.instructors.filter { u ->
                listOf(
                    u.displayName(),
                    u.email,
                    u.role.orEmpty(),
                    u.id,
                ).joinToString(" ").lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.admin_center_instructors_title))
                        state.centerName?.let { n ->
                            Text(
                                n,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.clearAssignError()
                        assignSheetOpen = true
                    }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.admin_center_instructors_assign))
                    }
                    IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.refresh() }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
            state.instructors.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.admin_center_instructors_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> PullToRefreshBox(
                isRefreshing = state.loading && state.instructors.isNotEmpty(),
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
                            stringResource(
                                R.string.admin_center_instructors_kpi,
                                state.instructors.size,
                                filteredInstructors.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.admin_center_instructors_search_label)) },
                            singleLine = true,
                        )
                    }
                    if (filteredInstructors.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.admin_center_instructors_empty_search),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(filteredInstructors, key = { it.id }) { user ->
                            InstructorRowCard(
                                user = user,
                                onClick = { innerNav.navigate(InnerRoutes.instructorPublic(user.id, centerId)) },
                                onUnassign = { vm.unassign(user) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (assignSheetOpen) {
        AssignInstructorSheet(
            state = state,
            onDismiss = { assignSheetOpen = false },
            onSearch = vm::searchCandidates,
            onAssign = vm::assign,
        )
    }
}

@Composable
private fun InstructorRowCard(
    user: UserDto,
    onClick: () -> Unit,
    onUnassign: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(user.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            user.role?.let {
                Text(
                    stringResource(R.string.profile_role, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.admin_center_instructors_open_profile_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onUnassign) {
                    Text(stringResource(R.string.admin_center_instructors_unassign))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignInstructorSheet(
    state: CenterInstructorsUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onAssign: (UserDto) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.admin_center_instructors_assign),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                label = { Text(stringResource(R.string.admin_center_instructors_assign_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.assigning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.assignError?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!state.assigning && query.trim().length >= 2 && state.candidates.isEmpty()) {
                Text(
                    stringResource(R.string.admin_center_instructors_assign_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.candidates.forEach { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName(), style = MaterialTheme.typography.titleSmall)
                            Text(
                                user.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = {
                                onAssign(user)
                                onDismiss()
                            },
                        ) {
                            Text(stringResource(R.string.admin_center_instructors_assign_action))
                        }
                    }
                }
            }
        }
    }
}
