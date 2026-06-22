package com.divehub.app.ui.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.remote.dto.DiveLogDto
import com.divehub.app.util.absoluteMediaUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiveLogDetailRoute(
    graph: AppGraph,
    innerNav: NavController,
    logId: String,
) {
    val vm: LogbookViewModel = viewModel(factory = LogbookViewModel.factory(graph))
    val state by vm.state.collectAsState()
    val log = state.logs.find { it.id == logId }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showShare by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logbook_dive_log_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (log != null) {
                        IconButton(onClick = { showShare = true }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.logbook_share_dive))
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.logbook_delete_dive))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (log == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.common_error))
            }
            return@Scaffold
        }
        DiveLogDetailContent(
            log = log,
            imageApiRoot = state.imageApiRoot,
            modifier = Modifier.padding(padding),
        )
    }

    if (showShare && log != null) {
        ShareDiveDialog(
            log = log,
            displayTitle = vm.displayTitle(log),
            onDismiss = { showShare = false },
            onShare = { text ->
                vm.shareDiveToFeed(
                    log = log,
                    shareText = text,
                    onSuccess = { showShare = false },
                    onError = { msg ->
                        scope.launch { snack.showSnackbar(msg) }
                    },
                )
            },
        )
    }

    if (showDelete && log != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.logbook_delete_dive)) },
            text = { Text(stringResource(R.string.logbook_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLog(log.id)
                    showDelete = false
                    innerNav.popBackStack()
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
internal fun DiveLogDetailContent(
    log: DiveLogDto,
    imageApiRoot: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.logbook_date, log.date))
        Text(stringResource(R.string.logbook_max_depth_value, log.maxDepth.toInt()))
        Text(stringResource(R.string.logbook_avg_depth_value, log.averageDepth?.toInt() ?: 0))
        Text(stringResource(R.string.logbook_duration_value, log.duration))
        log.waterTemperature?.let { Text(stringResource(R.string.logbook_water_temp_value, it.toInt())) }
        log.visibility?.let { Text(stringResource(R.string.logbook_visibility_value, it.toInt())) }
        log.current?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.logbook_current_label) + ": $it")
        }
        log.diveType?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.logbook_dive_type_label) + ": $it")
        }
        if (!log.notes.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(log.notes ?: "")
        }
        val photos = log.photoUrls.orEmpty()
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos) { p ->
                    AsyncImage(
                        model = absoluteMediaUrl(imageApiRoot, p),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(160.dp, 100.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareDiveDialog(
    log: DiveLogDto,
    displayTitle: String,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
) {
    var text by remember(log.id) {
        mutableStateOf(
            "$displayTitle - ${log.maxDepth.toInt()}m, ${log.duration} min",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logbook_share_dive)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        },
        confirmButton = {
            Button(onClick = { onShare(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.common_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
