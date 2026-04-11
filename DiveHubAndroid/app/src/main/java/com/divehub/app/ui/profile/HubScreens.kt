package com.divehub.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.divehub.app.AppGraph
import com.divehub.app.BuildConfig
import com.divehub.app.R
import com.divehub.app.ui.main.SessionViewModel
import com.divehub.app.ui.navigation.InnerRoutes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpRoute(
    innerNav: NavController,
    onPartnerApplication: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_help)) },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.help_intro), style = MaterialTheme.typography.bodyLarge)
            HelpSection(
                title = stringResource(R.string.help_section_explore_title),
                body = stringResource(R.string.help_section_explore_body),
            )
            HelpSection(
                title = stringResource(R.string.help_section_logbook_title),
                body = stringResource(R.string.help_section_logbook_body),
            )
            HelpSection(
                title = stringResource(R.string.help_section_trips_title),
                body = stringResource(R.string.help_section_trips_body),
            )
            HelpSection(
                title = stringResource(R.string.help_section_account_title),
                body = stringResource(R.string.help_section_account_body),
            )
            HelpSection(
                title = stringResource(R.string.help_section_partners_title),
                body = stringResource(R.string.help_section_partners_body),
            )
            OutlinedButton(
                onClick = onPartnerApplication,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.help_partner_application_button))
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    graph: AppGraph,
    sessionVm: SessionViewModel,
    innerNav: NavController,
) {
    val user by sessionVm.user.collectAsState()
    val preferDiverShell by sessionVm.preferDiverShell.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_settings)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.settings_account_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            user?.let { u ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(u.displayName(), style = MaterialTheme.typography.titleMedium)
                    Text(u.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    u.role?.let {
                        Text(
                            stringResource(R.string.profile_role, it),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    u.certificationLevel?.let {
                        Text(
                            stringResource(R.string.settings_cert_level, it),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } ?: Text(
                stringResource(R.string.profile_loading),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (user?.role?.trim()?.uppercase() == "INSTRUCTOR") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            stringResource(R.string.settings_use_diver_home_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.settings_use_diver_home_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = preferDiverShell,
                        onCheckedChange = { sessionVm.setPreferDiverShell(it) },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(
                onClick = { innerNav.navigate(InnerRoutes.EditProfile) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.profile_edit_title))
            }
            Text(
                stringResource(R.string.settings_shortcuts_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            TextButton(
                onClick = { innerNav.navigate(InnerRoutes.Notifications) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.screen_notifications))
            }
            TextButton(
                onClick = { innerNav.navigate(InnerRoutes.Trips) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.nav_trips))
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
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.settings_app_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            TextButton(
                onClick = {
                    scope.launch {
                        graph.tokenStore.setAppLanguageTag(null)
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_language_system))
            }
            TextButton(
                onClick = {
                    scope.launch {
                        graph.tokenStore.setAppLanguageTag("en")
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_language_en))
            }
            TextButton(
                onClick = {
                    scope.launch {
                        graph.tokenStore.setAppLanguageTag("ru")
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_language_ru))
            }
            Text(
                stringResource(R.string.settings_preferences_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
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
                onClick = { innerNav.navigate(InnerRoutes.GearProfiles) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.screen_gear_profiles))
            }
            TextButton(
                onClick = { innerNav.navigate(InnerRoutes.PrivacySettings) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.screen_privacy_settings))
            }
            TextButton(
                onClick = { innerNav.navigate(InnerRoutes.NotificationSettings) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.screen_notification_settings))
            }
            TextButton(
                onClick = { innerNav.navigate(InnerRoutes.MeasurementUnits) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.screen_measurement_units))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.settings_about_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (BuildConfig.DEBUG) {
                TextButton(
                    onClick = {
                        scope.launch {
                            val base = graph.tokenStore.getRootBaseUrl()
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(base)),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_open_api_root_debug))
                }
            }
        }
    }
}
