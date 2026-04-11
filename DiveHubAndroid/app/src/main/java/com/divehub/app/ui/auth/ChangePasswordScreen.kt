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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.ui.Routes
import com.divehub.app.ui.components.AuthScaffold
import kotlinx.coroutines.launch

@Composable
fun ChangePasswordRoute(nav: NavHostController, graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val vm: PasswordViewModel = viewModel(factory = PasswordViewModel.factory(graph))
    val loading by vm.loading.collectAsState()
    val err by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(err) {
        err?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) {
        var current by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }

        AuthScaffold(
            title = "Смена пароля",
            subtitle = "Установите новый пароль для безопасности аккаунта.",
        ) {
            OutlinedTextField(
                value = current,
                onValueChange = { current = it },
                label = { Text("Текущий пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = newPass,
                onValueChange = { newPass = it },
                label = { Text("Новый пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Повторите новый пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        if (newPass != confirm) {
                            scope.launch { snackbar.showSnackbar("Пароли не совпадают") }
                            return@Button
                        }
                        vm.submit(current, newPass) {
                            nav.navigate(Routes.Main) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                    enabled = current.isNotBlank() && newPass.length >= 8 && newPass == confirm,
                ) {
                    Text("Сохранить")
                }
            }
            TextButton(
                onClick = {
                    scope.launch {
                        graph.tokenStore.clearSession()
                        graph.resetApiClient()
                        nav.navigate(Routes.Login) {
                            popUpTo(Routes.ChangePassword) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Выйти")
            }
        }
    }
}
