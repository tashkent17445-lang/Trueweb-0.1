package ru.trueweb.vpn.ui

import ru.trueweb.vpn.i18n.L10n.t

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
    onHuaweiLoginClick: () -> Unit,
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
                Text(t("VPN без лишних шагов", "VPN without extra steps"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(34.dp))

                when (view) {
                    AuthView.CHOICE -> {
                        Button(
                            onClick = onHuaweiLoginClick,
                            enabled = !authInProgress,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text(t("Войти с HUAWEI ID", "Sign in with HUAWEI ID"), fontWeight = FontWeight.SemiBold) }

                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { view = AuthView.EMAIL },
                            enabled = !authInProgress,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text(t("Войти по email", "Sign in with email"), fontWeight = FontWeight.SemiBold) }

                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onTelegramLoginClick,
                            enabled = !authInProgress,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text(t("Войти через Telegram", "Sign in with Telegram"), fontWeight = FontWeight.SemiBold) }

                        TextButton(
                            onClick = { view = AuthView.PASSWORD },
                            enabled = !authInProgress
                        ) { Text(t("Войти по логину и паролю", "Sign in with username and password")) }
                    }

                    AuthView.EMAIL -> {
                        if (emailCodeSentTo.isNullOrBlank()) {
                            Text(t("Вход по email", "Email sign-in"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                t("Пришлём 6-значный код. Новый аккаунт получит пробный доступ, если он ещё не использовался.", "We will send a 6-digit code. A new account will receive trial access if it has not been used before."),
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
                                else Text(t("Отправить код", "Send code"), fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = { view = AuthView.CHOICE; onEmailReset() },
                                enabled = !authInProgress
                            ) { Text(t("Назад", "Back")) }
                        } else {
                            Text(t("Введите код", "Enter the code"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(t("Код отправлен на\n$emailCodeSentTo", "Code sent to\n$emailCodeSentTo"), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(18.dp))
                            OutlinedTextField(
                                value = code,
                                onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
                                enabled = !authInProgress,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(t("6-значный код", "6-digit code")) },
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
                                else Text(t("Войти", "Sign in"), fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                TextButton(onClick = { onEmailStart(emailCodeSentTo) }, enabled = !authInProgress) { Text(t("Отправить ещё раз", "Send again")) }
                                TextButton(
                                    onClick = { code = ""; onEmailReset() },
                                    enabled = !authInProgress
                                ) { Text(t("Изменить email", "Change email")) }
                            }
                            TextButton(onClick = { view = AuthView.CHOICE; onEmailReset() }, enabled = !authInProgress) { Text(t("Другой способ входа", "Another sign-in method")) }
                        }
                    }

                    AuthView.PASSWORD -> {
                        Text(t("Логин и пароль", "Username and password"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            t("Резервный способ входа. Логин и пароль можно задать в настройках аккаунта.", "Backup sign-in method. You can set a username and password in account settings."),
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
                            label = { Text(t("Логин", "Username")) }
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it.take(128) },
                            enabled = !authInProgress,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("Пароль", "Password")) },
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
                            else Text(t("Войти", "Sign in"), fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = { view = AuthView.CHOICE }, enabled = !authInProgress) { Text(t("Назад", "Back")) }
                    }


                }

                if (view != AuthView.PASSWORD) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onProxyClick,
                        enabled = !authInProgress,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text(t("Подключить прокси для Telegram", "Connect Telegram proxy")) }
                }

                if (!errorText.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(errorText, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    t("Email работает без Telegram. После подключения VPN Telegram можно привязать к тому же аккаунту.", "Email works without Telegram. After connecting the VPN, you can link Telegram to the same account."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
