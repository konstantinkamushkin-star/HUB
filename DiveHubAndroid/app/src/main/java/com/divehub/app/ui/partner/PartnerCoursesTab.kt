package com.divehub.app.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.PartnerCoursesRepository
import com.divehub.app.data.remote.dto.AdminCourseLocal
import com.divehub.app.data.remote.dto.CourseWriteRequestDto
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.ui.theme.IosDesign
import kotlinx.coroutines.launch
import java.time.Instant

private enum class CourseFilter(val value: String) {
    ALL("all"),
    ACTIVE("active"),
    DRAFT("draft"),
    ARCHIVED("archived"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerCoursesTab(graph: AppGraph) {
    val remoteRepo = remember { TripsRepository(graph) }
    val localRepo = remember { PartnerCoursesRepository(graph) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var centerId by remember { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<AdminCourseLocal>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(CourseFilter.ALL) }
    var editor by remember { mutableStateOf<AdminCourseLocal?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }

    fun filteredCourses(): List<AdminCourseLocal> {
        val q = query.trim().lowercase()
        return courses.filter { c ->
            val passFilter = when (filter) {
                CourseFilter.ALL -> true
                else -> c.status.equals(filter.value, ignoreCase = true)
            }
            val passSearch = q.isBlank() ||
                c.name.lowercase().contains(q) ||
                c.level.orEmpty().lowercase().contains(q) ||
                c.description.orEmpty().lowercase().contains(q)
            passFilter && passSearch
        }
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            saveError = null
            runCatching {
                val centers = remoteRepo.listManagedDiveCenters()
                val first = centers.firstOrNull()?.id
                centerId = first
                if (first == null) {
                    emptyList()
                } else {
                    val remoteCourses = remoteRepo.listCoursesForCenter(first)
                    localRepo.mergeWithRemote(first, remoteCourses)
                }
            }.onSuccess { merged ->
                courses = merged
            }.onFailure { e ->
                error = e.message
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(IosDesign.ScreenPadding),
    ) {
        Text(
            stringResource(R.string.partner_courses_header),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.partner_courses_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        saveError?.let { msg ->
            Text(
                msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.partner_courses_search_label)) },
                singleLine = true,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
            }
            IconButton(onClick = { showCreateSheet = true }, enabled = centerId != null) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.partner_courses_create))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CourseFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = { Text(filterLabel(item)) },
                )
            }
        }
        when {
            loading -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
            courses.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.partner_courses_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                val filtered = filteredCourses()
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.partner_courses_empty_filtered),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = IosDesign.CardCorner,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                StatusBadge(status = c.status)
                            }
                            c.level.takeIf { !it.isNullOrBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            c.description.takeIf { !it.isNullOrBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            c.durationMinutes?.takeIf { it > 0 }?.let { dm ->
                                Text(
                                    stringResource(R.string.partner_courses_duration_line, dm),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editor = c }) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Text(stringResource(R.string.common_edit), modifier = Modifier.padding(start = 6.dp))
                                }
                                if (c.status.equals("archived", ignoreCase = true)) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                localRepo.upsert(c.copy(status = "active"))
                                                refresh()
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Default.Unarchive, contentDescription = null)
                                        Text(stringResource(R.string.partner_courses_unarchive), modifier = Modifier.padding(start = 6.dp))
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                localRepo.upsert(c.copy(status = "archived"))
                                                refresh()
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Default.Archive, contentDescription = null)
                                        Text(stringResource(R.string.partner_courses_archive), modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    if (showCreateSheet) {
        CourseEditorSheet(
            title = stringResource(R.string.partner_courses_create),
            initial = null,
            showDraftStatus = false,
            onDismiss = { showCreateSheet = false },
            onSave = { name, level, description, status, durationMinutes ->
                val cId = centerId ?: return@CourseEditorSheet
                scope.launch {
                    saveError = null
                    runCatching {
                        val body = CourseWriteRequestDto(
                            diveCenterId = cId,
                            name = name,
                            level = level?.trim()?.ifBlank { null } ?: "basic",
                            description = description?.trim().orEmpty(),
                            duration = durationMinutes,
                        )
                        val created = remoteRepo.createCourse(body)
                        localRepo.upsert(
                            AdminCourseLocal(
                                id = created.id,
                                diveCenterId = cId,
                                name = created.name,
                                level = created.level,
                                description = created.description,
                                status = status,
                                updatedAt = created.updatedAt ?: Instant.now().toString(),
                                durationMinutes = created.duration ?: durationMinutes,
                            ),
                        )
                    }.onSuccess {
                        showCreateSheet = false
                        refresh()
                    }.onFailure { e ->
                        saveError = e.message ?: e::class.java.simpleName
                    }
                }
            },
        )
    }

    editor?.let { selected ->
        CourseEditorSheet(
            title = stringResource(R.string.partner_courses_edit),
            initial = selected,
            showDraftStatus = selected.id.startsWith("local_"),
            onDismiss = { editor = null },
            onSave = { name, level, description, status, durationMinutes ->
                val cId = centerId ?: return@CourseEditorSheet
                scope.launch {
                    saveError = null
                    runCatching {
                        val levelNorm = level?.trim()?.ifBlank { null } ?: "basic"
                        val desc = description?.trim().orEmpty()
                        val body = CourseWriteRequestDto(
                            diveCenterId = cId,
                            name = name,
                            level = levelNorm,
                            description = desc,
                            duration = durationMinutes,
                        )
                        if (selected.id.startsWith("local_")) {
                            val created = remoteRepo.createCourse(body)
                            localRepo.removeCourse(selected.id, cId)
                            localRepo.upsert(
                                AdminCourseLocal(
                                    id = created.id,
                                    diveCenterId = cId,
                                    name = created.name,
                                    level = created.level,
                                    description = created.description,
                                    status = status,
                                    updatedAt = created.updatedAt ?: Instant.now().toString(),
                                    durationMinutes = created.duration ?: durationMinutes,
                                ),
                            )
                        } else {
                            val updated = remoteRepo.updateCourse(selected.id, body)
                            localRepo.upsert(
                                selected.copy(
                                    name = updated.name,
                                    level = updated.level,
                                    description = updated.description,
                                    status = status,
                                    durationMinutes = updated.duration ?: durationMinutes,
                                    updatedAt = updated.updatedAt ?: Instant.now().toString(),
                                ),
                            )
                        }
                    }.onSuccess {
                        editor = null
                        refresh()
                    }.onFailure { e ->
                        saveError = e.message ?: e::class.java.simpleName
                    }
                }
            },
        )
    }
}

