package com.divehub.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.CertificationsRepository
import com.divehub.app.data.remote.dto.CertificationDto
import com.divehub.app.ui.main.SessionViewModel
import kotlinx.coroutines.launch
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificationsRoute(
    graph: AppGraph,
    sessionVm: SessionViewModel,
    innerNav: NavController,
) {
    val user by sessionVm.user.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { CertificationsRepository(graph) }
    val authRepo = remember { AuthRepository(graph) }
    val snack = remember { SnackbarHostState() }
    var items by remember { mutableStateOf<List<CertificationDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }
    var agency by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var instructorNumber by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf("") }
    var cardUri by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        cardUri = uri?.toString()
    }

    fun reload() {
        val uid = user?.id ?: return
        scope.launch {
            loading = true
            runCatching { repo.list(uid) }
                .onSuccess { items = it }
                .onFailure { e ->
                    items = emptyList()
                    if (e !is HttpException || e.code() != 404) {
                        snack.showSnackbar(authRepo.parseErrorMessage(e))
                    }
                }
            loading = false
        }
    }

    LaunchedEffect(user?.id) {
        reload()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_certifications)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (user != null) {
                FloatingActionButton(
                    onClick = {
                        agency = ""
                        level = ""
                        instructorNumber = ""
                        issueDate = ""
                        cardUri = null
                        showAdd = true
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.certifications_add))
                }
            }
        },
    ) { padding ->
        when {
            loading -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text(stringResource(R.string.certifications_empty), style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { c ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${c.agency} — ${c.level}", style = MaterialTheme.typography.titleSmall)
                                    c.issueDate?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    c.verificationStatus?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { repo.delete(c.id) }
                                                .onSuccess { reload() }
                                                .onFailure { e ->
                                                    snack.showSnackbar(authRepo.parseErrorMessage(e))
                                                }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { if (!saving) showAdd = false },
            title = { Text(stringResource(R.string.certifications_add_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = agency,
                        onValueChange = { agency = it },
                        label = { Text(stringResource(R.string.certifications_agency)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = level,
                        onValueChange = { level = it },
                        label = { Text(stringResource(R.string.certifications_level)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = instructorNumber,
                        onValueChange = { instructorNumber = it },
                        label = { Text(stringResource(R.string.certifications_instructor_number)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = issueDate,
                        onValueChange = { issueDate = it },
                        label = { Text(stringResource(R.string.certifications_issue_date_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Text(
                            if (cardUri == null) stringResource(R.string.certifications_pick_card)
                            else stringResource(R.string.certifications_card_selected),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && agency.isNotBlank() && level.isNotBlank(),
                    onClick = {
                        val uid = user?.id ?: return@TextButton
                        saving = true
                        scope.launch {
                            runCatching {
                                repo.create(
                                    userId = uid,
                                    agency = agency,
                                    level = level,
                                    instructorNumber = instructorNumber,
                                    issueDateIso = issueDate,
                                    cardImagePathOrUrl = cardUri,
                                    context = ctx,
                                )
                            }.onSuccess {
                                showAdd = false
                                reload()
                            }.onFailure { e ->
                                snack.showSnackbar(authRepo.parseErrorMessage(e))
                            }
                            saving = false
                        }
                    },
                ) { Text(stringResource(R.string.profile_edit_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }, enabled = !saving) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
