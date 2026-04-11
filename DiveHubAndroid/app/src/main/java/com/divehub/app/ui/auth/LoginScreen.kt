package com.divehub.app.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.ui.Routes
import com.divehub.app.ui.components.AuthScaffold

@Composable
fun LoginRoute(nav: NavHostController, graph: AppGraph) {
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
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        AuthScaffold(
            title = "Добро пожаловать",
            subtitle = "Войдите в DiveHub, чтобы продолжить.",
        ) {
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
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            TextButton(
                onClick = { nav.navigate(Routes.ForgotPassword) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Забыли пароль?")
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
                    Text("Войти")
                }
            }
            TextButton(
                onClick = { nav.navigate(Routes.Register) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Создать аккаунт")
            }
            TextButton(
                onClick = { nav.navigate(Routes.PartnerRegistration) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_partner_application))
            }
        }
    }
}
