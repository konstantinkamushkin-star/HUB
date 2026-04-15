package com.divehub.app.ui.logbook

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.util.absoluteMediaUrl
import com.divehub.app.data.remote.dto.DiveLogDto
import com.divehub.app.ui.theme.IosDesign
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookRoute(graph: AppGraph) {
    val vm: LogbookViewModel = viewModel(factory = LogbookViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var selectedLog by remember { mutableStateOf<DiveLogDto?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null) }
        },
    ) { padding ->
        when {
            state.loading && state.logs.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null && state.logs.isEmpty() && !state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        state.error ?: stringResource(R.string.common_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = { vm.refresh() }) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
            state.logs.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.logbook_no_dives_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.logbook_no_dives_subtitle), style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> PullToRefreshBox(
                isRefreshing = state.loading && state.logs.isNotEmpty(),
                onRefresh = { vm.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(IosDesign.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(IosDesign.SectionSpacing),
                ) {
                    if (state.error != null) {
                        item {
                            Text(
                                state.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    item { StatsCard(state.stats) }
                    items(state.logs, key = { it.id }) { log ->
                        LogRow(log = log, onTap = { selectedLog = log })
                    }
                }
            }
        }
    }

    if (selectedLog != null) {
        ModalBottomSheet(onDismissRequest = { selectedLog = null }) {
            DiveLogDetailSheet(
                log = selectedLog!!,
                imageApiRoot = state.imageApiRoot,
                onClose = { selectedLog = null },
            )
        }
    }
    if (showAdd) {
        AddDiveLogSheet(vm = vm, onDismiss = { showAdd = false })
    }
}

@Composable
private fun StatsCard(stats: LogbookStats) {
    Card(
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
    ) {
        Row(Modifier.fillMaxWidth().padding(IosDesign.ScreenPadding), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCell(stringResource(R.string.logbook_total_dives), "${stats.totalDives}")
            StatCell(stringResource(R.string.logbook_total_time), "${stats.totalBottomTime} min")
            StatCell(stringResource(R.string.logbook_max_depth), "${stats.deepestDive.toInt()}m")
        }
    }
}

@Composable
private fun StatCell(title: String, value: String) {
    Column {
        Text(value, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LogRow(log: DiveLogDto, onTap: () -> Unit) {
    Card(
        onClick = onTap,
        shape = IosDesign.CardCorner,
        elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
    ) {
        Row(Modifier.fillMaxWidth().padding(IosDesign.ScreenPadding), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(log.date, fontWeight = FontWeight.SemiBold)
                Text(log.notes ?: stringResource(R.string.logbook_dive_log_fallback), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${log.maxDepth.toInt()}m", fontWeight = FontWeight.Bold)
                Text("${log.duration} min", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DiveLogDetailSheet(log: DiveLogDto, imageApiRoot: String, onClose: () -> Unit) {
    val currentLabel = stringResource(R.string.logbook_current_label)
    val diveTypeLabel = stringResource(R.string.logbook_dive_type_label)
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.logbook_dive_log_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.logbook_date, log.date))
        Text(stringResource(R.string.logbook_max_depth_value, log.maxDepth.toInt()))
        Text(stringResource(R.string.logbook_avg_depth_value, log.averageDepth?.toInt() ?: 0))
        Text(stringResource(R.string.logbook_duration_value, log.duration))
        log.waterTemperature?.let { Text(stringResource(R.string.logbook_water_temp_value, it.toInt())) }
        log.visibility?.let { Text(stringResource(R.string.logbook_visibility_value, it.toInt())) }
        log.current?.takeIf { it.isNotBlank() }?.let { cur ->
            Text("$currentLabel: $cur", style = MaterialTheme.typography.bodyMedium)
        }
        log.diveType?.takeIf { it.isNotBlank() }?.let { dt ->
            Text("$diveTypeLabel: $dt", style = MaterialTheme.typography.bodyMedium)
        }
        if (!log.notes.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(log.notes ?: "")
        }
        val photos = log.photoUrls.orEmpty()
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos.size) { idx ->
                    val p = photos[idx]
                    val url = absoluteMediaUrl(imageApiRoot, p)
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(120.dp, 80.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_close)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDiveLogSheet(vm: LogbookViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var duration by remember { mutableStateOf("45") }
    var maxDepth by remember { mutableStateOf("18") }
    var avgDepth by remember { mutableStateOf("12") }
    var temp by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var diveType by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris -> photos = uris }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.logbook_add_dive_log), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = date.toString(), onValueChange = {}, label = { Text(stringResource(R.string.logbook_date_label)) }, enabled = false, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = duration, onValueChange = { duration = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.logbook_duration_label)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = maxDepth, onValueChange = { maxDepth = it }, label = { Text(stringResource(R.string.logbook_max_depth_label)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = avgDepth, onValueChange = { avgDepth = it }, label = { Text(stringResource(R.string.logbook_avg_depth_label)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = temp, onValueChange = { temp = it }, label = { Text(stringResource(R.string.logbook_water_temp_label)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = visibility, onValueChange = { visibility = it }, label = { Text(stringResource(R.string.logbook_visibility_label)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = current, onValueChange = { current = it }, label = { Text(stringResource(R.string.logbook_current_label)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = diveType, onValueChange = { diveType = it }, label = { Text(stringResource(R.string.logbook_dive_type_label)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.logbook_notes_label)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(stringResource(R.string.logbook_add_photos_count, photos.size))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    vm.addDive(
                        context = context,
                        date = date,
                        durationMin = duration.toIntOrNull() ?: 45,
                        maxDepth = maxDepth.toDoubleOrNull() ?: 18.0,
                        avgDepth = avgDepth.toDoubleOrNull(),
                        temp = temp.toDoubleOrNull(),
                        visibility = visibility.toDoubleOrNull(),
                        current = current,
                        diveType = diveType,
                        notes = notes,
                        photoUris = photos,
                        onDone = onDismiss,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.logbook_save_dive)) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
