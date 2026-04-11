package com.divehub.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.ui.components.AuthScaffold
import com.divehub.app.ui.theme.IosDesign

@Composable
fun ForgotPasswordRoute(nav: NavHostController, graph: AppGraph) {
    val vm: ForgotPasswordViewModel = viewModel(factory = ForgotPasswordViewModel.factory(graph))
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearMessages()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) {
        AuthScaffold(
            title = "Восстановление пароля",
            subtitle = "Введите email, подтвердите код и установите новый пароль.",
        ) {
            StepProgress(step = state.step)
            Spacer(modifier = Modifier.height(16.dp))

            when (state.step) {
                PasswordResetStep.EMAIL -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ActionButton(state.loading, "Отправить код") { vm.sendCode(email) }
                }

                PasswordResetStep.VERIFICATION_CODE -> {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("Код подтверждения") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.countdown > 0) {
                        Text("Повторная отправка через ${state.countdown} сек.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        TextButton(onClick = { vm.sendCode(email) }) { Text("Отправить код повторно") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionButton(state.loading, "Проверить код") { vm.verifyCode(email, code) }
                    TextButton(onClick = { vm.setStep(PasswordResetStep.EMAIL) }) { Text("Назад") }
                }

                PasswordResetStep.NEW_PASSWORD -> {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Новый пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Повторите пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ActionButton(state.loading, "Сохранить пароль") {
                        vm.resetPassword(email, code, newPassword, confirmPassword) {
                            nav.popBackStack()
                        }
                    }
                    TextButton(onClick = { vm.setStep(PasswordResetStep.VERIFICATION_CODE) }) { Text("Назад") }
                }
            }
        }
    }
}

@Composable
private fun StepProgress(step: PasswordResetStep) {
    val activeColor = MaterialTheme.colorScheme.primary
    val passiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val current = when (step) {
            PasswordResetStep.EMAIL -> 0
            PasswordResetStep.VERIFICATION_CODE -> 1
            PasswordResetStep.NEW_PASSWORD -> 2
        }
        repeat(3) { idx ->
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (idx <= current) activeColor else passiveColor)
                    .padding(6.dp),
            )
            if (idx < 2) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .height(2.dp)
                        .weight(1f)
                        .background(if (idx < current) activeColor else passiveColor),
                )
            }
        }
    }
}

@Composable
private fun ActionButton(loading: Boolean, text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
        shape = IosDesign.CardCorner,
    ) {
        if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
        else Text(text)
    }
}
