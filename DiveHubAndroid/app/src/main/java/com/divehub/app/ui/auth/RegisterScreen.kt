package com.divehub.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.BuildConfig
import com.divehub.app.R
import com.divehub.app.ui.Routes
import com.divehub.app.ui.components.AuthScaffold
import kotlinx.coroutines.launch

@Composable
fun RegisterRoute(nav: NavHostController, graph: AppGraph) {
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
        val ctx = LocalContext.current
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var showPassword by remember { mutableStateOf(false) }
        var consentAccepted by remember { mutableStateOf(false) }
        var emailError by remember { mutableStateOf<String?>(null) }
        var passwordError by remember { mutableStateOf<String?>(null) }

        val uriHandler = LocalUriHandler.current
        val legalBase = BuildConfig.LEGAL_DOCS_BASE_URL.trimEnd('/')

        val emailTrim = email.trim()
        val passTrim = password.trim()
        val emailOk = android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrim).matches()
        val passLenOk = passTrim.length >= 8
        val passHasLetter = passTrim.any { it.isLetter() }
        val passHasDigit = passTrim.any { it.isDigit() }
        val passOk = passLenOk && passHasLetter && passHasDigit
        val strength = when {
            passTrim.isEmpty() -> 0f
            !passLenOk -> 0.15f
            !passHasLetter || !passHasDigit -> 0.35f
            passTrim.length >= 12 -> 1f
            else -> 0.65f
        }
        val strengthLabel = when {
            passTrim.isEmpty() -> ""
            !passLenOk || !passHasLetter || !passHasDigit -> stringResource(R.string.auth_password_weak)
            passTrim.length >= 12 -> stringResource(R.string.auth_password_strong)
            else -> stringResource(R.string.auth_password_ok)
        }

        val createEnabled = consentAccepted && emailOk && passOk && !state.loading

        AuthScaffold(
            title = stringResource(R.string.auth_registration_title),
            subtitle = stringResource(R.string.auth_registration_subtitle),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            snackbar.showSnackbar(graph.application.getString(R.string.auth_oauth_apple_soon))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                ) {
                    Text(stringResource(R.string.auth_continue_apple))
                }
                Button(
                    onClick = {
                        scope.launch {
                            snackbar.showSnackbar(graph.application.getString(R.string.auth_oauth_google_soon))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                ) {
                    Text(stringResource(R.string.auth_continue_google))
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

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; emailError = null },
                    label = { Text(stringResource(R.string.auth_email_label)) },
                    placeholder = { Text("name@example.com") },
                    singleLine = true,
                    isError = emailError != null,
                    supportingText = emailError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = null },
                    label = { Text(stringResource(R.string.auth_password_label)) },
                    singleLine = true,
                    isError = passwordError != null,
                    supportingText = passwordError?.let { err -> { Text(err) } },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(
                                if (showPassword) {
                                    stringResource(R.string.auth_password_hide)
                                } else {
                                    stringResource(R.string.auth_password_show)
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                )
                if (passTrim.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.auth_password_strength), style = MaterialTheme.typography.bodySmall)
                        Text(strengthLabel, style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(
                        progress = { strength },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(
                        checked = consentAccepted,
                        onCheckedChange = { consentAccepted = it },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auth_consent_short))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { uriHandler.openUri("$legalBase/privacy") }) {
                                Text(stringResource(R.string.legal_open_privacy))
                            }
                            TextButton(onClick = { uriHandler.openUri("$legalBase/agreement") }) {
                                Text(stringResource(R.string.legal_open_agreement))
                            }
                        }
                    }
                }

                if (state.loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            emailError = null
                            passwordError = null
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrim).matches()) {
                                emailError = ctx.getString(R.string.auth_err_valid_email)
                                return@Button
                            }
                            if (passTrim.length < 8) {
                                passwordError = ctx.getString(R.string.auth_err_password_short)
                                return@Button
                            }
                            if (!passHasLetter || !passHasDigit) {
                                passwordError = ctx.getString(R.string.auth_err_password_letter_digit)
                                return@Button
                            }
                            if (!consentAccepted) return@Button
                            vm.register(emailTrim, passTrim, consentAccepted) {
                                nav.navigate(Routes.ProfileOnboarding) {
                                    popUpTo(Routes.Register) { inclusive = true }
                                }
                            }
                        },
                        enabled = createEnabled,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                    ) {
                        Text(stringResource(R.string.auth_create_account))
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.auth_have_account), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { nav.popBackStack() }) {
                        Text(stringResource(R.string.auth_sign_in))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
