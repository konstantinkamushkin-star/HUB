package com.divehub.app.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.data.remote.dto.ExploreItemKind
import com.divehub.app.ui.localization.localizedString
import com.divehub.app.ui.theme.IosDesign
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val ScreenBg = Color(0xFFF2F2F7)
private val timeSlots = listOf("09:00", "11:00", "13:00", "15:00")
private val poolTimeSlots = listOf("08:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingWizardRoute(
    graph: AppGraph,
    innerNav: NavController,
    centerIdArg: String?,
    siteIdArg: String?,
    instructorIdArg: String?,
    courseIdArg: String? = null,
) {
    val vm: BookingWizardViewModel = viewModel(
        factory = BookingWizardViewModel.factory(graph, centerIdArg, siteIdArg, instructorIdArg, courseIdArg),
    )
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(initialDisplayMode = DisplayMode.Picker)

    LaunchedEffect(state.submitSuccess, state.confirmationSummary) {
        val summary = state.confirmationSummary
        if (state.submitSuccess && summary != null) {
            graph.setPendingBookingConfirmation(summary, state.chatConversationId)
            vm.acknowledgeSubmitSuccess()
            innerNav.popBackStack()
            innerNav.navigate(InnerRoutes.BookingConfirmation)
        }
    }

    LaunchedEffect(state.submitError) {
        val err = state.submitError ?: return@LaunchedEffect
        snack.showSnackbar(err)
        vm.clearSubmitError()
    }

    Scaffold(
        containerColor = ScreenBg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Text(localizedString("booking", "ui_booking_n3412_n34212", R.string.booking_title))
                },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ScreenBg)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { vm.back() },
                    enabled = state.step > 0 && !state.submitLoading,
                ) {
                    Text(stringResource(R.string.booking_back))
                }
                if (state.step < state.totalSteps - 1) {
                    Button(
                        onClick = { vm.next() },
                        enabled = vm.canProceed() && !state.submitLoading,
                    ) {
                        Text(stringResource(R.string.booking_next))
                    }
                } else {
                    Button(
                        onClick = { vm.submit() },
                        enabled = vm.canProceed() && !state.submitLoading,
                    ) {
                        Text(stringResource(R.string.booking_confirm))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                stringResource(R.string.booking_step_format, state.step + 1, state.totalSteps),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                progress = { (state.step + 1).toFloat() / state.totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state.step) {
                    0 -> {
                        StepSelectBookingType(state, vm)
                        StepSelectCenter(state, vm)
                        StepSelectService(state, vm)
                    }
                    1 -> {
                        StepDateTime(state, vm, onPickDate = { showDatePicker = true }, onPickEndDate = { showEndDatePicker = true })
                        StepBookingPreferences(state, vm)
                        if (state.bookingType == BookingWizardType.OPEN_WATER) {
                            StepInstructor(state, vm)
                            StepDiveSite(state, vm)
                        }
                        StepGear(state, vm)
                    }
                    2 -> StepParticipants(state, vm)
                    3 -> StepReview(state)
                    4 -> StepPayment(state, vm)
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { vm.setDateMillis(it) }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showEndDatePicker) {
        val endDateState = rememberDatePickerState(initialDisplayMode = DisplayMode.Picker)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endDateState.selectedDateMillis?.let { vm.setEndDateMillis(it) }
                        showEndDatePicker = false
                    },
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = endDateState)
        }
    }
}

