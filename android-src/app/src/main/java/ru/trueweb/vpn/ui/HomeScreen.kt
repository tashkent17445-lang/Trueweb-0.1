package ru.trueweb.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.trueweb.vpn.BuildConfig
import ru.trueweb.vpn.model.*
import ru.trueweb.vpn.vpn.TrueWebVpnService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    subscription: SubscriptionInfo,
    refreshing: Boolean,
    trialLoading: Boolean,
    errorText: String?,
    vpnState: TrueWebVpnService.TunnelState,
    vpnError: String?,
    vpnMode: VpnMode,
    themeMode: TrueWebThemeMode,
    tariffs: List<TariffOption>,
    deviceProduct: DeviceProduct?,
    devices: List<DeviceItem>,
    managementLoading: Boolean,
    managementError: String?,
    paymentLoadingProduct: String?,
    paymentChecking: Boolean,
    paymentMessage: String?,
    hasPendingPayment: Boolean,
    accountActionLoading: Boolean,
    accountActionMessage: String?,
    batteryOptimizationRestricted: Boolean,
    showBatteryOptimizationNotice: Boolean,
    geoDataLastUpdatedMs: Long,
    geoDataRefreshing: Boolean,
    appUpdateChecking: Boolean,
    onBatterySettings: () -> Unit,
    onDismissBatteryNotice: () -> Unit,
    onGeoDataRefresh: () -> Unit,
    onAppUpdateCheck: () -> Unit,
    onConnectClick: () -> Unit,
    onTrialClick: () -> Unit,
    onRefresh: () -> Unit,
    onManagementRefresh: () -> Unit,
    onPay: (String) -> Unit,
    onCheckPayment: () -> Unit,
    onDismissPendingPayment: () -> Unit,
    onDeleteDevice: (Int) -> Unit,
    onThemeChanged: (TrueWebThemeMode) -> Unit,
    showTelegramLink: Boolean,
    onLinkTelegram: () -> Unit,
    onSetPasswordCredentials: (String, String) -> Unit,
    onTelegramGroup: () -> Unit,
    onSupport: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLogout: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(SettingsPage.SUBSCRIPTION) }
    var settingsDeviceToDelete by remember { mutableStateOf<DeviceItem?>(null) }
    var settingsMessage by remember { mutableStateOf<String?>(null) }
    var legalTitle by remember { mutableStateOf<String?>(null) }
    var legalText by remember { mutableStateOf<String?>(null) }
    var showPasswordSetup by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }

    if (!legalTitle.isNullOrBlank() && !legalText.isNullOrBlank()) {
        LegalDocumentScreen(
            title = legalTitle!!,
            text = legalText!!,
            onBack = { legalTitle = null; legalText = null }
        )
        return
    }

    val vpnOn = vpnState == TrueWebVpnService.TunnelState.RUNNING ||
        vpnState == TrueWebVpnService.TunnelState.CONNECTING

    val buttonTransition = rememberInfiniteTransition(label = "vpn-button-live")
    val glowX by buttonTransition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.66f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vpn-glow-x"
    )
    val glowY by buttonTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vpn-glow-y"
    )
    val edgePulse by buttonTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vpn-edge-pulse"
    )

    val globeColor by animateColorAsState(
        targetValue = if (vpnOn) Color.White else Color(0xFFFF3D47),
        animationSpec = tween(450),
        label = "globe-color"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TrueWeb",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { settingsPage = SettingsPage.SUBSCRIPTION; settingsMessage = null; showSettings = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("⚙", fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("↻", fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
                }
                TextButton(
                    onClick = onLogout,
                    modifier = Modifier.height(44.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text("Выйти")
                }
            }

            if (!errorText.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    errorText,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            if (showTelegramLink) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Привязать Telegram", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (vpnState == TrueWebVpnService.TunnelState.RUNNING)
                                    "VPN уже работает — теперь Telegram откроется."
                                else "Сначала включите VPN, затем привяжите старый аккаунт.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = onLinkTelegram,
                            enabled = vpnState == TrueWebVpnService.TunnelState.RUNNING,
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Привязать") }
                    }
                }
            }


            if (showBatteryOptimizationNotice) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("VPN во время сна", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Android может ограничивать TrueWeb при выключенном экране. Разрешите работу без ограничений батареи, чтобы VPN оставался стабильнее в фоне.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDismissBatteryNotice) { Text("Позже") }
                            TextButton(onClick = onBatterySettings) { Text("Настроить") }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(0.7f))

            val buttonModifier = Modifier
                .size(194.dp)
                .shadow(if (themeMode == TrueWebThemeMode.DARK) 0.dp else 8.dp, CircleShape)
                .drawBehind {
                    if (vpnOn) {
                        val brush = Brush.radialGradient(
                            colors = listOf(
                                TrueWebGreen,
                                Color(0xFF246F1D),
                                Color(0xFF10230E),
                                Color(0xFF020302)
                            ),
                            center = Offset(size.width * glowX, size.height * glowY),
                            radius = size.minDimension * 0.76f
                        )
                        drawCircle(brush = brush, radius = size.minDimension / 2f)
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.30f * edgePulse),
                            radius = size.minDimension / 2f,
                            style = Stroke(width = 10.dp.toPx())
                        )
                    } else {
                        // Keep the same animated “breathing” effect as the active state,
                        // but make the disconnected state unmistakably red.
                        val brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF3D47),
                                Color(0xFF9B2028),
                                Color(0xFF3A0B0F),
                                Color(0xFF020202)
                            ),
                            center = Offset(size.width * glowX, size.height * glowY),
                            radius = size.minDimension * 0.76f
                        )
                        drawCircle(brush = brush, radius = size.minDimension / 2f)
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.30f * edgePulse),
                            radius = size.minDimension / 2f,
                            style = Stroke(width = 10.dp.toPx())
                        )
                    }
                }
                .clickable(onClick = onConnectClick)

            Surface(
                modifier = buttonModifier,
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(
                    if (vpnOn) 2.dp else 1.dp,
                    if (vpnOn) TrueWebGreen else Color(0xFFFF3D47)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    GlobeIcon(color = globeColor, modifier = Modifier.size(98.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    vpnState == TrueWebVpnService.TunnelState.RUNNING && vpnMode == VpnMode.WHITELIST -> "VPN подключён · резервный режим"
                    vpnState == TrueWebVpnService.TunnelState.RUNNING -> "VPN подключён"
                    vpnState == TrueWebVpnService.TunnelState.CONNECTING -> "Подключение…"
                    vpnState == TrueWebVpnService.TunnelState.ERROR -> "Что-то пошло не так"
                    !vpnError.isNullOrBlank() -> "VPN отключён"
                    subscription.active -> "VPN выключен"
                    subscription.trialAvailable -> "Активируйте бесплатный доступ"
                    else -> "Подписка не активна"
                },
                fontWeight = FontWeight.SemiBold,
                color = if (vpnOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(14.dp))

            if (!vpnOn && !vpnError.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    vpnError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            Spacer(Modifier.weight(0.9f))

            SubscriptionSummaryCard(
                info = subscription,
                onManage = { settingsPage = SettingsPage.SUBSCRIPTION; settingsMessage = null; showSettings = true }
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onTelegramGroup,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Группа") }
                OutlinedButton(
                    onClick = onSupport,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Поддержка") }
            }
        }
    }

    if (showSettings) {
        LaunchedEffect(Unit) { onManagementRefresh() }
        val clipboard = LocalClipboardManager.current

        Dialog(
            onDismissRequest = { showSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "TrueWeb Android v${BuildConfig.VERSION_NAME}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(
                            onClick = { showSettings = false },
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Закрыть") }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsNavButton("Подписка", settingsPage == SettingsPage.SUBSCRIPTION) {
                            settingsPage = SettingsPage.SUBSCRIPTION
                            settingsMessage = null
                        }
                        SettingsNavButton("Устройства", settingsPage == SettingsPage.DEVICES) {
                            settingsPage = SettingsPage.DEVICES
                            settingsMessage = null
                        }
                        SettingsNavButton("Приложение", settingsPage == SettingsPage.APP) {
                            settingsPage = SettingsPage.APP
                            settingsMessage = null
                        }
                        SettingsNavButton("Документы", settingsPage == SettingsPage.DOCUMENTS) {
                            settingsPage = SettingsPage.DOCUMENTS
                            settingsMessage = null
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(18.dp)
                        ) {
                            if (managementLoading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(12.dp))
                            }
                            if (!managementError.isNullOrBlank()) {
                                Text(managementError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(10.dp))
                            }

                            when (settingsPage) {
                                SettingsPage.SUBSCRIPTION -> {
                                    Text("Подписка и тарифы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        when {
                                            subscription.trialPending -> "Пробный доступ готов · ${subscription.expiresLabel}"
                                            subscription.unlimited -> "Активна · без ограничения по сроку · устройства: ${subscription.devicesLabel}"
                                            subscription.active -> "Активна до ${subscription.expiresLabel} · осталось ${subscription.daysLeft} дн. · устройства: ${subscription.devicesLabel}"
                                            else -> "Не активна · ${subscription.expiresLabel} · устройства: ${subscription.devicesLabel}"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (!subscription.subscriptionUrl.isNullOrBlank()) {
                                        Spacer(Modifier.height(14.dp))
                                        OutlinedButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(subscription.subscriptionUrl!!))
                                                settingsMessage = "Ссылка подписки скопирована"
                                            },
                                            shape = RoundedCornerShape(14.dp)
                                        ) { Text("Копировать ссылку подписки") }
                                    }

                                    if (subscription.trialAvailable) {
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = onTrialClick,
                                            enabled = !trialLoading,
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            if (trialLoading) {
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Активируем…")
                                            } else Text("Получить 3 дня бесплатно")
                                        }
                                    }

                                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outline)
                                    Text("Тарифы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(6.dp))

                                    if (subscription.unlimited) {
                                        Text("У вас безлимитная подписка — продление не требуется.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (tariffs.isEmpty() && !managementLoading) {
                                        Text("Доступных тарифов сейчас нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        tariffs.forEach { tariff ->
                                            Spacer(Modifier.height(8.dp))
                                            TariffButton(
                                                tariff = tariff,
                                                loading = paymentLoadingProduct == tariff.code,
                                                enabled = paymentLoadingProduct == null && !paymentChecking,
                                                onClick = { onPay(tariff.code) }
                                            )
                                        }
                                    }

                                    if (deviceProduct != null && subscription.devicesLimit > 0) {
                                        Spacer(Modifier.height(16.dp))
                                        Text("Дополнительное устройство", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { onPay(deviceProduct.code) },
                                            enabled = paymentLoadingProduct == null && !paymentChecking,
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            if (paymentLoadingProduct == deviceProduct.code) {
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Создаём платёж…")
                                            } else Text("+ ${deviceProduct.title} — ${deviceProduct.price} ₽")
                                        }
                                    }

                                    if (!paymentMessage.isNullOrBlank() || hasPendingPayment) {
                                        Spacer(Modifier.height(16.dp))
                                        Text(paymentMessage ?: "Есть незавершённый платёж", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = onCheckPayment, enabled = !paymentChecking) {
                                                Text(if (paymentChecking) "Проверяем…" else "Проверить оплату")
                                            }
                                            TextButton(onClick = onDismissPendingPayment, enabled = !paymentChecking) { Text("Скрыть") }
                                        }
                                    }
                                }

                                SettingsPage.DEVICES -> {
                                    Text("Устройства", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        if (subscription.devicesLimit <= 0) "Используется: ${subscription.devicesUsed}"
                                        else "Используется: ${subscription.devicesUsed} из ${subscription.devicesLimit}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(12.dp))

                                    if (devices.isEmpty()) {
                                        Text(
                                            if (managementLoading) "Загружаем устройства…" else "Устройства не найдены",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        devices.forEach { device ->
                                            Spacer(Modifier.height(8.dp))
                                            DeviceRow(
                                                device = device,
                                                enabled = !managementLoading,
                                                onDelete = { settingsDeviceToDelete = device }
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))
                                    OutlinedButton(
                                        onClick = onManagementRefresh,
                                        enabled = !managementLoading,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text(if (managementLoading) "Обновляем…" else "Обновить список") }
                                }

                                SettingsPage.APP -> {
                                    Text("Приложение", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                                    SettingsSectionTitle("Сервер")
                                    SettingsInfoBox("Автовыбор · основной → запасной → резервный")

                                    SettingsSectionTitle("Тема")
                                    ThemeChoice("Тёмная — чёрная", TrueWebThemeMode.DARK, themeMode, onThemeChanged)
                                    ThemeChoice("Светлая — белая", TrueWebThemeMode.LIGHT, themeMode, onThemeChanged)

                                    SettingsSectionTitle("Android")
                                    OutlinedButton(
                                        onClick = onBatterySettings,
                                        enabled = batteryOptimizationRestricted,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(if (batteryOptimizationRestricted) "🔋 Разрешить работу без ограничений" else "✓ Батарея: без ограничений")
                                    }

                                    SettingsSectionTitle("Данные")
                                    OutlinedButton(
                                        onClick = onRefresh,
                                        enabled = !refreshing,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text(if (refreshing) "Обновляем…" else "↻ Обновить данные") }

                                    SettingsSectionTitle("GeoData")
                                    val geoText = if (geoDataLastUpdatedMs > 0L) {
                                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(geoDataLastUpdatedMs))
                                    } else "встроенная база"
                                    Text("GeoData: $geoText", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = onGeoDataRefresh,
                                        enabled = !geoDataRefreshing,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text(if (geoDataRefreshing) "Обновляем GeoData…" else "↻ Обновить GeoData сейчас") }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "TrueWeb автоматически обновляет geoip.dat и geosite.dat. Если источник временно недоступен, VPN продолжит работать на последней локальной версии.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    SettingsSectionTitle("Диагностика")
                                    OutlinedButton(
                                        onClick = {
                                            val stateLabel = when (vpnState) {
                                                TrueWebVpnService.TunnelState.RUNNING -> "RUNNING"
                                                TrueWebVpnService.TunnelState.CONNECTING -> "CONNECTING"
                                                TrueWebVpnService.TunnelState.ERROR -> "ERROR"
                                                else -> "STOPPED"
                                            }
                                            val text = buildString {
                                                appendLine("TrueWeb Android ${BuildConfig.VERSION_NAME}")
                                                appendLine("VPN: $stateLabel")
                                                appendLine("Mode: ${vpnMode.name}")
                                                appendLine("Server: ${subscription.serverName}")
                                                appendLine("Subscription active: ${subscription.active}")
                                                appendLine("Days left: ${subscription.daysLeft}")
                                                appendLine("Devices: ${subscription.devicesLabel}")
                                                appendLine("GeoData: $geoText")
                                                if (!vpnError.isNullOrBlank()) appendLine("VPN error: $vpnError")
                                            }
                                            clipboard.setText(AnnotatedString(text.trim()))
                                            settingsMessage = "Диагностика скопирована"
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text("Скопировать диагностику") }

                                    SettingsSectionTitle("Обновления")
                                    OutlinedButton(
                                        onClick = onAppUpdateCheck,
                                        enabled = !appUpdateChecking,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text(if (appUpdateChecking) "Проверяем обновление…" else "Проверить обновления") }

                                    SettingsSectionTitle("Аккаунт")
                                    OutlinedButton(
                                        onClick = { showPasswordSetup = true },
                                        enabled = !accountActionLoading,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text("Логин и пароль") }
                                    if (showTelegramLink) {
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { onLinkTelegram(); showSettings = false },
                                            modifier = Modifier.fillMaxWidth().height(50.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) { Text("Привязать Telegram") }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { onLogout(); showSettings = false },
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                    ) { Text("Выйти из аккаунта") }
                                }

                                SettingsPage.DOCUMENTS -> {
                                    Text("Документы и помощь", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Документы TrueWeb и быстрые ссылки на связь.", color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    SettingsSectionTitle("Документы")
                                    SettingsWideButton(LegalDocuments.PRIVACY_TITLE) {
                                        legalTitle = LegalDocuments.PRIVACY_TITLE
                                        legalText = LegalDocuments.PRIVACY
                                        showSettings = false
                                    }
                                    SettingsWideButton(LegalDocuments.TERMS_TITLE) {
                                        legalTitle = LegalDocuments.TERMS_TITLE
                                        legalText = LegalDocuments.TERMS
                                        showSettings = false
                                    }
                                    SettingsWideButton(LegalDocuments.RULES_TITLE) {
                                        legalTitle = LegalDocuments.RULES_TITLE
                                        legalText = LegalDocuments.RULES
                                        showSettings = false
                                    }
                                    SettingsWideButton(LegalDocuments.DELETION_TITLE) {
                                        legalTitle = LegalDocuments.DELETION_TITLE
                                        legalText = LegalDocuments.DELETION
                                        showSettings = false
                                    }

                                    Text(
                                        "Версия ${BuildConfig.VERSION_NAME} · Оператор: ${LegalDocuments.OPERATOR}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 10.dp)
                                    )

                                    SettingsSectionTitle("Связаться")
                                    SettingsWideButton("Группа TrueWeb в Telegram", onTelegramGroup)
                                    SettingsWideButton("Поддержка TrueWeb", onSupport)

                                    SettingsSectionTitle("Удаление аккаунта")
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text("Удаление аккаунта", fontWeight = FontWeight.SemiBold)
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Удаление необратимо. Перед удалением приложение попросит подтверждение.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            Button(
                                                onClick = { showDeleteAccount = true },
                                                enabled = !accountActionLoading,
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                            ) { Text("Удалить аккаунт") }
                                        }
                                    }
                                }
                            }

                            if (!settingsMessage.isNullOrBlank()) {
                                Spacer(Modifier.height(14.dp))
                                Text(settingsMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!accountActionMessage.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(accountActionMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    settingsDeviceToDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { settingsDeviceToDelete = null },
            title = { Text("Удалить устройство?") },
            text = {
                Text(
                    if (device.isCurrent) "Это текущее устройство. После удаления оно может зарегистрироваться снова при обновлении подключения."
                    else "${device.title}\n\nПосле удаления освободится один слот."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsDeviceToDelete = null
                    onDeleteDevice(device.id)
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { settingsDeviceToDelete = null }) { Text("Отмена") } }
        )
    }

    if (showPasswordSetup) {
        var login by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var repeatPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!accountActionLoading) showPasswordSetup = false },
            title = { Text("Логин и пароль") },
            text = {
                Column {
                    Text("Задайте резервный способ входа. Пароль хранится на сервере только в виде стойкого хеша.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = login, onValueChange = { login = it.trim().take(64) }, singleLine = true, label = { Text("Логин") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it.take(128) }, singleLine = true, label = { Text("Пароль") }, visualTransformation = PasswordVisualTransformation())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = repeatPassword, onValueChange = { repeatPassword = it.take(128) }, singleLine = true, label = { Text("Повторите пароль") }, visualTransformation = PasswordVisualTransformation())
                    if (password.isNotEmpty() && repeatPassword.isNotEmpty() && password != repeatPassword) {
                        Text("Пароли не совпадают", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSetPasswordCredentials(login, password); showPasswordSetup = false },
                    enabled = !accountActionLoading && login.length >= 3 && password.length >= 8 && password == repeatPassword
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showPasswordSetup = false }, enabled = !accountActionLoading) { Text("Отмена") } }
        )
    }

    if (showDeleteAccount) {
        AlertDialog(
            onDismissRequest = { if (!accountActionLoading) showDeleteAccount = false },
            title = { Text("Удалить аккаунт?") },
            text = { Text("Удаление необратимо. Будут удалены аккаунт, VPN-доступ и все подписки. Оплаченные периоды после удаления восстановить невозможно, история оплат не используется для возврата удалённой подписки. Повторная регистрация также не обнулит пробный период и не выдаст новый триал.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteAccount = false; onDeleteAccount() },
                    enabled = !accountActionLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccount = false }, enabled = !accountActionLoading) { Text("Отмена") } }
        )
    }

}

private enum class SettingsPage { SUBSCRIPTION, DEVICES, APP, DOCUMENTS }

@Composable
private fun SettingsNavButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
            Text(label)
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsInfoBox(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

@Composable
private fun SettingsWideButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

@Composable
private fun GlobeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.055f)
        val w = size.width
        val h = size.height
        val inset = size.minDimension * 0.05f
        val outer = Rect(inset, inset, w - inset, h - inset)

        drawOval(color = color, topLeft = outer.topLeft, size = outer.size, style = stroke)
        drawLine(color, Offset(w / 2f, inset), Offset(w / 2f, h - inset), stroke.width)
        drawLine(color, Offset(inset, h / 2f), Offset(w - inset, h / 2f), stroke.width)

        val narrow = Rect(w * 0.31f, inset, w * 0.69f, h - inset)
        drawOval(color = color, topLeft = narrow.topLeft, size = narrow.size, style = stroke)

        val upper = Rect(inset, h * 0.20f, w - inset, h * 0.56f)
        val lower = Rect(inset, h * 0.44f, w - inset, h * 0.80f)
        drawArc(color, startAngle = 18f, sweepAngle = 144f, useCenter = false, topLeft = upper.topLeft, size = upper.size, style = stroke)
        drawArc(color, startAngle = 198f, sweepAngle = 144f, useCenter = false, topLeft = lower.topLeft, size = lower.size, style = stroke)
    }
}

