package com.divehub.app.ui.logbook

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.remote.dto.ExploreDiveSite
import com.divehub.app.data.remote.dto.ExploreItemKind
import com.divehub.app.ui.components.FishSpeciesPickerSheet
import com.divehub.app.util.DiveLogLocationHelper
import com.divehub.app.ui.theme.IosDesign
import com.divehub.app.ui.util.fileProviderImageUri
import java.io.File
import androidx.compose.material.icons.filled.PhotoCamera
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun applyNearestDiveSiteFromGps(
    context: Context,
    exploreRepo: ExploreRepository,
    scope: CoroutineScope,
    setResolving: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    onFound: (ExploreDiveSite) -> Unit,
) {
    scope.launch {
        setResolving(true)
        setError(null)
        val pair = withContext(Dispatchers.IO) { DiveLogLocationHelper.getLastKnownLatLngOrNull(context) }
        if (pair == null) {
            setResolving(false)
            setError(context.getString(R.string.logbook_location_error_no_fix))
            return@launch
        }
        val (lat, lon) = pair
        val nearest = withContext(Dispatchers.IO) {
            exploreRepo.findNearestDiveSiteToCoordinates(lat, lon)
        }
        setResolving(false)
        if (nearest != null) onFound(nearest)
        else setError(context.getString(R.string.logbook_gps_no_nearby_dive_site))
    }
}

private suspend fun loadDiveSitesForLogbookPicker(
    repo: ExploreRepository,
    lang: String,
    search: String,
): List<ExploreDiveSite> = withContext(Dispatchers.IO) {
    if (search.isNotBlank()) {
        val fromSearch = runCatching {
            repo.getDiveSitesExplore(
                language = lang,
                page = 1,
                limit = 100,
                q = search,
                sort = "relevance",
            )
        }.getOrElse { emptyList() }
        if (fromSearch.isNotEmpty()) {
            return@withContext fromSearch.filter { it.kind == ExploreItemKind.DIVE_SITE }
        }
    }
    val merged = (1..3).flatMap { page ->
        repo.getDiveSitesExplore(
            language = lang,
            page = page,
            limit = 100,
        )
    }
    merged.distinctBy { it.id }.filter { it.kind == ExploreItemKind.DIVE_SITE }
}

