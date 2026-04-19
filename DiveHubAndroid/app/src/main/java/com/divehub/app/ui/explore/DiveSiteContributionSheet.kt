package com.divehub.app.ui.explore

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.remote.dto.ExploreDiveSite
import kotlinx.coroutines.launch

sealed class DiveSiteContributionMode {
    data class Correction(val site: ExploreDiveSite) : DiveSiteContributionMode()
    data object NewSite : DiveSiteContributionMode()
}

@Composable
fun DiveSiteContributionSheetContent(
    mode: DiveSiteContributionMode,
    graph: AppGraph,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newLat by remember { mutableStateOf("") }
    var newLng by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun parseCoord(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()

    val canSend = when (mode) {
        is DiveSiteContributionMode.Correction ->
            messageText.trim().isNotEmpty()
        DiveSiteContributionMode.NewSite -> {
            val lat = parseCoord(newLat)
            val lng = parseCoord(newLng)
            newName.trim().isNotEmpty() && lat != null && lng != null
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            when (mode) {
                is DiveSiteContributionMode.Correction ->
                    stringResource(R.string.dive_site_contribution_report_title)
                DiveSiteContributionMode.NewSite ->
                    stringResource(R.string.dive_site_contribution_suggest_new_title)
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))

        when (mode) {
            is DiveSiteContributionMode.Correction -> {
                Text(mode.site.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dive_site_contribution_message_label)) },
                    minLines = 4,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.dive_site_contribution_correction_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DiveSiteContributionMode.NewSite -> {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dive_site_contribution_site_name)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newLat,
                    onValueChange = { newLat = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dive_site_contribution_latitude)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newLng,
                    onValueChange = { newLng = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dive_site_contribution_longitude)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newDescription,
                    onValueChange = { newDescription = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dive_site_contribution_description)) },
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dive_site_contribution_notes_optional)) },
                    minLines = 3,
                )
            }
        }

        errorText?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    if (!canSend || sending) return@Button
                    scope.launch {
                        val token = graph.tokenStore.getAccessToken()
                        if (token.isNullOrBlank()) {
                            errorText = context.getString(R.string.dive_site_contribution_login_required)
                            return@launch
                        }
                        sending = true
                        errorText = null
                        runCatching {
                            val repo = ExploreRepository(graph)
                            when (mode) {
                                is DiveSiteContributionMode.Correction ->
                                    repo.submitDiveSiteCorrection(mode.site.id, messageText.trim())
                                DiveSiteContributionMode.NewSite -> {
                                    val lat = parseCoord(newLat)!!
                                    val lng = parseCoord(newLng)!!
                                    repo.submitNewDiveSite(
                                        name = newName.trim(),
                                        latitude = lat,
                                        longitude = lng,
                                        description = newDescription.trim().takeIf { it.isNotEmpty() },
                                        message = messageText.trim().takeIf { it.isNotEmpty() },
                                    )
                                }
                            }
                        }.onSuccess {
                            Toast.makeText(
                                context,
                                context.getString(R.string.dive_site_contribution_sent),
                                Toast.LENGTH_LONG,
                            ).show()
                            onDismiss()
                        }.onFailure { e ->
                            errorText = e.message ?: context.getString(R.string.common_error)
                        }
                        sending = false
                    }
                },
                enabled = canSend && !sending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.dive_site_contribution_send))
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_cancel))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
