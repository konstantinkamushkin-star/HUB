package com.divehub.app.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.SupportTicketsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportTicketFormRoute(
    graph: AppGraph,
    innerNav: NavController,
    category: String,
) {
    val ctx = LocalContext.current
    val normalized = category.lowercase().trim()
    val allowed = setOf("feedback", "bug", "other")
    val effectiveCategory = if (normalized in allowed) normalized else "other"

    val screenTitle = when (effectiveCategory) {
        "feedback" -> stringResource(R.string.support_form_feedback_title)
        "bug" -> stringResource(R.string.support_form_bug_title)
        else -> stringResource(R.string.support_form_other_title)
    }

    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tickets = remember(graph) { SupportTicketsRepository(graph) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
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
        ) {
            if (sent) {
                Text(
                    stringResource(R.string.support_ticket_sent),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text(stringResource(R.string.support_ticket_subject)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.support_ticket_body)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (loading) return@Button
                    if (subject.isBlank() || body.isBlank()) {
                        error = ctx.getString(R.string.support_ticket_fill_required)
                        return@Button
                    }
                    scope.launch {
                        loading = true
                        error = null
                        runCatching {
                            tickets.submit(
                                subject = subject,
                                body = body,
                                category = effectiveCategory,
                            )
                        }.onSuccess {
                            sent = true
                        }.onFailure { e ->
                            error = e.message ?: ctx.getString(R.string.chat_error_generic)
                        }
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.support_ticket_send))
                }
            }
            }
        }
    }
}