@Composable
private fun filterLabel(filter: CourseFilter): String = when (filter) {
    CourseFilter.ALL -> stringResource(R.string.partner_courses_filter_all)
    CourseFilter.ACTIVE -> stringResource(R.string.partner_courses_filter_active)
    CourseFilter.DRAFT -> stringResource(R.string.partner_courses_filter_draft)
    CourseFilter.ARCHIVED -> stringResource(R.string.partner_courses_filter_archived)
}

@Composable
private fun StatusBadge(status: String) {
    val text = when {
        status.equals("archived", ignoreCase = true) -> stringResource(R.string.partner_courses_filter_archived)
        status.equals("draft", ignoreCase = true) -> stringResource(R.string.partner_courses_filter_draft)
        else -> stringResource(R.string.partner_courses_filter_active)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseEditorSheet(
    title: String,
    initial: AdminCourseLocal?,
    showDraftStatus: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (name: String, level: String?, description: String?, status: String, durationMinutes: Int) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var level by remember(initial?.id) { mutableStateOf(initial?.level.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var durationText by remember(initial?.id) {
        mutableStateOf(initial?.durationMinutes?.takeIf { it > 0 }?.toString() ?: "120")
    }
    var status by remember(initial?.id) { mutableStateOf(initial?.status ?: "active") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.partner_courses_field_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = level,
                onValueChange = { level = it },
                label = { Text(stringResource(R.string.partner_courses_field_level)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.partner_courses_field_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            OutlinedTextField(
                value = durationText,
                onValueChange = { durationText = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.partner_courses_field_duration)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text(stringResource(R.string.partner_courses_field_duration_hint)) },
            )
            Text(stringResource(R.string.partner_courses_field_status), fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CourseFilter.entries
                    .filter { it != CourseFilter.ALL }
                    .filter { showDraftStatus || it != CourseFilter.DRAFT }
                    .forEach { option ->
                    FilterChip(
                        selected = status.equals(option.value, ignoreCase = true),
                        onClick = { status = option.value },
                        label = { Text(filterLabel(option)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = {
                        val dm = durationText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        onSave(
                            name.trim(),
                            level.trim().ifBlank { null },
                            description.trim().ifBlank { null },
                            status,
                            dm,
                        )
                    },
                    enabled = name.trim().isNotBlank() &&
                        (durationText.toIntOrNull()?.let { it >= 1 } == true),
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
