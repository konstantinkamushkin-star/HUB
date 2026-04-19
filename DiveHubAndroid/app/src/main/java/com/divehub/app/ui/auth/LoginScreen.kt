package com.divehub.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.ui.Routes
import com.divehub.app.ui.components.AuthScaffold
import com.divehub.app.ui.components.GoogleSignInBrandButtonLabel
import kotlinx.coroutines.launch

@Composable
fun LoginRoute(nav: NavHostController, graph: AppGraph) {
    val vm: AuthViewModel = viewModel(factory = AuthViewModel.factory(graph))
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        val webClientId = stringResource(R.string.google_oauth_web_client_id)
        val startGoogleSignIn = rememberGoogleSignInStarter(webClientId = webClientId) { result ->
            result
                .onSuccess { p ->
                    vm.signInWithGoogle(
                        idToken = p.idToken,
                        email = p.email,
                        firstName = p.givenName,
                        lastName = p.familyName,
                    ) { mustChange ->
                        if (mustChange) {
                            nav.navigate(Routes.ChangePassword) { launchSingleTop = true }
                        } else {
                            nav.navigate(Routes.Main) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        }
                    }
                }
                .onFailure { e ->
                    if (e is GetCredentialCancellationException) return@onFailure
                    scope.launch {
                        val msg = when {
                            e.message == "web_client_id_missing" ->
                                graph.application.getString(R.string.auth_google_not_configured)
                            else -> e.message ?: graph.application.getString(R.string.common_error)
                        }
                        snackbar.showSnackbar(msg)
                    }
                }
        }

        AuthScaffold(
            title = stringResource(R.string.auth_login_title),
            subtitle = stringResource(R.string.auth_login_subtitle),
        ) {
            TextButton(
                onClick = { startGoogleSignIn() },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                GoogleSignInBrandButtonLabel(title = stringResource(R.string.auth_continue_google))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.auth_or),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                HorizontalDivider(Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            TextButton(
                onClick = { nav.navigate(Routes.ForgotPassword) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.auth_forgot_password))
            }
            Spacer(modifier = Modifier.height(20.dp))

            if (state.loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        vm.login(email, password) { mustChange ->
                            if (mustChange) {
                                nav.navigate(Routes.ChangePassword) { launchSingleTop = true }
                            } else {
                                nav.navigate(Routes.Main) {
                                    popUpTo(Routes.Login) { inclusive = true }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 50.dp),
                ) {
                    Text(stringResource(R.string.auth_sign_in))
                }
            }
            TextButton(
                onClick = { nav.navigate(Routes.Register) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.auth_create_account))
            }
            TextButton(
                onClick = { nav.navigate(Routes.PartnerRegistration) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_partner_application))
            }
            TextButton(
                onClick = { nav.navigate(Routes.DiveCenterRegistration) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_dive_center_registration))
            }
        }
    }
}
