package com.divehub.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ProfilePreferencesRepository
import com.divehub.app.data.remote.dto.MeasurementPrefs
import com.divehub.app.data.remote.dto.NotificationPrefs
import com.divehub.app.data.remote.dto.PrivacyPrefs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsRoute(
    graph: AppGraph,
    innerNav: NavController,
) {
    val repo = remember { ProfilePreferencesRepository(graph) }
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf(NotificationPrefs()) }

    LaunchedEffect(Unit) {
        prefs = repo.loadNotificationPrefs()
    }

    fun save(next: NotificationPrefs) {
        prefs = next
        scope.launch { repo.saveNotificationPrefs(next) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_notification_settings)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ToggleRow(
                title = stringResource(R.string.settings_notif_push),
                checked = prefs.pushNotifications,
                onChecked = { save(prefs.copy(pushNotifications = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.settings_notif_booking),
                checked = prefs.bookingReminders,
                onChecked = { save(prefs.copy(bookingReminders = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.settings_notif_friends),
                checked = prefs.friendActivity,
                onChecked = { save(prefs.copy(friendActivity = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.settings_notif_messages),
                checked = prefs.newMessages,
                onChecked = { save(prefs.copy(newMessages = it)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsRoute(
    graph: AppGraph,
    innerNav: NavController,
) {
    val repo = remember { ProfilePreferencesRepository(graph) }
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf(PrivacyPrefs()) }

    LaunchedEffect(Unit) {
        prefs = repo.loadPrivacyPrefs()
    }

    fun save(next: PrivacyPrefs) {
        prefs = next
        scope.launch { repo.savePrivacyPrefs(next) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_privacy_settings)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ToggleRow(
                title = stringResource(R.string.settings_privacy_location),
                checked = prefs.shareLocation,
                onChecked = { save(prefs.copy(shareLocation = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.settings_privacy_public_profile),
                checked = prefs.publicProfile,
                onChecked = { save(prefs.copy(publicProfile = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.settings_privacy_friend_search),
                checked = prefs.showInFriendSearch,
                onChecked = { save(prefs.copy(showInFriendSearch = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.settings_privacy_logbook),
                checked = prefs.shareLogbook,
                onChecked = { save(prefs.copy(shareLogbook = it)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementUnitsRoute(
    graph: AppGraph,
    innerNav: NavController,
) {
    val repo = remember { ProfilePreferencesRepository(graph) }
    val scope = rememberCoroutineScope()
    var metric by remember { mutableStateOf(true) }
    var showUnitsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        metric = repo.loadMeasurementPrefs().metric
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_measurement_units)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.settings_units_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = metric,
                    onClick = {
                        if (!metric) {
                            metric = true
                            scope.launch {
                                repo.saveMeasurementPrefs(MeasurementPrefs(metric = true))
                                showUnitsDialog = true
                            }
                        }
                    },
                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                )
                Text(
                    stringResource(R.string.settings_units_metric),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = !metric,
                    onClick = {
                        if (metric) {
                            metric = false
                            scope.launch {
                                repo.saveMeasurementPrefs(MeasurementPrefs(metric = false))
                                showUnitsDialog = true
                            }
                        }
                    },
                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                )
                Text(
                    stringResource(R.string.settings_units_imperial),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }

    if (showUnitsDialog) {
        AlertDialog(
            onDismissRequest = { showUnitsDialog = false },
            title = { Text(stringResource(R.string.settings_units_changed_title)) },
            text = { Text(stringResource(R.string.settings_units_changed_body)) },
            confirmButton = {
                TextButton(onClick = { showUnitsDialog = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onChecked,
            )
        },
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
