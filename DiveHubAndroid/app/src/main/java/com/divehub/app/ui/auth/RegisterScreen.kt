package com.divehub.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.BuildConfig
import com.divehub.app.R
import com.divehub.app.ui.Routes
import com.divehub.app.ui.components.AuthScaffold

@Composable
fun RegisterRoute(nav: NavHostController, graph: AppGraph) {
    val vm: AuthViewModel = viewModel(factory = AuthViewModel.factory(graph))
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var consentAccepted by remember { mutableStateOf(false) }

        val uriHandler = LocalUriHandler.current
        val legalBase = BuildConfig.LEGAL_DOCS_BASE_URL.trimEnd('/')

        AuthScaffold(
            title = "Регистрация",
            subtitle = "Создайте аккаунт и начните планировать погружения.",
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя и фамилия") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль (мин. 8 символов)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            onClick = { uriHandler.openUri("$legalBase/privacy") },
                        ) {
                            Text(stringResource(R.string.legal_open_privacy))
                        }
                        TextButton(
                            onClick = { uriHandler.openUri("$legalBase/agreement") },
                        ) {
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
            Spacer(modifier = Modifier.height(12.dp))

            if (state.loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        vm.register(email, password, name, consentAccepted) {
                            nav.navigate(Routes.Main) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        }
                    },
                    enabled = consentAccepted,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                ) {
                    Text("Создать аккаунт")
                }
            }

            TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Назад к входу")
            }
        }
    }
}