private suspend fun loadDiveCentersForLogbookPicker(
    repo: ExploreRepository,
): List<ExploreDiveSite> = withContext(Dispatchers.IO) {
    repo.getDiveCenters(limit = 100).filter { it.kind == ExploreItemKind.DIVE_CENTER }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDiveLogScreen(
    graph: AppGraph,
    vm: LogbookViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val exploreRepo = remember(graph) { ExploreRepository(graph) }
    val coroutineScope = rememberCoroutineScope()
    val appLanguage = remember(context) {
        if (Build.VERSION.SDK_INT >= 24) {
            context.resources.configuration.locales[0]?.language?.ifBlank { "en" } ?: "en"
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    var addSection by remember { mutableStateOf("form") }
    var siteSearch by remember { mutableStateOf("") }
    var debouncedSearch by remember { mutableStateOf("") }
    var centerSearch by remember { mutableStateOf("") }
    var pickerSites by remember { mutableStateOf<List<ExploreDiveSite>>(emptyList()) }
    var pickerLoading by remember { mutableStateOf(false) }
    var pickerError by remember { mutableStateOf<String?>(null) }
    var allCenters by remember { mutableStateOf<List<ExploreDiveSite>>(emptyList()) }
    var centersLoading by remember { mutableStateOf(false) }
    var centersLoadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(siteSearch) {
        delay(350)
        debouncedSearch = siteSearch
    }
    LaunchedEffect(debouncedSearch, addSection) {
        if (addSection != "sites") return@LaunchedEffect
        pickerLoading = true
        pickerError = null
        val result = runCatching { loadDiveSitesForLogbookPicker(exploreRepo, appLanguage, debouncedSearch) }
        pickerLoading = false
        result
            .onSuccess { pickerSites = it }
            .onFailure { e ->
                pickerError = e.message
                pickerSites = emptyList()
            }
    }
    LaunchedEffect(addSection) {
        if (addSection != "centers" || allCenters.isNotEmpty()) return@LaunchedEffect
        centersLoading = true
        centersLoadError = null
        runCatching { loadDiveCentersForLogbookPicker(exploreRepo) }
            .onSuccess { allCenters = it }
            .onFailure { e ->
                centersLoadError = e.message
                allCenters = emptyList()
            }
        centersLoading = false
    }

    var date by remember { mutableStateOf(LocalDate.now()) }
    var timeText by remember { mutableStateOf("") }
    var locationFree by remember { mutableStateOf("") }
    var selectedDiveSite by remember { mutableStateOf<ExploreDiveSite?>(null) }
    var selectedDiveCenter by remember { mutableStateOf<ExploreDiveSite?>(null) }
    var duration by remember { mutableStateOf("45") }
    var maxDepth by remember { mutableStateOf("18") }
    var avgDepth by remember { mutableStateOf("12") }
    var temp by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedSpecies by remember { mutableStateOf<List<String>>(emptyList()) }
    var publishToFeed by remember { mutableStateOf(false) }
    var showSpeciesPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var resolvingGps by remember { mutableStateOf(false) }
    var gpsError by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()) }
    val dateDisplay = remember(date) { date.format(dateFormatter) }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val currentOptions = listOf(
        "" to R.string.logbook_current_none,
        "Mild" to R.string.logbook_current_mild,
        "Moderate" to R.string.logbook_current_moderate,
        "Strong" to R.string.logbook_current_strong,
        "Very strong" to R.string.logbook_current_very_strong,
    )
    var currentMenuExpanded by remember { mutableStateOf(false) }

    val requestLocationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { ok ->
        if (ok) {
            applyNearestDiveSiteFromGps(
                context = context,
                exploreRepo = exploreRepo,
                scope = coroutineScope,
                setResolving = { resolvingGps = it },
                setError = { gpsError = it },
                onFound = { site ->
                    selectedDiveSite = site
                },
            )
        } else {
            gpsError = context.getString(R.string.logbook_location_error_denied)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris -> photos = uris }
    var logCameraTarget: Uri? by remember { mutableStateOf(null) }
    val takeDivePhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (ok) {
            val u = logCameraTarget
            if (u != null && photos.size < 10) {
                photos = photos + u
            }
        }
    }

    val diveSiteIdForCreate = selectedDiveSite?.id
    val canSave = (maxDepth.toDoubleOrNull() ?: 0.0) > 0.0 && (duration.toIntOrNull() ?: 0) > 0

    val centerRows = remember(allCenters, centerSearch) {
        val q = centerSearch.trim()
        if (q.isEmpty()) allCenters
        else allCenters.filter { it.name.contains(q, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (addSection) {
                "sites" -> {
                    Column(Modifier.fillMaxSize()) {
                        CenterAlignedTopAppBar(
                            title = { Text(stringResource(R.string.logbook_dive_site_picker_title)) },
                            navigationIcon = {
                                IconButton(onClick = { addSection = "form"; siteSearch = "" }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                        OutlinedTextField(
                            value = siteSearch,
                            onValueChange = { siteSearch = it },
                            label = { Text(stringResource(R.string.logbook_dive_site_picker_search)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                        )
                        if (pickerLoading) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            pickerError?.let { err ->
                                Text(
                                    err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                            ) {
                                items(pickerSites, key = { it.id }) { site ->
                                    val subtitle = listOfNotNull(
                                        site.country.takeIf { it.isNotBlank() },
                                        site.region.takeIf { it.isNotBlank() },
                                    ).joinToString(", ")
                                    ListItem(
                                        headlineContent = { Text(site.name) },
                                        supportingContent = {
                                            if (subtitle.isNotEmpty()) {
                                                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedDiveSite = site
                                                addSection = "form"
                                                siteSearch = ""
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
                "centers" -> {
                    Column(Modifier.fillMaxSize()) {
                        CenterAlignedTopAppBar(
                            title = { Text(stringResource(R.string.logbook_dive_center_picker_title)) },
                            navigationIcon = {
                                IconButton(onClick = { addSection = "form" }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                        OutlinedTextField(
                            value = centerSearch,
                            onValueChange = { centerSearch = it },
                            label = { Text(stringResource(R.string.logbook_dive_center_picker_search)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                        )
                        if (centersLoading) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            centersLoadError?.let { err ->
                                Text(
                                    err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                            ) {
                                items(centerRows, key = { it.id }) { c ->
                                    val sub = c.region.ifBlank { c.country }
                                    ListItem(
                                        headlineContent = { Text(c.name) },
                                        supportingContent = {
                                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedDiveCenter = c
                                                addSection = "form"
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        stringResource(R.string.logbook_add_title_short),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                navigationIcon = {
                                    TextButton(onClick = onDismiss) {
                                        Text(stringResource(R.string.common_cancel))
                                    }
                                },
                                actions = {
                                    TextButton(
                                        onClick = {
                                            if (canSave) {
                                            vm.addDive(
                                                context = context,
                                                date = date,
                                                durationMin = duration.toIntOrNull() ?: 45,
                                                maxDepth = maxDepth.toDoubleOrNull() ?: 18.0,
                                                avgDepth = avgDepth.toDoubleOrNull(),
                                                temp = temp.toDoubleOrNull(),
                                                visibility = visibility.toDoubleOrNull(),
                                                current = current.takeIf { it.isNotEmpty() },
                                                diveType = null,
                                                notes = notes.trim().takeIf { it.isNotEmpty() },
                                                photoUris = photos,
                                                startTime = timeText.trim().takeIf { it.isNotEmpty() },
                                                locationName = locationFree.trim().takeIf { it.isNotEmpty() }
                                                    ?: selectedDiveSite?.name,
                                                diveSiteId = selectedDiveSite?.id,
                                                diveCenterId = selectedDiveCenter?.id,
                                                fishSpecies = selectedSpecies,
                                                publishToFeed = publishToFeed,
                                                onDone = onDismiss,
                                            )
                                            }
                                        },
                                        enabled = canSave,
                                    ) {
                                        Text(stringResource(R.string.common_save))
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                ),
                            )
                        },
                    ) { innerPad ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPad)
                                .imePadding()
                                .navigationBarsPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = IosDesign.ScreenPadding),
                        ) {
                            // Basic
                            LogbookFormSectionTitle(stringResource(R.string.logbook_section_basic))
                            Spacer(Modifier.height(8.dp))
                            LogbookFormCard {
                                Text(stringResource(R.string.logbook_date_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(dateDisplay, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { showDatePicker = true },
                                    ) { Icon(Icons.Filled.CalendarToday, contentDescription = stringResource(R.string.logbook_pick_dive_date)) }
                                }
                                TextButton(onClick = { date = LocalDate.now() }) {
                                    Text(stringResource(R.string.logbook_today))
                                }
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = timeText,
                                    onValueChange = { timeText = it },
                                    label = { Text(stringResource(R.string.logbook_time_label)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                TextButton(
                                    onClick = {
                                        timeText = timeFormatter.format(LocalTime.now())
                                    },
                                ) { Text(stringResource(R.string.logbook_use_current_time)) }
                                Spacer(Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = locationFree,
                                    onValueChange = { locationFree = it },
                                    label = { Text(stringResource(R.string.logbook_location_free_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = {
                                            if (!DiveLogLocationHelper.hasLocationPermission(context)) {
                                                requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                            } else {
                                                applyNearestDiveSiteFromGps(
                                                    context, exploreRepo, coroutineScope,
                                                    { resolvingGps = it },
                                                    { gpsError = it },
                                                ) { selectedDiveSite = it }
                                            }
                                        },
                                        enabled = !resolvingGps,
                                    ) { Text(stringResource(R.string.logbook_fill_dive_site_from_gps)) }
                                    if (resolvingGps) {
                                        Spacer(Modifier.width(8.dp))
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                }
                                gpsError?.let {
                                    Text(
                                        it,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LogbookSelectRow(
                                    label = stringResource(R.string.logbook_dive_center_label),
                                    value = selectedDiveCenter?.name,
                                    onClick = { addSection = "centers" },
                                )
                                Spacer(Modifier.height(4.dp))
                                LogbookSelectRow(
                                    label = stringResource(R.string.logbook_dive_site_row_label),
                                    value = selectedDiveSite?.name,
                                    onClick = { addSection = "sites" },
                                )
                                if (selectedDiveSite != null) {
                                    TextButton(onClick = { selectedDiveSite = null }) {
                                        Text(stringResource(R.string.logbook_dive_site_clear))
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            // Dive details
                            LogbookFormSectionTitle(stringResource(R.string.logbook_section_dive_details))
                            Spacer(Modifier.height(8.dp))
                            LogbookFormCard {
                                LabeledField(
                                    stringResource(R.string.logbook_max_depth_label),
                                    maxDepth,
                                ) { maxDepth = it }
                                LabeledField(
                                    stringResource(R.string.logbook_avg_depth_label),
                                    avgDepth,
                                ) { avgDepth = it }
                                LabeledField(
                                    stringResource(R.string.logbook_duration_label),
                                    duration,
                                ) { s -> duration = s.filter(Char::isDigit) }
                            }
                            Spacer(Modifier.height(16.dp))
                            // Conditions
                            LogbookFormSectionTitle(stringResource(R.string.logbook_section_conditions))
                            Spacer(Modifier.height(8.dp))
                            LogbookFormCard {
                                val currentValueLabel = currentOptions.find { it.first == current }?.second
                                    ?.let { stringResource(it) }
                                    ?: stringResource(R.string.logbook_current_none)
                                LabeledField(
                                    stringResource(R.string.logbook_water_temp_label),
                                    temp,
                                ) { temp = it }
                                LabeledField(
                                    stringResource(R.string.logbook_visibility_label),
                                    visibility,
                                ) { visibility = it }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.logbook_current_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ExposedDropdownMenuBox(
                                    expanded = currentMenuExpanded,
                                    onExpandedChange = { currentMenuExpanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = currentValueLabel,
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(currentMenuExpanded) },
                                    )
                                    ExposedDropdownMenu(
                                        expanded = currentMenuExpanded,
                                        onDismissRequest = { currentMenuExpanded = false },
                                    ) {
                                        currentOptions.forEach { (v, resId) ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(resId)) },
                                                onClick = {
                                                    current = v
                                                    currentMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            // Photos
                            LogbookFormSectionTitle(stringResource(R.string.logbook_section_photos))
                            Spacer(Modifier.height(8.dp))
                            LogbookFormCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.logbook_add_photos_action))
                                        if (photos.isNotEmpty()) {
                                            Spacer(Modifier.width(8.dp))
                                            Text("(${photos.size})")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (photos.size >= 10) return@OutlinedButton
                                            val f = File(context.cacheDir, "log_dive_${System.currentTimeMillis()}.jpg")
                                            f.parentFile?.mkdirs()
                                            val u = fileProviderImageUri(context, f)
                                            logCameraTarget = u
                                            runCatching { takeDivePhoto.launch(u) }
                                        },
                                        enabled = photos.size < 10,
                                    ) {
                                        Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.photo_choose_camera))
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            // Marine
                            LogbookFormSectionTitle(stringResource(R.string.logbook_section_marine))
                            Spacer(Modifier.height(8.dp))
                            LogbookFormCard {
                                LogbookSelectRow(
                                    label = stringResource(R.string.logbook_select_fish_species),
                                    value = if (selectedSpecies.isEmpty()) {
                                        null
                                    } else {
                                        stringResource(R.string.logbook_selected_fish_species_count, selectedSpecies.size)
                                    },
                                    onClick = { showSpeciesPicker = true },
                                )
                                if (selectedSpecies.isNotEmpty()) {
                                    Text(
                                        selectedSpecies.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            // Notes
                            LogbookFormSectionTitle(stringResource(R.string.logbook_section_notes))
                            Spacer(Modifier.height(8.dp))
                            LogbookFormCard {
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text(stringResource(R.string.logbook_notes_label)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 100.dp, max = 200.dp),
                                    minLines = 3,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            // Publish
                            LogbookFormSectionTitle(stringResource(R.string.logbook_section_publish))
                            Spacer(Modifier.height(8.dp))
                            LogbookFormCard {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { publishToFeed = !publishToFeed },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        stringResource(R.string.logbook_publish_to_feed),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Switch(
                                        checked = publishToFeed,
                                        onCheckedChange = { publishToFeed = it },
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            date = Instant.ofEpochMilli(millis)
                                .atZone(zone)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) { DatePicker(state = datePickerState) }
    }

    if (showSpeciesPicker) {
        FishSpeciesPickerSheet(
            selected = selectedSpecies,
            onSelectedChange = { selectedSpecies = it },
            onDismiss = { showSpeciesPicker = false },
        )
    }
}

@Composable
private fun LogbookFormSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LogbookFormCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun LogbookSelectRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            value ?: stringResource(R.string.logbook_select_action),
            color = if (value == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
}
