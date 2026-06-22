package com.divehub.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.divehub.app.R
import com.divehub.app.ui.util.fileProviderImageUri
import com.divehub.app.ui.theme.IosDesign
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import com.divehub.app.ui.theme.iosChromePageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val AgOther = "___OTHER___"

val AgencyPresets: List<String> = listOf(
    "PADI", "SSI", "NAUI", "CMAS", "BSAC", "SDI", "TDI", "GUE", "ADAS", AgOther,
)

data class AddCertificationForm(
    val agency: String,
    val level: String,
    val certificateNumber: String,
    val instructorName: String,
    val instructorNumber: String,
    val issueDateMillis: Long,
    val cardImageUri: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCertificationScreen(
    onDismiss: () -> Unit,
    onSave: (AddCertificationForm) -> Unit,
    saving: Boolean,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val dark = isSystemInDarkTheme()
    val pageBg = iosChromePageBackground()
    val grouped = if (dark) IosDesign.DarkChrome.groupedSurface else IosDesign.Profile.groupedSurface
    val secondary = if (dark) IosDesign.DarkChrome.secondaryLabel else IosDesign.Profile.secondaryLabel
    val link = if (dark) IosDesign.DarkChrome.systemBlue else IosDesign.Profile.linkBlue

    var level by remember { mutableStateOf("") }
    var certificateNumber by remember { mutableStateOf("") }
    var instructorName by remember { mutableStateOf("") }
    var instructorNumber by remember { mutableStateOf("") }
    var cardUri by remember { mutableStateOf<String?>(null) }
    var agencyKey by remember { mutableStateOf<String?>(AgencyPresets.firstOrNull()) }
    var customAgency by remember { mutableStateOf("") }
    var issueDateMillis by remember { mutableStateOf(Instant.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }

    var ocrLoading by remember { mutableStateOf(false) }
    var showDataDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var agencyMenuExpanded by remember { mutableStateOf(false) }
    var lastOcrUri by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { u ->
        cardUri = u?.toString()
    }
    var certCameraTarget: Uri? by remember { mutableStateOf(null) }
    val takeCertPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) certCameraTarget?.let { cardUri = it.toString() }
    }

    val effectiveAgency = remember(agencyKey, customAgency) {
        when (agencyKey) {
            null -> ""
            AgOther -> customAgency.trim()
            else -> agencyKey!!.trim()
        }
    }
    val canSave = !saving && effectiveAgency.isNotBlank() && level.isNotBlank()

    LaunchedEffect(cardUri) {
        val u = cardUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return@LaunchedEffect
        ocrLoading = true
        val prepared = withContext(Dispatchers.Default) { CertificateCardCropper.cropAndSave(ctx, u) }
        val preparedKey = prepared.toString()
        if (preparedKey != cardUri) {
            cardUri = preparedKey
            ocrLoading = false
            return@LaunchedEffect
        }
        if (lastOcrUri == preparedKey) {
            ocrLoading = false
            return@LaunchedEffect
        }
        lastOcrUri = preparedKey
        val ocr = withContext(Dispatchers.Default) { CertificateOcr.extractFromCardUri(ctx, prepared) }
        ocrLoading = false
        if (!ocr.isNotEmpty()) return@LaunchedEffect
        ocr.organization?.let { o ->
            val match = AgencyPresets.find { it.equals(o, ignoreCase = true) && it != AgOther }
            if (match != null) {
                agencyKey = match
                customAgency = ""
            } else {
                agencyKey = AgOther
                customAgency = o
            }
        }
        ocr.level?.let { level = it }
        ocr.issueDateMillis?.let { issueDateMillis = it }
        ocr.instructorName?.let { instructorName = it }
        ocr.instructorNumber?.let { instructorNumber = it }
        ocr.certificateNumber?.let { certificateNumber = it }
        showDataDialog = true
    }

    val dateText = remember(issueDateMillis) {
        val d = Instant.ofEpochMilli(issueDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = issueDateMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            issueDateMillis = millis
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

    if (showDataDialog) {
        AlertDialog(
            onDismissRequest = { showDataDialog = false },
            title = { Text(stringResource(R.string.data_extracted_title)) },
            text = { Text(stringResource(R.string.data_extracted_message)) },
            confirmButton = {
                TextButton(onClick = { showDataDialog = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    Scaffold(
        containerColor = pageBg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageBg,
                    scrolledContainerColor = pageBg,
                ),
                navigationIcon = {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                    ) { Text(stringResource(R.string.common_cancel), color = link) }
                },
                title = {
                    Text(
                        stringResource(R.string.certifications_add_title),
                        color = if (dark) Color.White else MaterialTheme.colorScheme.onBackground,
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSave(
                                AddCertificationForm(
                                    agency = effectiveAgency,
                                    level = level.trim(),
                                    certificateNumber = certificateNumber.trim(),
                                    instructorName = instructorName.trim(),
                                    instructorNumber = instructorNumber.trim(),
                                    issueDateMillis = issueDateMillis,
                                    cardImageUri = cardUri,
                                ),
                            )
                        },
                        enabled = canSave,
                    ) {
                        Text(
                            stringResource(R.string.common_save),
                            color = if (canSave) link else secondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.certifications_section_details),
                color = secondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 4.dp),
            )
            Surface(
                color = grouped,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // Agency dropdown
                    ExposedDropdownMenuBox(
                        expanded = agencyMenuExpanded,
                        onExpandedChange = { newExpanded ->
                            if (!saving) agencyMenuExpanded = newExpanded
                        },
                    ) {
                        OutlinedTextField(
                            value = when {
                                agencyKey == null -> ""
                                agencyKey == AgOther && customAgency.isNotBlank() -> customAgency
                                agencyKey == AgOther -> stringResource(R.string.certifications_agency_custom_hint)
                                else -> agencyKey!!
                            },
                            onValueChange = { },
                            readOnly = true,
                            enabled = !saving,
                            label = { Text(stringResource(R.string.certifications_agency)) },
                            placeholder = { Text(stringResource(R.string.certifications_org_placeholder)) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedLabelColor = secondary,
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = agencyMenuExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = agencyMenuExpanded,
                            onDismissRequest = { agencyMenuExpanded = false },
                        ) {
                            for (a in AgencyPresets) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (a == AgOther) {
                                                stringResource(R.string.certifications_agency_other)
                                            } else {
                                                a
                                            },
                                        )
                                    },
                                    onClick = {
                                        agencyKey = a
                                        if (a != AgOther) customAgency = ""
                                        agencyMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    if (agencyKey == AgOther) {
                        OutlinedTextField(
                            value = customAgency,
                            onValueChange = { customAgency = it },
                            enabled = !saving,
                            singleLine = true,
                            label = { Text(stringResource(R.string.certifications_agency_custom_hint)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(start = 12.dp, end = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    OutlinedTextField(
                        value = level,
                        onValueChange = { level = it },
                        enabled = !saving,
                        singleLine = true,
                        label = { Text(stringResource(R.string.certifications_level)) },
                        placeholder = { Text(stringResource(R.string.certifications_level_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    HorizontalDivider(Modifier.padding(start = 12.dp, end = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 8.dp, 16.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.certifications_issue_date),
                            color = if (dark) Color.White else MaterialTheme.colorScheme.onBackground,
                        )
                        TextButton(onClick = { if (!saving) showDatePicker = true }, enabled = !saving) {
                            Text(dateText, color = link, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 12.dp, end = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    OutlinedTextField(
                        value = certificateNumber,
                        onValueChange = { certificateNumber = it },
                        enabled = !saving,
                        singleLine = true,
                        label = { Text(stringResource(R.string.certifications_number)) },
                        placeholder = { Text(stringResource(R.string.certifications_number_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    HorizontalDivider(Modifier.padding(start = 12.dp, end = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    OutlinedTextField(
                        value = instructorName,
                        onValueChange = { instructorName = it },
                        enabled = !saving,
                        singleLine = true,
                        label = { Text(stringResource(R.string.certifications_instructor_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    HorizontalDivider(Modifier.padding(start = 12.dp, end = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    OutlinedTextField(
                        value = instructorNumber,
                        onValueChange = { instructorNumber = it },
                        enabled = !saving,
                        singleLine = true,
                        label = { Text(stringResource(R.string.certifications_instructor_number)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .padding(bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.certifications_section_photo),
                color = secondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp),
            )
            Surface(
                color = grouped,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (cardUri != null) {
                        AsyncImage(
                            model = Uri.parse(cardUri),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .padding(8.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (ocrLoading) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.certifications_ocr_running), color = secondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = link,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Text(
                                stringResource(R.string.photo_choose_gallery),
                                color = link,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TextButton(
                            onClick = {
                                val f = File(ctx.cacheDir, "cert_card_${System.currentTimeMillis()}.jpg")
                                f.parentFile?.mkdirs()
                                val u = fileProviderImageUri(ctx, f)
                                certCameraTarget = u
                                runCatching { takeCertPhoto.launch(u) }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = null,
                                tint = link,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Text(
                                stringResource(R.string.photo_choose_camera),
                                color = link,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (cardUri != null && !ocrLoading) {
                        TextButton(
                            onClick = {
                                val u = runCatching { Uri.parse(cardUri) }.getOrNull() ?: return@TextButton
                                scope.launch {
                                    ocrLoading = true
                                    val ocr = withContext(Dispatchers.Default) { CertificateOcr.extractFromCardUri(ctx, u) }
                                    ocrLoading = false
                                    if (!ocr.isNotEmpty()) {
                                        ocrLoading = false
                                        return@launch
                                    }
                                    ocr.organization?.let { o ->
                                        val match = AgencyPresets.find { it.equals(o, ignoreCase = true) && it != AgOther }
                                        if (match != null) {
                                            agencyKey = match
                                            customAgency = ""
                                        } else {
                                            agencyKey = AgOther
                                            customAgency = o
                                        }
                                    }
                                    ocr.level?.let { level = it }
                                    ocr.issueDateMillis?.let { issueDateMillis = it }
                                    ocr.instructorName?.let { instructorName = it }
                                    ocr.instructorNumber?.let { instructorNumber = it }
                                    ocr.certificateNumber?.let { certificateNumber = it }
                                    snack.showSnackbar(ctx.getString(R.string.certifications_ocr_applied))
                                }
                            },
                        ) { Text(stringResource(R.string.certifications_extract_ocr), color = link) }
                    }
                }
            }
        }
    }
}