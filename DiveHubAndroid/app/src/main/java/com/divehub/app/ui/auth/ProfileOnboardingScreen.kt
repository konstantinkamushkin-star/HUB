package com.divehub.app.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AuthRepository
import com.divehub.app.ui.Routes
import com.divehub.app.ui.profile.DiveProfileCatalog
import com.divehub.app.ui.profile.DiveProfileCatalogLabels
import kotlinx.coroutines.launch
import java.util.Locale

private fun suggestUsername(email: String, userId: String): String {
    val local = email.substringBefore('@')
        .replace(Regex("[^a-zA-Z0-9._-]"), "")
        .take(30)
    if (local.length >= 3) return local
    return "diver_${userId.take(8)}"
}

private fun isoCountries(displayLocale: Locale): List<Pair<String, String>> =
    Locale.getISOCountries()
        .map { code -> code to Locale("", code).getDisplayCountry(displayLocale).ifBlank { code } }
        .sortedBy { it.second.lowercase(displayLocale) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileOnboardingRoute(nav: NavHostController, graph: AppGraph) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository(graph) }
    val displayLocale = remember { Locale.getDefault() }

    var step by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showCountrySheet by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }

    var certLevel by remember { mutableStateOf("") }
    var selectedAgencies by remember { mutableStateOf(setOf<String>()) }
    var divesRange by remember { mutableStateOf("") }
    var selectedInterests by remember { mutableStateOf(setOf<String>()) }
    var selectedEquipment by remember { mutableStateOf(setOf<String>()) }
    var userEmail by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val privacy = remember {
        mutableStateMapOf(
            "showProfilePhoto" to true,
            "showCertificationLevel" to true,
            "showNumberOfDives" to true,
            "showLocation" to true,
            "showLastDive" to false,
            "showEquipment" to false,
            "showBuddySearchStatus" to true,
            "showLogbook" to false,
            "showContactOptions" to false,
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> photoUri = uri }

    LaunchedEffect(Unit) {
        val u = repo.cachedUser() ?: return@LaunchedEffect
        userEmail = u.email
        userId = u.id
        val dp = u.diverProfile
        displayName = (dp?.get("displayName") as? String)?.trim().orEmpty()
            .ifBlank { u.displayName() }
        username = u.username?.trim().orEmpty()
            .ifBlank { (dp?.get("username") as? String)?.trim().orEmpty() }
            .ifBlank { suggestUsername(u.email, u.id) }
        countryCode = u.countryCode?.trim().orEmpty()
        city = (dp?.get("city") as? String)?.trim().orEmpty()
        certLevel = (dp?.get("certificationLevel") as? String)?.trim().orEmpty()
        @Suppress("UNCHECKED_CAST")
        val agencies = (dp?.get("certifyingAgencies") as? List<String>)?.toSet()
        selectedAgencies = when {
            !agencies.isNullOrEmpty() -> agencies
            else -> {
                val legacy = (dp?.get("certifyingAgency") as? String)?.trim().orEmpty()
                if (legacy.isEmpty()) emptySet()
                else legacy.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }
        }
        divesRange = (dp?.get("totalDivesRange") as? String)?.trim().orEmpty()
        @Suppress("UNCHECKED_CAST")
        selectedInterests = (dp?.get("diveInterests") as? List<String>)?.toSet() ?: emptySet()
        @Suppress("UNCHECKED_CAST")
        selectedEquipment = (dp?.get("ownEquipment") as? List<String>)?.toSet() ?: emptySet()
        @Suppress("UNCHECKED_CAST")
        val p = dp?.get("privacy") as? Map<String, Any?>
        p?.forEach { (k, v) ->
            when (v) {
                is Boolean -> privacy[k] = v
                is Number -> privacy[k] = v.toInt() != 0
            }
        }
    }

    fun splitName(raw: String): Pair<String, String> {
        val p = raw.trim().split(Regex("\\s+"), limit = 2).map { it.trim() }.filter { it.isNotEmpty() }
        val first = p.firstOrNull() ?: "Diver"
        val last = p.getOrNull(1) ?: first
        return first to last
    }

    fun resolvedUsername(): String {
        val manual = username.trim().replace("@", "")
        if (manual.length >= 3) return manual.take(30)
        if (userId.isNotBlank()) return suggestUsername(userEmail, userId)
        return "diver_user"
    }

    fun toggleAgency(code: String, on: Boolean) {
        selectedAgencies = when {
            code == "NONE_YET" -> if (on) setOf("NONE_YET") else selectedAgencies - "NONE_YET"
            on -> (selectedAgencies - "NONE_YET") + code
            else -> selectedAgencies - code
        }
    }

    fun countryLabel(code: String): String {
        if (code.isBlank()) return "—"
        val name = Locale("", code).getDisplayCountry(displayLocale).ifBlank { code }
        return "$name ($code)"
    }

    val countries = remember(displayLocale) { isoCountries(displayLocale) }
    val filteredCountries = remember(countryQuery, countries) {
        val q = countryQuery.trim()
        if (q.isEmpty()) countries
        else countries.filter { (code, name) ->
            name.contains(q, ignoreCase = true) || code.contains(q, ignoreCase = true)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            Text(
                stringResource(R.string.onboarding_profile_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    0 to R.string.onboarding_step_basics,
                    1 to R.string.onboarding_step_diving,
                    2 to R.string.onboarding_step_privacy,
                ).forEach { (idx, labelRes) ->
                    FilterChip(
                        selected = step == idx,
                        onClick = { step = idx },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text(stringResource(R.string.onboarding_display_name)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.onboarding_username)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showCountrySheet = true }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.onboarding_country))
                            Text(
                                countryLabel(countryCode),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text(stringResource(R.string.onboarding_city_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.onboarding_photo))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (photoUri != null) {
                                    AsyncImage(
                                        model = photoUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                TextButton(
                                    onClick = {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                ) { Text(stringResource(R.string.onboarding_pick_photo)) }
                            }
                        }
                        Button(
                            onClick = {
                                if (displayName.trim().length < 2) {
                                    scope.launch {
                                        snackbar.showSnackbar(context.getString(R.string.onboarding_err_display_name_short))
                                    }
                                    return@Button
                                }
                                if (countryCode.isBlank()) {
                                    scope.launch {
                                        snackbar.showSnackbar(context.getString(R.string.onboarding_err_country_required))
                                    }
                                    return@Button
                                }
                                step = 1
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.onboarding_continue)) }
                    }
                    1 -> {
                        Text(stringResource(R.string.onboarding_certification_level), fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiveProfileCatalog.certificationLevelsForPicker(certLevel).forEach { c ->
                                FilterChip(
                                    selected = certLevel == c,
                                    onClick = { certLevel = c },
                                    label = { Text(DiveProfileCatalogLabels.certificationLevelLabel(c)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.onboarding_dive_count_range), fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiveProfileCatalog.diveCountRanges.forEach { r ->
                                FilterChip(
                                    selected = divesRange == r,
                                    onClick = { divesRange = r },
                                    label = { Text(DiveProfileCatalogLabels.englishCatalogValue(r)) },
                                )
                            }
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.onboarding_agency), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.onboarding_agency_exclusive_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DiveProfileCatalog.certifyingAgencies.forEach { a ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(DiveProfileCatalogLabels.englishCatalogValue(a))
                                Switch(
                                    checked = selectedAgencies.contains(a),
                                    onCheckedChange = { toggleAgency(a, it) },
                                )
                            }
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.onboarding_interests), fontWeight = FontWeight.SemiBold)
                        DiveProfileCatalog.diveInterests.forEach { key ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(DiveProfileCatalogLabels.englishCatalogValue(key))
                                Switch(
                                    checked = selectedInterests.contains(key),
                                    onCheckedChange = { on ->
                                        selectedInterests = if (on) selectedInterests + key else selectedInterests - key
                                    },
                                )
                            }
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.onboarding_equipment), fontWeight = FontWeight.SemiBold)
                        DiveProfileCatalog.equipmentKeys.forEach { key ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(DiveProfileCatalogLabels.englishCatalogValue(key))
                                Switch(
                                    checked = selectedEquipment.contains(key),
                                    onCheckedChange = { on ->
                                        selectedEquipment = if (on) selectedEquipment + key else selectedEquipment - key
                                    },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextFieldLikeButton(
                                label = stringResource(R.string.common_back),
                                onClick = { step = 0 },
                            )
                            Button(
                                onClick = {
                                    when {
                                        certLevel.isBlank() -> scope.launch {
                                            snackbar.showSnackbar(context.getString(R.string.onboarding_err_cert_required))
                                        }
                                        selectedAgencies.isEmpty() -> scope.launch {
                                            snackbar.showSnackbar(context.getString(R.string.onboarding_err_agency_required))
                                        }
                                        divesRange.isBlank() -> scope.launch {
                                            snackbar.showSnackbar(context.getString(R.string.onboarding_err_dives_range_required))
                                        }
                                        else -> step = 2
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.onboarding_continue)) }
                        }
                        TextButton(
                            onClick = {
                                selectedInterests = emptySet()
                                selectedEquipment = emptySet()
                                step = 2
                            },
                        ) { Text(stringResource(R.string.onboarding_skip_optional_fields)) }
                    }
                    else -> {
                        Text(
                            stringResource(R.string.onboarding_privacy_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PrivacyToggle(stringResource(R.string.onboarding_privacy_profile_photo), "showProfilePhoto", privacy)
                        PrivacyToggle(stringResource(R.string.onboarding_privacy_certification), "showCertificationLevel", privacy)
                        PrivacyToggle(stringResource(R.string.onboarding_privacy_dive_count), "showNumberOfDives", privacy)
                        PrivacyToggle(stringResource(R.string.onboarding_privacy_location), "showLocation", privacy)
                        PrivacyToggle(stringResource(R.string.onboarding_privacy_last_dive), "showLastDive", privacy)
                        PrivacyToggle(stringResource(R.string.onboarding_privacy_equipment), "showEquipment", privacy)
                        PrivacyToggle(stringResource(R.string.onboarding_privacy_buddy_status), "showBuddySearchStatus", privacy)
                        PrivacyToggle(stringResource(R.string.onboarding_privacy_logbook), "showLogbook", privacy)
                        PrivacyToggle(stringResource(R.string.onboarding_privacy_contact_options), "showContactOptions", privacy)
                        Button(
                            onClick = {
                                if (saving) return@Button
                                saving = true
                                scope.launch {
                                    runCatching {
                                        var avatarUrl: String? = null
                                        photoUri?.let { uri ->
                                            avatarUrl = repo.uploadAvatar(context, uri)
                                        }
                                        val dn = displayName.trim().ifBlank { "Diver" }
                                        val (fn, ln) = splitName(dn)
                                        val noneOnly = selectedAgencies == setOf("NONE_YET")
                                        val realAgencies = selectedAgencies.filter { it != "NONE_YET" }.sorted()
                                        val legacyAgency = when {
                                            noneOnly -> "NONE_YET"
                                            realAgencies.isEmpty() -> null
                                            else -> realAgencies.joinToString(",")
                                        }
                                        val dp = linkedMapOf<String, Any?>(
                                            "displayName" to dn,
                                            "username" to resolvedUsername(),
                                            "city" to city.trim().takeIf { it.isNotEmpty() },
                                            "certificationLevel" to certLevel,
                                            "certifyingAgency" to legacyAgency,
                                            "certifyingAgencies" to selectedAgencies.sorted().takeIf { it.isNotEmpty() },
                                            "noCertYet" to noneOnly,
                                            "totalDivesRange" to divesRange,
                                            "diveInterests" to selectedInterests.sorted().takeIf { it.isNotEmpty() },
                                            "ownEquipment" to selectedEquipment.sorted().takeIf { it.isNotEmpty() },
                                            "onboardingCompleted" to true,
                                            "privacy" to privacy.toMap(),
                                        )
                                        repo.updateProfile(
                                            firstName = fn,
                                            lastName = ln,
                                            avatarUrl = avatarUrl,
                                            countryCode = countryCode.trim(),
                                            diverProfile = dp,
                                        )
                                    }.onSuccess {
                                        nav.navigate(Routes.Main) {
                                            popUpTo(Routes.ProfileOnboarding) { inclusive = true }
                                        }
                                    }.onFailure { e ->
                                        snackbar.showSnackbar(repo.parseErrorMessage(e))
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (saving) {
                                CircularProgressIndicator(Modifier.size(22.dp))
                            } else {
                                Text(stringResource(R.string.onboarding_save_continue))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCountrySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCountrySheet = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.onboarding_country), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = countryQuery,
                    onValueChange = { countryQuery = it },
                    label = { Text(stringResource(R.string.onboarding_country_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                LazyColumn(Modifier.height(360.dp)) {
                    items(filteredCountries, key = { it.first }) { (code, name) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    countryCode = code
                                    showCountrySheet = false
                                    countryQuery = ""
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(name)
                            Text(code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyToggle(
    label: String,
    key: String,
    privacy: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(
            checked = privacy[key] == true,
            onCheckedChange = { privacy[key] = it },
        )
    }
}

@Composable
private fun OutlinedTextFieldLikeButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label) }
}
