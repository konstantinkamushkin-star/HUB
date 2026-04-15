package com.divehub.app.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
        val passHasLetter = newPass.any { it.isLetter() }
        val passHasDigit = newPass.any { it.isDigit() }
        val canSubmit = current.isNotBlank() && newPass.isNotBlank() && confirm.isNotBlank()

        AuthScaffold(
            title = stringResource(R.string.force_password_change_title),
            subtitle = stringResource(R.string.force_password_change_subtitle),
        ) {
            Icon(
                imageVector = Icons.Default.LockReset,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = current,
                onValueChange = { current = it },
                label = { Text(stringResource(R.string.auth_current_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = newPass,
                onValueChange = { newPass = it },
                label = { Text(stringResource(R.string.auth_new_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text(stringResource(R.string.auth_confirm_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_err_password_letter_digit),
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        if (newPass.length < 8) {
                            scope.launch { snackbar.showSnackbar(graph.application.getString(R.string.auth_err_password_short)) }
                            return@Button
                        }
                        if (!passHasLetter || !passHasDigit) {
                            scope.launch { snackbar.showSnackbar(graph.application.getString(R.string.auth_err_password_letter_digit)) }
                            return@Button
                        }
                        if (newPass != confirm) {
                            scope.launch { snackbar.showSnackbar(graph.application.getString(R.string.auth_passwords_do_not_match)) }
                            return@Button
                        }
                        vm.submit(current, newPass) {
                            nav.navigate(Routes.Main) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                    enabled = canSubmit,
                ) {
                    Text(stringResource(R.string.auth_save_new_password))
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
                Text(stringResource(R.string.profile_logout))
            }
        }
    }
}
