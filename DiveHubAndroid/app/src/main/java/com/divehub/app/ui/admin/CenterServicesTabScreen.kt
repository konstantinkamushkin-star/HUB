package com.divehub.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.divehub.app.data.remote.dto.CenterServiceDto
import com.divehub.app.data.remote.dto.CreateCenterServiceDto
import com.divehub.app.data.remote.dto.UpdateCenterServiceDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.ui.main.SessionViewModel
import com.divehub.app.ui.theme.IosDesign
import com.divehub.app.ui.theme.diveHubTopAppBarColors
import com.divehub.app.ui.theme.iosChromePageBackground
import java.util.Locale
import kotlinx.coroutines.launch

private val serviceTypeOptions = listOf(
    "fun_dive",
    "package",
    "night_dive",
    "pool_session",
    "equipment_rental",
    "course",
    "other",
)

private data class ServiceEditorDraft(
    val editingId: String? = null,
    val name: String = "",
    val description: String = "",
    val serviceType: String = "fun_dive",
    val basePriceAmount: String = "",
    val currency: String = "USD",
    val pricingUnit: String = "per_person",
    val durationMinutes: String = "0",
    val maxParticipants: String = "0",
    val includedItemsText: String = "",
    val requirementsText: String = "",
    val ownGearDiscountPercent: String = "",
    val nightDiveSurchargeAmount: String = "",
    val privateInstructorSurchargeAmount: String = "",
    val groupDiscountThreshold: String = "",
    val groupDiscountPercent: String = "",
    val isActive: Boolean = true,
)

private fun CenterServiceDto.toEditorDraft(): ServiceEditorDraft = ServiceEditorDraft(
    editingId = id,
    name = name,
    description = description.orEmpty(),
    serviceType = type?.trim()?.ifEmpty { "fun_dive" } ?: "fun_dive",
    basePriceAmount = String.format(Locale.US, "%.2f", price?.amount ?: 0.0),
    currency = price?.currency?.trim()?.ifEmpty { "USD" } ?: "USD",
    pricingUnit = pricingUnit?.trim()?.ifBlank { "per_person" } ?: "per_person",
    durationMinutes = duration.toString(),
    maxParticipants = maxParticipants.toString(),
    includedItemsText = includedItems.orEmpty().joinToString("\n"),
    requirementsText = requirements.orEmpty().joinToString("\n"),
    ownGearDiscountPercent = ownGearDiscountPercent?.let { String.format(Locale.US, "%.2f", it) } ?: "",
    nightDiveSurchargeAmount = nightDiveSurchargeAmount?.let { String.format(Locale.US, "%.2f", it) } ?: "",
    privateInstructorSurchargeAmount = privateInstructorSurchargeAmount?.let { String.format(Locale.US, "%.2f", it) } ?: "",
    groupDiscountThreshold = groupDiscountThreshold?.toString().orEmpty(),
    groupDiscountPercent = groupDiscountPercent?.let { String.format(Locale.US, "%.2f", it) } ?: "",
    isActive = isActive,
)

private fun parseTextList(value: String): List<String> =
    value.lines().map { it.trim() }.filter { it.isNotEmpty() }