@Composable
private fun SubscriptionManagementScreen(
    subscription: SubscriptionInfo,
    refreshing: Boolean,
    trialLoading: Boolean,
    tariffs: List<TariffOption>,
    deviceProduct: DeviceProduct?,
    devices: List<DeviceItem>,
    loading: Boolean,
    errorText: String?,
    paymentLoadingProduct: String?,
    paymentChecking: Boolean,
    paymentMessage: String?,
    hasPendingPayment: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTrialClick: () -> Unit,
    onPay: (String) -> Unit,
    onCheckPayment: () -> Unit,
    onDismissPendingPayment: () -> Unit,
    onDeleteDevice: (Int) -> Unit
) {
    var deviceToDelete by remember { mutableStateOf<DeviceItem?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Text("‹", fontSize = 36.sp, color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Управление подпиской",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            if (!errorText.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            TrueWebCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Подписка", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            subscription.trialPending -> "Пробный доступ готов"
                            subscription.active -> "Активна"
                            else -> "Не активна"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (subscription.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            subscription.trialPending -> subscription.expiresLabel
                            subscription.unlimited -> "Без ограничения по сроку"
                            subscription.active -> "Действует до ${subscription.expiresLabel}"
                            else -> subscription.expiresLabel
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (subscription.active && !subscription.unlimited) {
                        Spacer(Modifier.height(4.dp))
                        Text("Осталось ${subscription.daysLeft} дн.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrueWebCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Трафик", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(subscription.trafficUsed, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                TrueWebCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Устройства", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(subscription.devicesLabel, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (subscription.trialAvailable) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onTrialClick,
                    enabled = !trialLoading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (trialLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Получить 3 дня бесплатно", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Продление и оплата", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (subscription.unlimited) {
                TrueWebCard(Modifier.fillMaxWidth()) {
                    Text(
                        "У вас безлимитная подписка — продление не требуется.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (tariffs.isEmpty() && !loading) {
                TrueWebCard(Modifier.fillMaxWidth()) {
                    Text("Тарифы пока не загружены", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                tariffs.forEach { tariff ->
                    Spacer(Modifier.height(8.dp))
                    TariffButton(
                        tariff = tariff,
                        loading = paymentLoadingProduct == tariff.code,
                        enabled = paymentLoadingProduct == null && !paymentChecking,
                        onClick = { onPay(tariff.code) }
                    )
                }
            }

            if (!paymentMessage.isNullOrBlank() || hasPendingPayment) {
                Spacer(Modifier.height(12.dp))
                TrueWebCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(paymentMessage ?: "Есть незавершённый платёж", color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onCheckPayment,
                            enabled = !paymentChecking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (paymentChecking) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Проверяем…")
                            } else Text("Проверить оплату")
                        }
                        TextButton(
                            onClick = onDismissPendingPayment,
                            enabled = !paymentChecking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Скрыть платёж", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Устройства", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(subscription.devicesLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            if (devices.isEmpty()) {
                TrueWebCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (loading) "Загружаем устройства…" else "Зарегистрированных устройств нет",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                devices.forEach { device ->
                    Spacer(Modifier.height(8.dp))
                    DeviceRow(device = device, enabled = !loading, onDelete = { deviceToDelete = device })
                }
            }

            if (deviceProduct != null && subscription.devicesLimit > 0) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onPay(deviceProduct.code) },
                    enabled = paymentLoadingProduct == null && !paymentChecking,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (paymentLoadingProduct == deviceProduct.code) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Создаём платёж…")
                    } else {
                        Text("+ Доп. устройство — ${deviceProduct.price} ₽", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onRefresh,
                enabled = !refreshing && !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (refreshing || loading) "Обновляем…" else "Обновить данные") }

            Spacer(Modifier.height(24.dp))
        }
    }

    deviceToDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            title = { Text("Удалить устройство?") },
            text = {
                Text(
                    if (device.isCurrent) "Это текущее устройство. После удаления оно может зарегистрироваться снова при обновлении подключения."
                    else "${device.title}\n\nПосле удаления освободится один слот."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deviceToDelete = null
                    onDeleteDevice(device.id)
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deviceToDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun TariffButton(tariff: TariffOption, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Создаём платёж…")
        } else {
            Text(tariff.title, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, fontWeight = FontWeight.SemiBold)
            if (tariff.badge.isNotBlank()) {
                Text("${tariff.badge}  ", style = MaterialTheme.typography.labelSmall)
            }
            Text("${tariff.price} ₽", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceItem, enabled: Boolean, onDelete: () -> Unit) {
    TrueWebCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    if (device.isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Text("это устройство", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (device.details.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(device.details, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                if (device.lastSeenMs > 0L) {
                    Spacer(Modifier.height(2.dp))
                    Text("Был онлайн: ${formatDeviceTime(device.lastSeenMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(onClick = onDelete, enabled = enabled) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatDeviceTime(value: Long): String {
    val ms = if (value < 10_000_000_000L) value * 1000L else value
    return runCatching { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ms)) }.getOrDefault("—")
}

@Composable
private fun ThemeChoice(
    label: String,
    mode: TrueWebThemeMode,
    selected: TrueWebThemeMode,
    onChange: (TrueWebThemeMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(mode) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = mode == selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SubscriptionSummaryCard(info: SubscriptionInfo, onManage: () -> Unit) {
    TrueWebCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Подписка", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(
                        when {
                            info.trialPending -> "Пробный доступ готов"
                            info.active -> "Активна"
                            else -> "Не активна"
                        },
                        fontWeight = FontWeight.Bold,
                        color = if (info.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.weight(1f))
                if (info.active && !info.unlimited) Text("${info.daysLeft} дн.", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    info.trialPending -> info.expiresLabel
                    info.unlimited -> "Без ограничения по сроку"
                    info.active -> "до ${info.expiresLabel}"
                    else -> info.expiresLabel
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth()) {
                Metric("Трафик", info.trafficUsed, Modifier.weight(1f))
                Metric("Устройства", info.devicesLabel, Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onManage,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Управление подпиской", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TrueWebCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content
    )
}
