package com.divehub.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.BuildConfig
import com.divehub.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerRegistrationRoute(
    nav: NavHostController,
    graph: AppGraph,
    fixedKind: PartnerRegKind? = null,
) {
    val vm: PartnerRegistrationViewModel = viewModel(factory = PartnerRegistrationViewModel.factory(graph))
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var kind by remember(fixedKind) { mutableStateOf(fixedKind ?: PartnerRegKind.DIVE_CENTER) }
    var shopType by remember { mutableStateOf(PartnerRegShopType.OFFLINE) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var latitudeText by remember { mutableStateOf("") }
    var longitudeText by remember { mutableStateOf("") }
    var consentAccepted by remember { mutableStateOf(false) }

    val uriHandler = LocalUriHandler.current
    val legalBase = BuildConfig.LEGAL_DOCS_BASE_URL.trimEnd('/')

    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            snackbar.showSnackbar(msg)
            vm.clearError()
        }
    }

    val successMsg = state.successMessage
    if (successMsg != null) {
        AlertDialog(
            onDismissRequest = {
                vm.clearSuccess()
                nav.popBackStack()
            },
            title = { Text(stringResource(R.string.partner_reg_success_title)) },
            text = { Text(successMsg) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearSuccess()
                        nav.popBackStack()
                    },
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (fixedKind == PartnerRegKind.DIVE_CENTER) {
                            stringResource(R.string.dive_center_registration_title)
                        } else {
                            stringResource(R.string.partner_reg_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (fixedKind == PartnerRegKind.DIVE_CENTER) {
                    stringResource(R.string.dive_center_registration_subtitle)
                } else {
                    stringResource(R.string.partner_reg_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (fixedKind == null) {
                Text(stringResource(R.string.partner_reg_kind_label), style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == PartnerRegKind.DIVE_CENTER,
                        onClick = { kind = PartnerRegKind.DIVE_CENTER },
                        label = { Text(stringResource(R.string.partner_reg_kind_dive_center)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilterChip(
                        selected = kind == PartnerRegKind.SHOP,
                        onClick = { kind = PartnerRegKind.SHOP },
                        label = { Text(stringResource(R.string.partner_reg_kind_shop)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (kind == PartnerRegKind.SHOP) {
                Text(stringResource(R.string.partner_reg_shop_type_label), style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = shopType == PartnerRegShopType.OFFLINE,
                        onClick = { shopType = PartnerRegShopType.OFFLINE },
                        label = { Text(stringResource(R.string.partner_reg_shop_offline)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilterChip(
                        selected = shopType == PartnerRegShopType.ONLINE,
                        onClick = { shopType = PartnerRegShopType.ONLINE },
                        label = { Text(stringResource(R.string.partner_reg_shop_online)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                when {
                    kind == PartnerRegKind.DIVE_CENTER -> stringResource(R.string.partner_reg_coords_hint_dive)
                    shopType == PartnerRegShopType.OFFLINE -> stringResource(R.string.partner_reg_coords_hint_shop_offline)
                    else -> stringResource(R.string.partner_reg_coords_hint_shop_online)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.partner_reg_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.partner_reg_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = contactEmail,
                onValueChange = { contactEmail = it },
                label = { Text(stringResource(R.string.partner_reg_contact_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { contactPhone = it },
                label = { Text(stringResource(R.string.partner_reg_contact_phone)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = country,
                onValueChange = { country = it },
                label = { Text(stringResource(R.string.partner_reg_country)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text(stringResource(R.string.partner_reg_city)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.partner_reg_address)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text(stringResource(R.string.partner_reg_website)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = latitudeText,
                onValueChange = { latitudeText = it },
                label = { Text(stringResource(R.string.partner_reg_latitude)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = longitudeText,
                onValueChange = { longitudeText = it },
                label = { Text(stringResource(R.string.partner_reg_longitude)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                Checkbox(
                    checked = consentAccepted,
                    onCheckedChange = { consentAccepted = it },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.personal_data_consent_checkbox))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { uriHandler.openUri("$legalBase/privacy") }) {
                            Text(stringResource(R.string.legal_open_privacy))
                        }
                        TextButton(onClick = { uriHandler.openUri("$legalBase/agreement") }) {
                            Text(stringResource(R.string.legal_open_agreement))
                        }
                    }
                    Text(
                        text = stringResource(R.string.personal_data_consent_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.loading) {
                CircularProgressIndicator(Modifier.padding(vertical = 16.dp))
            } else {
                Button(
                    onClick = {
                        vm.submit(
                            kind = fixedKind ?: kind,
                            shopType = shopType,
                            name = name,
                            description = description,
                            contactEmail = contactEmail,
                            contactPhone = contactPhone,
                            country = country,
                            city = city,
                            address = address,
                            website = website,
                            latitudeText = latitudeText,
                            longitudeText = longitudeText,
                            personalDataConsent = consentAccepted,
                        )
                    },
                    enabled = consentAccepted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.partner_reg_submit))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun DiveCenterRegistrationRoute(nav: NavHostController, graph: AppGraph) {
    PartnerRegistrationRoute(
        nav = nav,
        graph = graph,
        fixedKind = PartnerRegKind.DIVE_CENTER,
    )
}
