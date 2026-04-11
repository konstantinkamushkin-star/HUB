package com.divehub.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.divehub.app.ui.main.SessionViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileRoute(
    graph: AppGraph,
    innerNav: NavController,
    sessionVm: SessionViewModel,
) {
    val user by sessionVm.user.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository(graph) }
    val snackbar = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }

    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en") }
    var avatarUrl by remember { mutableStateOf("") }

    LaunchedEffect(user) {
        val u = user ?: return@LaunchedEffect
        first = u.firstName.orEmpty()
        last = u.lastName.orEmpty()
        phone = u.phone.orEmpty()
        bio = u.bio.orEmpty()
        language = u.language?.trim()?.ifBlank { null } ?: "en"
        avatarUrl = u.avatarUrl.orEmpty()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        if (user == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            OutlinedTextField(
                value = first,
                onValueChange = { first = it },
                label = { Text(stringResource(R.string.profile_edit_first_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = last,
                onValueChange = { last = it },
                label = { Text(stringResource(R.string.profile_edit_last_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.profile_edit_phone)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text(stringResource(R.string.profile_edit_bio)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = language,
                onValueChange = { language = it },
                label = { Text(stringResource(R.string.profile_edit_language)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = avatarUrl,
                onValueChange = { avatarUrl = it },
                label = { Text(stringResource(R.string.profile_edit_avatar_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (first.isBlank() || last.isBlank()) {
                        scope.launch {
                            snackbar.showSnackbar(ctx.getString(R.string.profile_edit_names_required))
                        }
                        return@Button
                    }
                    scope.launch {
                        saving = true
                        try {
                            runCatching {
                                repo.updateProfile(
                                    firstName = first,
                                    lastName = last,
                                    phone = phone.ifBlank { null },
                                    bio = bio.ifBlank { null },
                                    language = language.ifBlank { "en" },
                                    avatarUrl = avatarUrl.ifBlank { null },
                                )
                            }
                                .onSuccess { updated ->
                                    sessionVm.onUserUpdated(updated)
                                    snackbar.showSnackbar(ctx.getString(R.string.profile_edit_saved))
                                    innerNav.popBackStack()
                                }
                                .onFailure { e ->
                                    snackbar.showSnackbar(repo.parseErrorMessage(e))
                                }
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.profile_edit_save))
                }
            }
        }
    }
}
