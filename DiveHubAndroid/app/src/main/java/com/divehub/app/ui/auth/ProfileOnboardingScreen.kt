package com.divehub.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AuthRepository
import com.divehub.app.ui.Routes
import kotlinx.coroutines.launch

private val certLevels = listOf(
    "TRY_SCUBA", "OPEN_WATER", "ADVANCED_OPEN_WATER", "RESCUE",
    "DIVEMASTER", "INSTRUCTOR", "TECHNICAL", "FREEDIVER", "OTHER",
)
private val agencies = listOf("PADI", "SSI", "CMAS", "NAUI", "RAID", "GUE", "OTHER", "NONE_YET")
private val ranges = listOf("0", "1_10", "11_25", "26_50", "51_100", "100_PLUS")

@Composable
fun ProfileOnboardingRoute(nav: NavHostController, graph: AppGraph) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository(graph) }

    var step by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var certLevel by remember { mutableStateOf("") }
    var certAgency by remember { mutableStateOf("") }
    var divesRange by remember { mutableStateOf("") }
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

    LaunchedEffect(Unit) {
        val u = repo.cachedUser() ?: return@LaunchedEffect
        val dp = u.diverProfile
        displayName = (dp?.get("displayName") as? String)
            ?: listOfNotNull(u.firstName, u.lastName).joinToString(" ").trim().ifBlank {
                u.email.substringBefore('@')
            }
        country = u.countryCode ?: ""
        certLevel = (dp?.get("certificationLevel") as? String) ?: ""
        certAgency = (dp?.get("certifyingAgency") as? String) ?: ""
        divesRange = (dp?.get("totalDivesRange") as? String) ?: ""
    }

    fun splitName(raw: String): Pair<String, String> {
        val p = raw.trim().split(Regex("\\s+"), limit = 2).map { it.trim() }.filter { it.isNotEmpty() }
        val first = p.firstOrNull() ?: "Diver"
        val last = p.getOrNull(1) ?: first
        return first to last
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.onboarding_profile_title), style = MaterialTheme.typography.titleLarge)
            Text("Step ${step + 1}/3", style = MaterialTheme.typography.labelMedium)

            when (step) {
                0 -> {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(stringResource(R.string.onboarding_display_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it.uppercase() },
                        label = { Text(stringResource(R.string.onboarding_country)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        if (displayName.trim().length < 2) {
                            scope.launch { snackbar.showSnackbar("Display name too short") }
                            return@Button
                        }
                        if (country.trim().length < 2) {
                            scope.launch { snackbar.showSnackbar("Country required") }
                            return@Button
                        }
                        step = 1
                    }) {
                        Text(stringResource(R.string.onboarding_step_diving))
                    }
                }
                1 -> {
                    Text("Certification level", style = MaterialTheme.typography.labelLarge)
                    certLevels.forEach { c ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.replace('_', ' '))
                            Switch(checked = certLevel == c, onCheckedChange = { if (it) certLevel = c })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Agency", style = MaterialTheme.typography.labelLarge)
                    agencies.forEach { a ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(a)
                            Switch(checked = certAgency == a, onCheckedChange = { if (it) certAgency = a })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Dive count range", style = MaterialTheme.typography.labelLarge)
                    ranges.forEach { r ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(r.replace('_', '–'))
                            Switch(checked = divesRange == r, onCheckedChange = { if (it) divesRange = r })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { step = 0 }) { Text("Back") }
                        Button(onClick = {
                            if (certLevel.isBlank() || certAgency.isBlank() || divesRange.isBlank()) {
                                scope.launch { snackbar.showSnackbar("Fill all required fields") }
                                return@Button
                            }
                            step = 2
                        }) { Text(stringResource(R.string.onboarding_step_privacy)) }
                    }
                }
                else -> {
                    privacy.keys.forEach { k ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k)
                            Switch(
                                checked = privacy[k] == true,
                                onCheckedChange = { privacy[k] = it },
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (saving) return@Button
                            saving = true
                            scope.launch {
                                runCatching {
                                    val (fn, ln) = splitName(displayName)
                                    val dp = mutableMapOf<String, Any?>(
                                        "displayName" to displayName.trim(),
                                        "certificationLevel" to certLevel,
                                        "certifyingAgency" to certAgency,
                                        "noCertYet" to (certAgency == "NONE_YET"),
                                        "totalDivesRange" to divesRange,
                                        "onboardingCompleted" to true,
                                        "privacy" to privacy.toMap(),
                                    )
                                    repo.updateProfile(
                                        firstName = fn,
                                        lastName = ln,
                                        countryCode = country.trim(),
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
