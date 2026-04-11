package com.divehub.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.BuildConfig
import com.divehub.app.R
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.ui.Routes
import com.divehub.app.ui.main.SessionViewModel
import com.divehub.app.ui.navigation.InnerRoutes
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    graph: AppGraph,
    sessionVm: SessionViewModel,
    user: UserDto?,
    innerNav: NavController,
    rootNav: NavController,
    onLoggedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var overrideUrl by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG) {
            overrideUrl = graph.tokenStore.getApiBaseOverride().orEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                if (user != null) {
                    Text(user.displayName(), style = MaterialTheme.typography.titleMedium)
                    Text(user.email, style = MaterialTheme.typography.bodyMedium)
                    user.role?.let {
                        Text(
                            stringResource(R.string.profile_role, it),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    Text(stringResource(R.string.profile_loading), style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(
                    onClick = { innerNav.navigate(InnerRoutes.EditProfile) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.profile_edit_title))
                }
                TextButton(
                    onClick = { innerNav.navigate(InnerRoutes.Search) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.search_screen_title))
                }
                TextButton(
                    onClick = { innerNav.navigate(InnerRoutes.Subscription) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.screen_subscription))
                }
                TextButton(
                    onClick = { innerNav.navigate(InnerRoutes.Certifications) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.screen_certifications))
                }

                TextButton(
                    onClick = { innerNav.navigate(InnerRoutes.Trips) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.nav_trips))
                }
                TextButton(
                    onClick = { innerNav.navigate(InnerRoutes.Notifications) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.screen_notifications))
                }
                TextButton(
                    onClick = { innerNav.navigate(InnerRoutes.Settings) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.screen_settings))
                }
                TextButton(
                    onClick = { innerNav.navigate(InnerRoutes.Statistics) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.screen_statistics))
                }
                TextButton(
                    onClick = { innerNav.navigate(InnerRoutes.Achievements) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.screen_achievements))
                }
                TextButton(
                    onClick = { innerNav.navigate(InnerRoutes.Help) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.screen_help))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { rootNav.navigate(Routes.ChangePassword) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.profile_change_password))
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (BuildConfig.DEBUG) {
                    Icon(
                        imageVector = Icons.Default.BubbleChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.profile_debug_api_title), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.profile_debug_api_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = overrideUrl,
                        onValueChange = { overrideUrl = it },
                        label = { Text(stringResource(R.string.profile_debug_url_label)) },
                        placeholder = { Text("http://192.168.1.10:3000") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                graph.tokenStore.setApiBaseOverride(overrideUrl.ifBlank { null })
                                graph.resetApiClient()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.profile_debug_save_url))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        scope.launch {
                            sessionVm.logout()
                            onLoggedOut()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.profile_logout))
                }
            }
        }
    }
}