private suspend fun resolveDiveCenterIdOrFirstManaged(graph: AppGraph, user: UserDto?): String? {
    val direct = user?.diveCenterId?.trim()?.takeIf { it.isNotEmpty() }
    if (direct != null) return direct
    return runCatching { TripsRepository(graph).listManagedDiveCenters() }
        .getOrNull()
        ?.firstOrNull()
        ?.id
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private fun userFacingServicesError(graph: AppGraph, raw: String): String {
    val lowered = raw.lowercase(Locale.ROOT)
    if (lowered.contains("center-services") || lowered.contains("center_services") ||
        lowered.contains("relation") && lowered.contains("does not exist")
    ) {
        return graph.application.getString(R.string.admin_services_backend_unavailable)
    }
    return raw
}

@Composable
private fun ServiceSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun ServiceGroupedCard(content: @Composable () -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenterServiceEditorScreen(
    draft: ServiceEditorDraft,
    saving: Boolean,
    error: String?,
    onDraftChange: (ServiceEditorDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var pricingUnitMenuExpanded by remember { mutableStateOf(false) }
    val title = if (draft.editingId == null) {
        stringResource(R.string.admin_services_new)
    } else {
        stringResource(R.string.admin_services_edit)
    }
    val serviceTypeDisplay = remember(draft.serviceType) {
        when (draft.serviceType) {
            "fun_dive" -> "Развлекательное погружение"
            "package" -> "Пакет"
            "night_dive" -> "Ночное погружение"
            "pool_session" -> "Бассейн"
            "equipment_rental" -> "Аренда снаряжения"
            "course" -> "Курс"
            else -> "Другое"
        }
    }
    val pricingUnitDisplay = remember(draft.pricingUnit) {
        when (draft.pricingUnit) {
            "per_person" -> "за человека"
            "per_group" -> "за группу"
            "per_hour" -> "за час"
            "per_day" -> "за день"
            else -> draft.pricingUnit
        }
    }
    Scaffold(
        containerColor = iosChromePageBackground(),
        topBar = {
            CenterAlignedTopAppBar(
                colors = diveHubTopAppBarColors(),
                navigationIcon = {
                    TextButton(onClick = onCancel, enabled = !saving) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
                title = { Text("") },
                actions = {
                    TextButton(onClick = onSave, enabled = !saving) {
                        Text(stringResource(R.string.common_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            error?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ServiceSectionLabel(stringResource(R.string.admin_services_section_basic))
            ServiceGroupedCard {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { onDraftChange(draft.copy(description = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_field_description)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { if (!saving) typeMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = serviceTypeDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.admin_services_field_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !saving,
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                    ) {
                        serviceTypeOptions.forEach { opt ->
                            val label = when (opt) {
                                "fun_dive" -> "Развлекательное погружение"
                                "package" -> "Пакет"
                                "night_dive" -> "Ночное погружение"
                                "pool_session" -> "Бассейн"
                                "equipment_rental" -> "Аренда снаряжения"
                                "course" -> "Курс"
                                else -> "Другое"
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onDraftChange(draft.copy(serviceType = opt))
                                    typeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            ServiceSectionLabel(stringResource(R.string.admin_services_section_pricing))
            ServiceGroupedCard {
                OutlinedTextField(
                    value = draft.basePriceAmount,
                    onValueChange = { onDraftChange(draft.copy(basePriceAmount = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_field_price)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ExposedDropdownMenuBox(
                    expanded = currencyMenuExpanded,
                    onExpandedChange = { if (!saving) currencyMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = draft.currency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.admin_services_field_currency)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyMenuExpanded) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !saving,
                    )
                    ExposedDropdownMenu(
                        expanded = currencyMenuExpanded,
                        onDismissRequest = { currencyMenuExpanded = false },
                    ) {
                        listOf("USD", "EUR", "RUB").forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = {
                                    onDraftChange(draft.copy(currency = opt))
                                    currencyMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ExposedDropdownMenuBox(
                    expanded = pricingUnitMenuExpanded,
                    onExpandedChange = { if (!saving) pricingUnitMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = pricingUnitDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.admin_services_field_pricing_unit)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pricingUnitMenuExpanded) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !saving,
                    )
                    ExposedDropdownMenu(
                        expanded = pricingUnitMenuExpanded,
                        onDismissRequest = { pricingUnitMenuExpanded = false },
                    ) {
                        listOf("per_person", "per_group", "per_hour", "per_day").forEach { opt ->
                            val label = when (opt) {
                                "per_person" -> "за человека"
                                "per_group" -> "за группу"
                                "per_hour" -> "за час"
                                else -> "за день"
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onDraftChange(draft.copy(pricingUnit = opt))
                                    pricingUnitMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            ServiceSectionLabel(stringResource(R.string.admin_services_section_details))
            ServiceGroupedCard {
                OutlinedTextField(
                    value = draft.durationMinutes,
                    onValueChange = { onDraftChange(draft.copy(durationMinutes = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_field_duration)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                OutlinedTextField(
                    value = draft.maxParticipants,
                    onValueChange = { onDraftChange(draft.copy(maxParticipants = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_field_max_participants)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.admin_services_field_active))
                    Switch(
                        checked = draft.isActive,
                        onCheckedChange = { onDraftChange(draft.copy(isActive = it)) },
                        enabled = !saving,
                    )
                }
            }

            ServiceSectionLabel(stringResource(R.string.admin_services_section_extra))
            ServiceGroupedCard {
                OutlinedTextField(
                    value = draft.includedItemsText,
                    onValueChange = { onDraftChange(draft.copy(includedItemsText = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_field_included)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                Text(
                    stringResource(R.string.admin_services_one_item_per_line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            ServiceSectionLabel(stringResource(R.string.admin_services_section_requirements))
            ServiceGroupedCard {
                OutlinedTextField(
                    value = draft.requirementsText,
                    onValueChange = { onDraftChange(draft.copy(requirementsText = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_field_requirements)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                Text(
                    stringResource(R.string.admin_services_one_item_per_line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            ServiceSectionLabel(stringResource(R.string.admin_services_section_rules))
            ServiceGroupedCard {
                OutlinedTextField(
                    value = draft.ownGearDiscountPercent,
                    onValueChange = { onDraftChange(draft.copy(ownGearDiscountPercent = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_rule_own_gear_discount)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                OutlinedTextField(
                    value = draft.nightDiveSurchargeAmount,
                    onValueChange = { onDraftChange(draft.copy(nightDiveSurchargeAmount = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_rule_night_surcharge)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                OutlinedTextField(
                    value = draft.privateInstructorSurchargeAmount,
                    onValueChange = { onDraftChange(draft.copy(privateInstructorSurchargeAmount = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_rule_private_instructor)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                OutlinedTextField(
                    value = draft.groupDiscountThreshold,
                    onValueChange = { onDraftChange(draft.copy(groupDiscountThreshold = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_rule_group_threshold)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                OutlinedTextField(
                    value = draft.groupDiscountPercent,
                    onValueChange = { onDraftChange(draft.copy(groupDiscountPercent = it)) },
                    placeholder = { Text(stringResource(R.string.admin_services_rule_group_discount)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
            }
            if (saving) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterServicesTabRoute(graph: AppGraph, sessionVm: SessionViewModel) {
    val user by sessionVm.user.collectAsState()
    var includeInactive by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var services by remember { mutableStateOf<List<CenterServiceDto>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<CenterServiceDto?>(null) }
    var editorDraft by remember { mutableStateOf<ServiceEditorDraft?>(null) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var savingEditor by remember { mutableStateOf(false) }
    var resolvedCenterId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var loadGen by remember { mutableStateOf(0) }

    LaunchedEffect(user?.id, includeInactive, loadGen) {
        loading = loadGen == 0
        if (loadGen > 0) refreshing = true
        error = null
        val centerId = runCatching { resolveDiveCenterIdOrFirstManaged(graph, user) }.getOrNull()
        resolvedCenterId = centerId
        if (centerId.isNullOrBlank()) {
            services = emptyList()
            error = graph.application.getString(R.string.dive_center_admin_no_center)
            loading = false
            refreshing = false
            return@LaunchedEffect
        }
        runCatching {
            graph.centerServicesApi().listByCenter(centerId, if (includeInactive) "true" else null)
        }
            .onSuccess {
                services = it
                error = null
            }
            .onFailure {
                services = emptyList()
                error = userFacingServicesError(graph, it.message ?: it::class.java.simpleName)
            }
        loading = false
        refreshing = false
    }

    pendingDelete?.let { toDel ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.admin_services_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = toDel.id
                    pendingDelete = null
                    scope.launch {
                        runCatching { graph.centerServicesApi().deleteService(id) }
                            .onSuccess { loadGen++ }
                            .onFailure {
                                error = userFacingServicesError(graph, it.message ?: it::class.java.simpleName)
                            }
                    }
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    val draft = editorDraft
    if (draft != null) {
        val errNoCenter = stringResource(R.string.dive_center_admin_no_center)
        val errName = stringResource(R.string.admin_services_err_name)
        val errPrice = stringResource(R.string.admin_services_err_price)
        val errDuration = stringResource(R.string.admin_services_err_duration)
        val errMaxParticipants = stringResource(R.string.admin_services_err_max_participants)
        CenterServiceEditorScreen(
            draft = draft,
            saving = savingEditor,
            error = editorError,
            onDraftChange = { editorDraft = it },
            onCancel = {
                if (!savingEditor) {
                    editorDraft = null
                    editorError = null
                }
            },
            onSave = {
                val centerId = resolvedCenterId
                if (centerId.isNullOrBlank()) {
                    editorError = errNoCenter
                    return@CenterServiceEditorScreen
                }
                val name = draft.name.trim()
                val price = draft.basePriceAmount.trim().toDoubleOrNull()
                val duration = draft.durationMinutes.trim().toIntOrNull() ?: 0
                val maxParticipants = draft.maxParticipants.trim().toIntOrNull() ?: 0
                val ownGearDiscount = draft.ownGearDiscountPercent.trim().toDoubleOrNull()
                val nightSurcharge = draft.nightDiveSurchargeAmount.trim().toDoubleOrNull()
                val privateInstructorSurcharge = draft.privateInstructorSurchargeAmount.trim().toDoubleOrNull()
                val groupThreshold = draft.groupDiscountThreshold.trim().toIntOrNull()
                val groupDiscount = draft.groupDiscountPercent.trim().toDoubleOrNull()
                if (name.isEmpty()) {
                    editorError = errName
                    return@CenterServiceEditorScreen
                }
                if (price == null || price < 0.0) {
                    editorError = errPrice
                    return@CenterServiceEditorScreen
                }
                if (duration < 0) {
                    editorError = errDuration
                    return@CenterServiceEditorScreen
                }
                if (maxParticipants < 0) {
                    editorError = errMaxParticipants
                    return@CenterServiceEditorScreen
                }
                savingEditor = true
                editorError = null
                scope.launch {
                    val result = if (draft.editingId == null) {
                        runCatching {
                            graph.centerServicesApi().createService(
                                CreateCenterServiceDto(
                                    diveCenterId = centerId,
                                    name = name,
                                    description = draft.description.trim().ifBlank { null },
                                    serviceType = draft.serviceType.trim().ifBlank { "fun_dive" },
                                    basePriceAmount = price,
                                    currency = draft.currency.trim().ifBlank { "USD" },
                                    pricingUnit = draft.pricingUnit.trim().ifBlank { "per_person" },
                                    durationMinutes = duration,
                                    maxParticipants = maxParticipants,
                                    requirements = parseTextList(draft.requirementsText),
                                    includedItems = parseTextList(draft.includedItemsText),
                                    ownGearDiscountPercent = ownGearDiscount,
                                    nightDiveSurchargeAmount = nightSurcharge,
                                    privateInstructorSurchargeAmount = privateInstructorSurcharge,
                                    groupDiscountThreshold = groupThreshold,
                                    groupDiscountPercent = groupDiscount,
                                    isActive = draft.isActive,
                                ),
                            )
                        }
                    } else {
                        runCatching {
                            graph.centerServicesApi().updateService(
                                draft.editingId,
                                UpdateCenterServiceDto(
                                    name = name,
                                    description = draft.description.trim().ifBlank { null },
                                    serviceType = draft.serviceType.trim().ifBlank { "fun_dive" },
                                    basePriceAmount = price,
                                    currency = draft.currency.trim().ifBlank { "USD" },
                                    pricingUnit = draft.pricingUnit.trim().ifBlank { "per_person" },
                                    durationMinutes = duration,
                                    maxParticipants = maxParticipants,
                                    requirements = parseTextList(draft.requirementsText),
                                    includedItems = parseTextList(draft.includedItemsText),
                                    ownGearDiscountPercent = ownGearDiscount,
                                    nightDiveSurchargeAmount = nightSurcharge,
                                    privateInstructorSurchargeAmount = privateInstructorSurcharge,
                                    groupDiscountThreshold = groupThreshold,
                                    groupDiscountPercent = groupDiscount,
                                    isActive = draft.isActive,
                                ),
                            )
                        }
                    }
                    result
                        .onSuccess {
                            editorDraft = null
                            editorError = null
                            loadGen++
                        }
                        .onFailure {
                            editorError = userFacingServicesError(graph, it.message ?: it::class.java.simpleName)
                        }
                    savingEditor = false
                }
            },
        )
        return
    }

    Scaffold(
        containerColor = iosChromePageBackground(),
        topBar = {
            CenterAlignedTopAppBar(
                colors = diveHubTopAppBarColors(),
                title = { Text(stringResource(R.string.partner_tab_services), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = {
                        editorError = null
                        editorDraft = ServiceEditorDraft()
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.admin_services_new))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { loadGen++ },
            modifier = Modifier
                .fillMaxSize()
                .background(IosDesign.Profile.pageBackground)
                .padding(padding),
        ) {
            when {
                loading && services.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ServiceGroupedCard {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.admin_services_include_inactive), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = includeInactive, onCheckedChange = { includeInactive = it })
                            }
                        }
                    }
                    error?.takeIf { it.isNotBlank() }?.let { err ->
                        item {
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    item { ServiceSectionLabel(stringResource(R.string.admin_services_pricing)) }
                    if (services.isEmpty() && error == null && !loading) {
                        item {
                            ServiceGroupedCard {
                                Text(
                                    stringResource(R.string.admin_services_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                                TextButton(
                                    onClick = {
                                        editorError = null
                                        editorDraft = ServiceEditorDraft()
                                    },
                                ) {
                                    Text(stringResource(R.string.admin_services_new))
                                }
                            }
                        }
                    }
                    items(services, key = { it.id }) { s ->
                        ServiceGroupedCard {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    editorError = null
                                    editorDraft = s.toEditorDraft()
                                },
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (s.isActive) stringResource(R.string.admin_service_status_active)
                                            else stringResource(R.string.admin_service_status_inactive),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (s.isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    s.type?.takeIf { it.isNotBlank() }?.let {
                                        Text(it.replace('_', ' '), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    s.description?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    val p = s.price
                                    val line = if (p != null) {
                                        String.format(Locale.US, "%.2f %s · %d min", p.amount, p.currency, s.duration)
                                    } else {
                                        String.format(Locale.US, "— · %d min", s.duration)
                                    }
                                    Text(line, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { pendingDelete = s }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
