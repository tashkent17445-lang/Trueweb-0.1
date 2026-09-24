package ru.trueweb.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class AuthView { CHOICE, EMAIL, PASSWORD }

@Composable
fun AuthScreen(
    authInProgress: Boolean,
    errorText: String?,
    themeMode: TrueWebThemeMode,
    emailCodeSentTo: String?,
    onProxyClick: () -> Unit,
    onTelegramLoginClick: () -> Unit,
    onEmailStart: (String) -> Unit,
    onEmailVerify: (String, String) -> Unit,
    onEmailReset: () -> Unit,
    onPasswordLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf(emailCodeSentTo.orEmpty()) }
    var code by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var view by remember { mutableStateOf(if (!emailCodeSentTo.isNullOrBlank()) AuthView.EMAIL else AuthView.CHOICE) }

    LaunchedEffect(emailCodeSentTo) {
        if (!emailCodeSentTo.isNullOrBlank()) {
            email = emailCodeSentTo
            view = AuthView.EMAIL
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🌐", fontSize = 72.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "TrueWeb",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text("VPN без лишних шагов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(34.dp))

                when (view) {
                    AuthView.CHOICE -> {
                        Button(
                            onClick = { view = AuthView.EMAIL },
                            enabled = !authInProgress,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("Войти по email", fontWeight = FontWeight.SemiBold) }

                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onTelegramLoginClick,
                            enabled = !authInProgress,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("Войти через Telegram", fontWeight = FontWeight.SemiBold) }

                        TextButton(
                            onClick = { view = AuthView.PASSWORD },
                            enabled = !authInProgress
                        ) { Text("Войти по логину и паролю") }
                    }

                    AuthView.EMAIL -> {
                        if (emailCodeSentTo.isNullOrBlank()) {
                            Text("Вход по email", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Пришлём 6-значный код. Новый аккаунт получит пробный доступ, если он ещё не использовался.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(18.dp))
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim().take(160) },
                                enabled = !authInProgress,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Email") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onEmailStart(email) },
                                enabled = !authInProgress && email.contains("@") && email.length >= 5,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                if (authInProgress) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                else Text("Отправить код", fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = { view = AuthView.CHOICE; onEmailReset() },
                                enabled = !authInProgress
                            ) { Text("Назад") }
                        } else {
                            Text("Введите код", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Код отправлен на\n$emailCodeSentTo", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(18.dp))
                            OutlinedTextField(
                                value = code,
                                onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
                                enabled = !authInProgress,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("6-значный код") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onEmailVerify(emailCodeSentTo, code) },
                                enabled = !authInProgress && code.length == 6,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                if (authInProgress) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                else Text("Войти", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                TextButton(onClick = { onEmailStart(emailCodeSentTo) }, enabled = !authInProgress) { Text("Отправить ещё раз") }
                                TextButton(
                                    onClick = { code = ""; onEmailReset() },
                                    enabled = !authInProgress
                                ) { Text("Изменить email") }
                            }
                            TextButton(onClick = { view = AuthView.CHOICE; onEmailReset() }, enabled = !authInProgress) { Text("Другой способ входа") }
                        }
                    }

                    AuthView.PASSWORD -> {
                        Text("Логин и пароль", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Резервный способ входа. Логин и пароль можно задать в настройках аккаунта.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(18.dp))
                        OutlinedTextField(
                            value = login,
                            onValueChange = { login = it.trim().take(64) },
                            enabled = !authInProgress,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Логин") }
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it.take(128) },
                            enabled = !authInProgress,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Пароль") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onPasswordLogin(login, password) },
                            enabled = !authInProgress && login.length >= 3 && password.length >= 8,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            if (authInProgress) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text("Войти", fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = { view = AuthView.CHOICE }, enabled = !authInProgress) { Text("Назад") }
                    }
                }

                if (view != AuthView.PASSWORD) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onProxyClick,
                        enabled = !authInProgress,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("Подключить прокси для Telegram") }
                }

                if (!errorText.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(errorText, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "Email работает без Telegram. После подключения VPN Telegram можно привязать к тому же аккаунту.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
