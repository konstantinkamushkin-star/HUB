package com.divehub.app.ui.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.PartnerCoursesRepository
import com.divehub.app.data.remote.dto.AdminCourseLocal
import com.divehub.app.data.remote.dto.CourseModuleWriteDto
import com.divehub.app.data.remote.dto.CourseWriteRequestDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.ui.theme.IosDesign
import com.divehub.app.ui.theme.diveHubTopAppBarColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import androidx.compose.runtime.rememberCoroutineScope

private data class LevelOption(val api: String, val labelRes: Int)

private val levelOptions = listOf(
    LevelOption("basic", R.string.partner_courses_level_basic),
    LevelOption("advanced", R.string.partner_courses_level_advanced),
    LevelOption("professional", R.string.partner_courses_level_professional),
    LevelOption("technical", R.string.partner_courses_level_technical),
    LevelOption("specialization", R.string.partner_courses_level_specialization),
)

private val availableSystems = listOf("PADI", "SSI", "NAUI", "CMAS", "BSAC", "SDI", "TDI")

private val moduleTypeOptions = listOf(
    "theory" to R.string.partner_courses_module_type_theory,
    "confined_water" to R.string.partner_courses_module_type_confined,
    "open_water" to R.string.partner_courses_module_type_openwater,
    "exam" to R.string.partner_courses_module_type_exam,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerCourseEditorRoute(
    graph: AppGraph,
    innerNav: NavController,
    courseId: String? = null,
) {
    val scope = rememberCoroutineScope()
    val remoteRepo = remember { TripsRepository(graph) }
    val localRepo = remember { PartnerCoursesRepository(graph) }
    val isEdit = !courseId.isNullOrBlank()

    var centerId by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    var name by remember { mutableStateOf("") }
    var levelKey by remember { mutableStateOf("basic") }
    var description by remember { mutableStateOf("") }
    var durationDays by remember { mutableStateOf(1) }
    var trainingSystems by remember { mutableStateOf<List<String>>(emptyList()) }
    var systemMenuExpanded by remember { mutableStateOf(false) }
    var selectedSystemToAdd by remember { mutableStateOf("") }
    var prerequisites by remember { mutableStateOf<List<String>>(emptyList()) }
    var modules by remember { mutableStateOf<List<CourseModuleWriteDto>>(emptyList()) }
    var selectedInstructorIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var instructors by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var loadingInstructors by remember { mutableStateOf(false) }

    var showPrereqDialog by remember { mutableStateOf(false) }
    var prereqDraft by remember { mutableStateOf("") }
    var showModuleDialog by remember { mutableStateOf(false) }
    var moduleTitleDraft by remember { mutableStateOf("") }
    var moduleTypeKey by remember { mutableStateOf("theory") }
    var moduleHours by remember { mutableStateOf(1) }

    var showLevelMenu by remember { mutableStateOf(false) }

    fun canSave(): Boolean =
        name.trim().isNotEmpty() &&
            description.trim().isNotEmpty() &&
            durationDays in 1..30 &&
            centerId != null

    LaunchedEffect(courseId) {
        pageLoading = true
        loadError = null
        runCatching { remoteRepo.listManagedDiveCenters() }
            .onSuccess { list ->
                centerId = list.firstOrNull()?.id
            }
            .onFailure { e ->
                loadError = e.message
                centerId = null
            }
        if (centerId == null) {
            pageLoading = false
            return@LaunchedEffect
        }
        if (isEdit) {
            val id = courseId ?: return@LaunchedEffect
            runCatching { remoteRepo.getCourse(id) }
                .onSuccess { d ->
                    name = d.name
                    levelKey = d.level?.lowercase() ?: "basic"
                    description = d.description.orEmpty()
                    durationDays = (d.duration ?: 1).coerceIn(1, 30)
                    trainingSystems = d.trainingSystems.orEmpty()
                    prerequisites = d.prerequisites.orEmpty()
                    modules = d.modules.orEmpty().mapIndexed { i, m ->
                        CourseModuleWriteDto(
                            id = m.id,
                            title = m.title,
                            description = m.description,
                            duration = m.duration?.coerceIn(1, 24) ?: 1,
                            moduleType = m.moduleType.ifBlank { "theory" },
                            order = m.order ?: i,
                        )
                    }
                    selectedInstructorIds = d.instructorIds.orEmpty().toSet()
                }
                .onFailure { e -> loadError = e.message }
        } else {
            name = ""
            levelKey = "basic"
            description = ""
            durationDays = 1
            trainingSystems = emptyList()
            prerequisites = emptyList()
            modules = emptyList()
            selectedInstructorIds = emptySet()
        }
        val cId = centerId
        if (cId != null) {
            loadingInstructors = true
            runCatching { remoteRepo.listInstructorsForCenter(cId) }
                .onSuccess { instructors = it }
                .onFailure { instructors = emptyList() }
            loadingInstructors = false
        }
        pageLoading = false
    }

    fun mergeListFromRemote() {
        val cId = centerId ?: return
        scope.launch {
            runCatching {
                val remote = remoteRepo.listCoursesForCenter(cId)
                localRepo.mergeWithRemote(cId, remote)
            }
        }
    }

    fun onSave() {
        val cId = centerId ?: return
        if (!canSave()) return
        scope.launch {
            saving = true
            saveError = null
            val cleaned = modules
                .filter { it.title.trim().isNotEmpty() }
                .mapIndexed { i, m -> m.copy(order = i) }
            val body = CourseWriteRequestDto(
                diveCenterId = cId,
                name = name.trim(),
                level = levelKey,
                description = description.trim(),
                duration = durationDays,
                trainingSystems = trainingSystems,
                modules = cleaned,
                prerequisites = prerequisites,
                instructorIds = selectedInstructorIds.toList().sorted(),
            )
            val result = if (isEdit) {
                val id = courseId ?: return@launch
                runCatching { remoteRepo.updateCourse(id, body) }
            } else {
                runCatching { remoteRepo.createCourse(body) }
            }
            result
                .onSuccess { res ->
                    localRepo.upsert(
                        AdminCourseLocal(
                            id = res.id,
                            diveCenterId = cId,
                            name = res.name,
                            level = res.level,
                            description = res.description,
                            status = "active",
                            updatedAt = res.updatedAt ?: Instant.now().toString(),
                            durationMinutes = res.duration,
                        ),
                    )
                    mergeListFromRemote()
                    innerNav.popBackStack()
                }
                .onFailure { e ->
                    saveError = e.message
                }
            saving = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = diveHubTopAppBarColors(),
                navigationIcon = {
                    TextButton(
                        onClick = { innerNav.popBackStack() },
                        enabled = !saving,
                    ) { Text(stringResource(R.string.common_cancel)) }
                },
                title = {
                    Text(
                        stringResource(
                            if (isEdit) R.string.partner_courses_edit else R.string.partner_courses_create,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    TextButton(
                        onClick = { onSave() },
                        enabled = !saving && canSave(),
                    ) { Text(stringResource(R.string.common_save)) }
                },
            )
        },
    ) { padding ->
        when {
            pageLoading -> BoxCentered(Modifier.padding(padding)) { CircularProgressIndicator() }
            loadError != null && centerId == null -> BoxCentered(Modifier.padding(padding)) {
                Text(loadError ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error)
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(IosDesign.Profile.pageBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                saveError?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                IosFormSectionLabel(stringResource(R.string.partner_courses_section_basic))
                IosGroupedCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.partner_courses_field_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    ExposedDropdownMenuBox(
                        expanded = showLevelMenu,
                        onExpandedChange = { showLevelMenu = it },
                    ) {
                        val levelLabel = levelOptions.find { it.api == levelKey }?.labelRes
                            ?: R.string.partner_courses_level_basic
                        OutlinedTextField(
                            value = stringResource(levelLabel),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.partner_courses_field_level)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showLevelMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = showLevelMenu,
                            onDismissRequest = { showLevelMenu = false },
                        ) {
                            levelOptions.forEach { opt ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(opt.labelRes)) },
                                    onClick = {
                                        levelKey = opt.api
                                        showLevelMenu = false
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.partner_courses_field_description)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(
                                R.string.partner_courses_duration_label,
                            ) + ": " + stringResource(
                                R.string.partner_courses_duration_days_count,
                                durationDays,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(20.dp),
                                )
                                .padding(horizontal = 4.dp),
                        ) {
                            IconButton(
                                onClick = { if (durationDays > 1) durationDays-- },
                            ) { Icon(Icons.Default.Remove, null) }
                            androidx.compose.material3.VerticalDivider(Modifier.height(24.dp))
                            IconButton(
                                onClick = { if (durationDays < 30) durationDays++ },
                            ) { Icon(Icons.Default.Add, null) }
                        }
                    }
                }

                IosFormSectionLabel(stringResource(R.string.partner_courses_training_systems))
                IosGroupedCard {
                    trainingSystems.forEach { sys ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sys, style = MaterialTheme.typography.bodyLarge)
                            TextButton(
                                onClick = {
                                    trainingSystems = trainingSystems.filter { it != sys }
                                },
                            ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = systemMenuExpanded,
                        onExpandedChange = { systemMenuExpanded = it },
                    ) {
                        val pickLabel = availableSystems.firstOrNull { !trainingSystems.contains(it) }
                        OutlinedTextField(
                            value = if (selectedSystemToAdd.isNotEmpty()) {
                                selectedSystemToAdd
                            } else {
                                stringResource(R.string.partner_courses_add_training_system)
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.partner_courses_select_system)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = systemMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .clickable {
                                    if (pickLabel == null) return@clickable
                                    systemMenuExpanded = true
                                },
                        )
                        ExposedDropdownMenu(
                            expanded = systemMenuExpanded,
                            onDismissRequest = { systemMenuExpanded = false },
                        ) {
                            availableSystems.filter { !trainingSystems.contains(it) }.forEach { sys ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(sys) },
                                    onClick = {
                                        trainingSystems = trainingSystems + sys
                                        systemMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                IosFormSectionLabel(stringResource(R.string.partner_courses_instructors_section))
                IosGroupedCard {
                    when {
                        loadingInstructors -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.partner_courses_instructors_loading),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        instructors.isEmpty() -> Text(
                            stringResource(R.string.partner_courses_instructors_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> instructors.forEach { inst ->
                            val checked = inst.id in selectedInstructorIds
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    inst.displayName(),
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        selectedInstructorIds = if (on) {
                                            selectedInstructorIds + inst.id
                                        } else {
                                            selectedInstructorIds - inst.id
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.partner_courses_instructors_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                IosFormSectionLabel(stringResource(R.string.partner_courses_prerequisites))
                IosGroupedCard {
                    prerequisites.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(p, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { prerequisites = prerequisites.filter { it != p } },
                            ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    TextButton(
                        onClick = {
                            prereqDraft = ""
                            showPrereqDialog = true
                        },
                    ) { Text(stringResource(R.string.partner_courses_add_prerequisite)) }
                }

                IosFormSectionLabel(stringResource(R.string.partner_courses_modules))
                IosGroupedCard {
                    modules.forEachIndexed { index, m ->
                        val typeLabel = moduleTypeOptions.find { it.first == m.moduleType }?.second
                            ?: R.string.partner_courses_module_type_theory
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { /* optional edit */ },
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(typeLabel) + " · " + m.duration + "h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val l = modules.toMutableList()
                                            l[index] = l[index - 1].also { l[index - 1] = l[index] }
                                            modules = l
                                        }
                                    },
                                    enabled = index > 0,
                                ) { Icon(Icons.Default.KeyboardArrowUp, null) }
                                IconButton(
                                    onClick = {
                                        if (index < modules.lastIndex) {
                                            val l = modules.toMutableList()
                                            l[index] = l[index + 1].also { l[index + 1] = l[index] }
                                            modules = l
                                        }
                                    },
                                    enabled = index < modules.lastIndex,
                                ) { Icon(Icons.Default.KeyboardArrowDown, null) }
                            }
                        }
                        if (index < modules.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                    TextButton(
                        onClick = {
                            moduleTitleDraft = ""
                            moduleTypeKey = "theory"
                            moduleHours = 1
                            showModuleDialog = true
                        },
                    ) { Text(stringResource(R.string.partner_courses_add_module)) }
                }
            }
        }
    }

    if (showPrereqDialog) {
        AlertDialog(
            onDismissRequest = { showPrereqDialog = false },
            title = { Text(stringResource(R.string.partner_courses_prerequisite_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = prereqDraft,
                    onValueChange = { prereqDraft = it },
                    label = { Text(stringResource(R.string.partner_courses_prerequisite_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = prereqDraft.trim()
                        if (t.isNotEmpty()) prerequisites = prerequisites + t
                        showPrereqDialog = false
                    },
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPrereqDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showModuleDialog) {
        AlertDialog(
            onDismissRequest = { showModuleDialog = false },
            title = { Text(stringResource(R.string.partner_courses_add_module)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = moduleTitleDraft,
                        onValueChange = { moduleTitleDraft = it },
                        label = { Text(stringResource(R.string.partner_courses_module_title)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    var typeMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = typeMenu,
                        onExpandedChange = { typeMenu = it },
                    ) {
                        val tRes = moduleTypeOptions.find { it.first == moduleTypeKey }?.second
                            ?: R.string.partner_courses_module_type_theory
                        OutlinedTextField(
                            value = stringResource(tRes),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.partner_courses_module_type)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = typeMenu,
                            onDismissRequest = { typeMenu = false },
                        ) {
                            moduleTypeOptions.forEach { (k, r) ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(r)) },
                                    onClick = {
                                        moduleTypeKey = k
                                        typeMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.partner_courses_module_duration_hours) + ": $moduleHours")
                        Spacer(Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(20.dp),
                                )
                                .padding(horizontal = 4.dp),
                        ) {
                            IconButton(
                                onClick = { if (moduleHours > 1) moduleHours-- },
                            ) { Icon(Icons.Default.Remove, null) }
                            androidx.compose.material3.VerticalDivider(Modifier.height(24.dp))
                            IconButton(
                                onClick = { if (moduleHours < 24) moduleHours++ },
                            ) { Icon(Icons.Default.Add, null) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = moduleTitleDraft.trim()
                        if (t.isNotEmpty()) {
                            modules = modules + CourseModuleWriteDto(
                                id = UUID.randomUUID().toString(),
                                title = t,
                                description = "",
                                duration = moduleHours,
                                moduleType = moduleTypeKey,
                                order = modules.size,
                            )
                        }
                        showModuleDialog = false
                    },
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showModuleDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun IosFormSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun IosGroupedCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}

@Composable
private fun BoxCentered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
