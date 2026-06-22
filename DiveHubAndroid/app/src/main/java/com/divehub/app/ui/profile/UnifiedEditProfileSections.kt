package com.divehub.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.divehub.app.R
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedDiverProfileSection(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    certLevel: String,
    onCertLevelChange: (String) -> Unit,
    divesRange: String,
    onDivesRangeChange: (String) -> Unit,
    selectedAgencies: Set<String>,
    onToggleAgency: (String) -> Unit,
    selectedInterests: Set<String>,
    onToggleInterest: (String) -> Unit,
    selectedEquipment: Set<String>,
    onToggleEquipment: (String) -> Unit,
    privacy: Map<String, Boolean>,
    onPrivacyChange: (String, Boolean) -> Unit,
) {
    val countries = remember { isoCountriesList() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.onboarding_step_basics), fontWeight = FontWeight.SemiBold)
        androidx.compose.material3.OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(stringResource(R.string.onboarding_display_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        androidx.compose.material3.OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.onboarding_username)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        CountryPickerField(
            countries = countries,
            countryCode = countryCode,
            onCountryCodeChange = onCountryCodeChange,
        )
        androidx.compose.material3.OutlinedTextField(
            value = city,
            onValueChange = onCityChange,
            label = { Text(stringResource(R.string.onboarding_city_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        HorizontalDivider()
        Text(stringResource(R.string.onboarding_step_diving), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.onboarding_certification_level), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiveProfileCatalog.certificationLevelsForPicker(certLevel).forEach { c ->
                FilterChip(
                    selected = certLevel == c,
                    onClick = { onCertLevelChange(c) },
                    label = { Text(DiveProfileCatalogLabels.certificationLevelLabel(c)) },
                )
            }
        }
        Text(stringResource(R.string.onboarding_dive_count_range), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiveProfileCatalog.diveCountRanges.forEach { r ->
                FilterChip(
                    selected = divesRange == r,
                    onClick = { onDivesRangeChange(r) },
                    label = { Text(DiveProfileCatalogLabels.englishCatalogValue(r)) },
                )
            }
        }

        Text(stringResource(R.string.onboarding_agency), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiveProfileCatalog.certifyingAgencies.forEach { a ->
                FilterChip(
                    selected = selectedAgencies.contains(a),
                    onClick = { onToggleAgency(a) },
                    label = { Text(DiveProfileCatalogLabels.englishCatalogValue(a)) },
                )
            }
        }

        Text(stringResource(R.string.onboarding_interests), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiveProfileCatalog.diveInterests.forEach { key ->
                FilterChip(
                    selected = selectedInterests.contains(key),
                    onClick = { onToggleInterest(key) },
                    label = { Text(DiveProfileCatalogLabels.englishCatalogValue(key)) },
                )
            }
        }

        Text(stringResource(R.string.onboarding_equipment), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiveProfileCatalog.equipmentKeys.forEach { key ->
                FilterChip(
                    selected = selectedEquipment.contains(key),
                    onClick = { onToggleEquipment(key) },
                    label = { Text(DiveProfileCatalogLabels.englishCatalogValue(key)) },
                )
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.onboarding_step_privacy), fontWeight = FontWeight.SemiBold)
        PrivacyToggleRow(stringResource(R.string.onboarding_privacy_profile_photo), "showProfilePhoto", privacy, onPrivacyChange)
        PrivacyToggleRow(stringResource(R.string.onboarding_privacy_certification), "showCertificationLevel", privacy, onPrivacyChange)
        PrivacyToggleRow(stringResource(R.string.onboarding_privacy_dive_count), "showNumberOfDives", privacy, onPrivacyChange)
        PrivacyToggleRow(stringResource(R.string.onboarding_privacy_location), "showLocation", privacy, onPrivacyChange)
        PrivacyToggleRow(stringResource(R.string.onboarding_privacy_last_dive), "showLastDive", privacy, onPrivacyChange)
        PrivacyToggleRow(stringResource(R.string.onboarding_privacy_equipment), "showEquipment", privacy, onPrivacyChange)
        PrivacyToggleRow(stringResource(R.string.onboarding_privacy_buddy_status), "showBuddySearchStatus", privacy, onPrivacyChange)
        PrivacyToggleRow(stringResource(R.string.onboarding_privacy_logbook), "showLogbook", privacy, onPrivacyChange)
        PrivacyToggleRow(stringResource(R.string.onboarding_privacy_contact_options), "showContactOptions", privacy, onPrivacyChange)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PrivacyToggleRow(
    label: String,
    key: String,
    privacy: Map<String, Boolean>,
    onPrivacyChange: (String, Boolean) -> Unit,
) {
    RowSwitch(label = label, checked = privacy[key] != false) { onPrivacyChange(key, it) }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun isoCountriesList(): List<Pair<String, String>> {
    val locale = Locale.getDefault()
    return Locale.getISOCountries()
        .map { code -> code to Locale("", code).getDisplayCountry(locale).ifBlank { code } }
        .sortedBy { it.second.lowercase(locale) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerField(
    countries: List<Pair<String, String>>,
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val label = countries.find { it.first == countryCode }?.second ?: countryCode
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = if (countryCode.isBlank()) "" else label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.onboarding_country)) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            countries.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onCountryCodeChange(code)
                        expanded = false
                    },
                )
            }
        }
    }
}
