package com.divehub.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.ui.main.SessionViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

private fun UserDto.isDiverBasic(): Boolean {
    val r = role?.trim()?.uppercase().orEmpty()
    return r.isEmpty() || r == "DIVER_BASIC"
}

private fun UserDto.isDiverPro(): Boolean =
    role?.trim()?.uppercase() == "DIVER_PRO"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionRoute(
    graph: AppGraph,
    sessionVm: SessionViewModel,
    innerNav: NavController,
) {
    val user by sessionVm.user.collectAsState()
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository(graph) }
    val snack = remember { SnackbarHostState() }
    var monthly by remember { mutableStateOf(true) }
    var showPaySheet by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_subscription)) },
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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = user
            when {
                current == null -> Text(stringResource(R.string.profile_loading))
                current.isDiverBasic() -> {
                    Text(stringResource(R.string.subscription_upgrade_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.subscription_plan_label), style = MaterialTheme.typography.bodyMedium)
                    RowChoice(
                        selected = monthly,
                        onMonthly = { monthly = true },
                        onAnnual = { monthly = false },
                    )
                    Button(
                        onClick = { showPaySheet = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.subscription_subscribe))
                    }
                    Text(stringResource(R.string.subscription_pro_features), style = MaterialTheme.typography.titleSmall)
                    ProBullet(stringResource(R.string.subscription_bullet_logbook))
                    ProBullet(stringResource(R.string.subscription_bullet_social))
                    ProBullet(stringResource(R.string.subscription_bullet_chats))
                    ProBullet(stringResource(R.string.subscription_bullet_gear))
                }
                current.isDiverPro() -> {
                    Text(stringResource(R.string.subscription_active_title), color = MaterialTheme.colorScheme.primary)
                    current.subscriptionExpiresAt?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            stringResource(R.string.subscription_expires, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { showCancelConfirm = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.subscription_cancel))
                    }
                }
                else -> Text(stringResource(R.string.subscription_not_diver_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showPaySheet) {
        AlertDialog(
            onDismissRequest = { showPaySheet = false },
            title = { Text(stringResource(R.string.subscription_payment_title)) },
            text = { Text(stringResource(R.string.subscription_payment_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPaySheet = false
                        val u = user ?: return@TextButton
                        busy = true
                        scope.launch {
                            runCatching {
                                val expires = Instant.now().plus(if (monthly) 30L else 365L, ChronoUnit.DAYS).toString()
                                val next = u.copy(
                                    role = "DIVER_PRO",
                                    subscriptionTier = "PRO",
                                    subscriptionStatus = "active",
                                    subscriptionExpiresAt = expires,
                                )
                                repo.persistCachedUser(next)
                                sessionVm.onUserUpdated(next)
                            }.onFailure { e ->
                                snack.showSnackbar(AuthRepository(graph).parseErrorMessage(e))
                            }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.subscription_confirm_demo)) }
            },
            dismissButton = {
                TextButton(onClick = { showPaySheet = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(stringResource(R.string.subscription_cancel_title)) },
            text = { Text(stringResource(R.string.subscription_cancel_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        val u = user ?: return@TextButton
                        busy = true
                        scope.launch {
                            runCatching {
                                val next = u.copy(subscriptionStatus = "cancelled")
                                repo.persistCachedUser(next)
                                sessionVm.onUserUpdated(next)
                            }.onFailure { e ->
                                snack.showSnackbar(AuthRepository(graph).parseErrorMessage(e))
                            }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.subscription_confirm_cancel)) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text(stringResource(R.string.common_keep))
                }
            },
        )
    }
}

@Composable
private fun ProBullet(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun RowChoice(
    selected: Boolean,
    onMonthly: () -> Unit,
    onAnnual: () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RadioButton(
                selected = selected,
                onClick = onMonthly,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
            )
            Text(stringResource(R.string.subscription_plan_monthly), modifier = Modifier.padding(start = 8.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RadioButton(
                selected = !selected,
                onClick = onAnnual,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
            )
            Text(stringResource(R.string.subscription_plan_annual), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
