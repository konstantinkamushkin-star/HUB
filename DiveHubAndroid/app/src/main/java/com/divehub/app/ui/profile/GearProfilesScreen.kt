package com.divehub.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.GearProfilesRepository
import com.divehub.app.data.remote.dto.GearProfileItemStored
import com.divehub.app.data.remote.dto.GearProfileStored
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearProfilesRoute(
    graph: AppGraph,
    innerNav: NavController,
) {
    val repo = remember { GearProfilesRepository(graph) }
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<GearProfileStored>>(emptyList()) }
    var showAddProfile by remember { mutableStateOf(false) }
    var showAddItemFor by remember { mutableStateOf<GearProfileStored?>(null) }
    var profileName by remember { mutableStateOf("") }
    var itemCategory by remember { mutableStateOf("wetsuit") }
    var itemSize by remember { mutableStateOf("") }
    var itemNotes by remember { mutableStateOf("") }

    suspend fun persist(next: List<GearProfileStored>) {
        repo.saveAll(next)
        profiles = repo.loadAll()
    }

    LaunchedEffect(Unit) {
        profiles = repo.loadAll()
    }

    LaunchedEffect(showAddItemFor) {
        if (showAddItemFor != null) {
            itemCategory = "wetsuit"
            itemSize = ""
            itemNotes = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_gear_profiles)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { profileName = ""; showAddProfile = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.gear_add_profile))
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Text(
                stringResource(R.string.gear_profiles_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(profiles, key = { it.id }) { p ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(R.string.gear_item_count, p.items.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { showAddItemFor = p }) {
                                    Text(stringResource(R.string.gear_add_item))
                                }
                            }
                            p.items.forEach { it ->
                                Text(
                                    "• ${it.category}: ${it.size}" + (it.notes?.let { n -> " — $n" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        persist(profiles.filter { it.id != p.id })
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.gear_delete_profile))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddProfile) {
        AlertDialog(
            onDismissRequest = { showAddProfile = false },
            title = { Text(stringResource(R.string.gear_new_profile_title)) },
            text = {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text(stringResource(R.string.gear_profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = profileName.trim().ifBlank { "Profile" }
                        val now = System.currentTimeMillis()
                        val p = GearProfileStored(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            items = emptyList(),
                            createdAtMs = now,
                            updatedAtMs = now,
                        )
                        scope.launch {
                            persist(profiles + p)
                            showAddProfile = false
                        }
                    },
                ) { Text(stringResource(R.string.profile_edit_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddProfile = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    val editing = showAddItemFor
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { showAddItemFor = null },
            title = { Text(stringResource(R.string.gear_add_item_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = itemCategory,
                        onValueChange = { itemCategory = it },
                        label = { Text(stringResource(R.string.gear_category)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = itemSize,
                        onValueChange = { itemSize = it },
                        label = { Text(stringResource(R.string.gear_size)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = itemNotes,
                        onValueChange = { itemNotes = it },
                        label = { Text(stringResource(R.string.gear_notes_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = itemSize.isNotBlank(),
                    onClick = {
                        val profile = editing
                        val item = GearProfileItemStored(
                            id = UUID.randomUUID().toString(),
                            category = itemCategory.trim().ifBlank { "other" },
                            size = itemSize.trim(),
                            notes = itemNotes.trim().takeIf { it.isNotEmpty() },
                        )
                        scope.launch {
                            val now = System.currentTimeMillis()
                            val next = profiles.map {
                                if (it.id == profile.id) {
                                    it.copy(
                                        items = it.items + item,
                                        updatedAtMs = now,
                                    )
                                } else {
                                    it
                                }
                            }
                            persist(next)
                            showAddItemFor = null
                        }
                    },
                ) { Text(stringResource(R.string.profile_edit_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemFor = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