@Composable
private fun StepSelectBookingType(state: BookingWizardUiState, vm: BookingWizardViewModel) {
    Text(stringResource(R.string.booking_type_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        FilterChip(
            selected = state.bookingType == BookingWizardType.OPEN_WATER,
            onClick = { vm.setBookingType(BookingWizardType.OPEN_WATER) },
            label = { Text(stringResource(R.string.booking_type_open_water)) },
        )
        FilterChip(
            selected = state.bookingType == BookingWizardType.POOL,
            onClick = { vm.setBookingType(BookingWizardType.POOL) },
            label = { Text(stringResource(R.string.booking_type_pool)) },
        )
    }
}

@Composable
private fun StepSelectCenter(state: BookingWizardUiState, vm: BookingWizardViewModel) {
    Text(stringResource(R.string.booking_step_center), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.booking_step_center_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.centersLoading) {
        Text(stringResource(R.string.chat_loading))
    } else {
        state.centers.filter { it.kind == ExploreItemKind.DIVE_CENTER }.forEach { c ->
            SelectableCard(
                title = c.name,
                subtitle = listOfNotNull(c.region, c.country).filter { it.isNotBlank() }.joinToString(", "),
                selected = state.selectedCenterId == c.id,
                onClick = { vm.selectCenter(c.id) },
            )
        }
    }
}

@Composable
private fun StepSelectService(state: BookingWizardUiState, vm: BookingWizardViewModel) {
    Text(stringResource(R.string.booking_step_service), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    state.courseContextSummary?.let { summary ->
        Text(
            stringResource(R.string.booking_course_context_hint, summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    when {
        state.servicesLoading -> {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text(
                stringResource(R.string.booking_services_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.servicesError != null -> {
            Text(
                state.servicesError ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { vm.retryLoadServices() }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.common_retry))
            }
        }
        state.services.isEmpty() -> {
            Text(
                stringResource(R.string.booking_no_services_for_center),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            state.services.forEach { s ->
                val priceLine = when {
                    s.priceAmount <= 0.0 && s.durationMin <= 0 ->
                        stringResource(R.string.booking_price_on_request)
                    s.durationMin > 0 ->
                        "${formatMoney(s.priceAmount, s.currency)} · ${s.durationMin} min"
                    else -> formatMoney(s.priceAmount, s.currency)
                }
                val subtitle = listOfNotNull(s.subtitleExtra?.takeIf { it.isNotBlank() }, priceLine).joinToString(" · ")
                SelectableCard(
                    title = s.name,
                    subtitle = subtitle,
                    selected = state.selectedServiceId == s.id,
                    onClick = { vm.selectService(s.id) },
                )
            }
        }
    }
}

@Composable
private fun StepBookingPreferences(state: BookingWizardUiState, vm: BookingWizardViewModel) {
    Text(
        stringResource(R.string.booking_step_preferences),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.booking_participants_count, state.participantsCount))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.setParticipantsCount(state.participantsCount - 1) }) { Text("−") }
            OutlinedButton(onClick = { vm.setParticipantsCount(state.participantsCount + 1) }) { Text("+") }
        }
    }
    if (state.bookingType == BookingWizardType.OPEN_WATER) {
        OutlinedTextField(
            value = state.preferredInstructorLanguage,
            onValueChange = vm::setPreferredInstructorLanguage,
            label = { Text(stringResource(R.string.booking_instructor_language)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.instructorNotes,
            onValueChange = vm::setInstructorNotes,
            label = { Text(stringResource(R.string.booking_instructor_notes)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.booking_needs_private_instructor))
            Switch(checked = state.needsPrivateInstructor, onCheckedChange = vm::setNeedsPrivateInstructor)
        }
    } else {
        OutlinedTextField(
            value = state.poolPreferences,
            onValueChange = vm::setPoolPreferences,
            label = { Text(stringResource(R.string.booking_pool_preferences)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.booking_needs_equipment))
        Switch(checked = state.needsEquipmentRental, onCheckedChange = vm::setNeedsEquipmentRental)
    }
    OutlinedTextField(
        value = state.notes,
        onValueChange = vm::setNotes,
        label = { Text(stringResource(R.string.booking_general_notes)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
}

@Composable
private fun StepDateTime(
    state: BookingWizardUiState,
    vm: BookingWizardViewModel,
    onPickDate: () -> Unit,
    onPickEndDate: () -> Unit,
) {
    Text(stringResource(R.string.booking_step_datetime), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    val dateLabel = state.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
    } ?: stringResource(R.string.booking_pick_date)
    TextButton(onClick = onPickDate) {
        Text(
            if (state.bookingType == BookingWizardType.POOL) {
                stringResource(R.string.booking_pool_date, dateLabel)
            } else {
                stringResource(R.string.booking_selected_date, dateLabel)
            },
        )
    }
    if (state.bookingType == BookingWizardType.OPEN_WATER) {
        val endLabel = state.selectedEndDateMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        } ?: stringResource(R.string.booking_pick_date)
        TextButton(onClick = onPickEndDate) {
            Text(stringResource(R.string.booking_end_date, endLabel))
        }
        Text(stringResource(R.string.booking_time_slots), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            timeSlots.forEach { t ->
                FilterChip(
                    selected = state.selectedTime == t,
                    onClick = { vm.setTime(t) },
                    label = { Text(t) },
                )
            }
        }
    } else {
        Text(stringResource(R.string.booking_pool_time_slots), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            poolTimeSlots.forEach { t ->
                FilterChip(
                    selected = state.poolTime == t,
                    onClick = { vm.setPoolTime(t) },
                    label = { Text(t) },
                )
            }
        }
    }
}

@Composable
private fun StepInstructor(state: BookingWizardUiState, vm: BookingWizardViewModel) {
    Text(stringResource(R.string.booking_step_instructor), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.booking_instructor_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SelectableCard(
        title = stringResource(R.string.booking_instructor_any),
        subtitle = "",
        selected = state.selectedInstructorId == null,
        onClick = { vm.selectInstructor(null) },
    )
}

@Composable
private fun StepDiveSite(state: BookingWizardUiState, vm: BookingWizardViewModel) {
    Text(stringResource(R.string.booking_step_site), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (state.sitesLoading) {
        Text(stringResource(R.string.chat_loading))
    } else {
        SelectableCard(
            title = stringResource(R.string.booking_site_skip),
            subtitle = "",
            selected = state.selectedDiveSiteId == null,
            onClick = { vm.selectDiveSite(null) },
        )
        state.sites.filter { it.kind == ExploreItemKind.DIVE_SITE }.take(80).forEach { s ->
            SelectableCard(
                title = s.name,
                subtitle = s.country,
                selected = state.selectedDiveSiteId == s.id,
                onClick = { vm.selectDiveSite(s.id) },
            )
        }
    }
}

@Composable
private fun StepGear(state: BookingWizardUiState, vm: BookingWizardViewModel) {
    Text(stringResource(R.string.booking_step_gear), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (state.gearCatalog.isEmpty()) {
        Text(
            stringResource(R.string.booking_gear_none_available),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    state.gearCatalog.forEach { g ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(g.name, fontWeight = FontWeight.SemiBold)
                Text("${g.size} · $${"%.0f".format(g.price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = state.selectedGearIds.contains(g.id),
                onCheckedChange = { vm.toggleGear(g.id) },
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun StepParticipants(state: BookingWizardUiState, vm: BookingWizardViewModel) {
    Text(stringResource(R.string.booking_step_participants), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.booking_participants_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = state.participantDraftName,
        onValueChange = { vm.setParticipantDraft(it, state.participantDraftEmail) },
        label = { Text(stringResource(R.string.booking_participant_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.participantDraftEmail,
        onValueChange = { vm.setParticipantDraft(state.participantDraftName, it) },
        label = { Text(stringResource(R.string.booking_participant_email)) },
        placeholder = { Text(stringResource(R.string.booking_participant_email_optional)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { vm.addParticipant() },
        enabled = state.participantDraftName.trim().isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.booking_participant_add))
    }
    state.participants.forEach { p ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(p.name, fontWeight = FontWeight.SemiBold)
                p.email?.takeIf { it.isNotBlank() }?.let { em ->
                    Text(em, style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = { vm.removeParticipant(p.id) }) {
                Text(stringResource(R.string.common_delete))
            }
        }
    }
}

@Composable
private fun StepReview(state: BookingWizardUiState) {
    Text(
        stringResource(R.string.booking_step_review),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    val center = state.centers.find { it.id == state.selectedCenterId }
    val service = state.services.find { it.id == state.selectedServiceId }
    val dateLabel = state.selectedDateMillis?.let { ms ->
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } ?: "—"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            center?.name?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            service?.name?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.booking_confirmed_when, dateLabel, state.selectedTime))
            Text(stringResource(R.string.booking_confirmed_participants, state.participants.size.coerceAtLeast(1)))
            state.notes.takeIf { it.isNotBlank() }?.let { n ->
                Text(stringResource(R.string.booking_confirmed_notes, n))
            }
        }
    }
}

@Composable
private fun StepPayment(state: BookingWizardUiState, vm: BookingWizardViewModel) {
    Text(stringResource(R.string.booking_step_payment), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = state.paymentMethod == "online",
            onClick = { vm.setPaymentMethod("online") },
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Text(stringResource(R.string.booking_pay_online), modifier = Modifier.padding(start = 8.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = state.paymentMethod == "on_site",
            onClick = { vm.setPaymentMethod("on_site") },
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Text(stringResource(R.string.booking_pay_onsite), modifier = Modifier.padding(start = 8.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = state.paymentMethod == "google_pay",
            onClick = { vm.setPaymentMethod("google_pay") },
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Text(stringResource(R.string.booking_pay_google), modifier = Modifier.padding(start = 8.dp))
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.booking_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    val service = state.services.find { it.id == state.selectedServiceId }
    val payCurrency = service?.currency?.trim()?.takeIf { it.isNotEmpty() } ?: "USD"
    val gearTotal = state.gearCatalog.filter { state.selectedGearIds.contains(it.id) }.sumOf { it.price }
    val serviceRight = when {
        service == null -> "—"
        service.priceAmount <= 0.0 && service.durationMin <= 0 -> stringResource(R.string.booking_price_on_request)
        else -> formatMoney(service.priceAmount, service.currency)
    }
    val serviceAmountNumeric = when {
        service == null -> 0.0
        service.priceAmount <= 0.0 && service.durationMin <= 0 -> 0.0
        else -> service.priceAmount
    }
    val totalNumeric = serviceAmountNumeric + gearTotal
    BookingSummaryRow(
        label = stringResource(R.string.booking_summary_line_service),
        value = serviceRight,
    )
    BookingSummaryRow(
        label = stringResource(R.string.booking_summary_line_gear),
        value = formatMoney(gearTotal, payCurrency),
    )
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    BookingSummaryTotalRow(
        label = stringResource(R.string.booking_summary_line_total),
        value = formatMoney(totalNumeric, payCurrency),
    )
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.booking_notes_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.booking_notes_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.notes,
        onValueChange = vm::setNotes,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp),
        minLines = 4,
        maxLines = 10,
        placeholder = { Text(stringResource(R.string.booking_notes_placeholder)) },
    )
    Text(
        stringResource(R.string.booking_summary_placeholder),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BookingSummaryRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookingSummaryTotalRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun formatMoney(amount: Double, currencyCode: String): String =
    runCatching {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            currency = Currency.getInstance(currencyCode.uppercase(Locale.ROOT))
        }.format(amount)
    }.getOrElse { "${currencyCode.uppercase(Locale.ROOT)} ${"%.2f".format(amount)}" }

@Composable
private fun SelectableCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .selectable(selected, role = Role.Button, onClick = onClick),
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
