package ru.trueweb.vpn

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ru.trueweb.vpn.api.TrueWebApi
import ru.trueweb.vpn.auth.AuthBackend
import ru.trueweb.vpn.auth.SessionStore
import ru.trueweb.vpn.model.*
import ru.trueweb.vpn.store.DeviceIdentity
import ru.trueweb.vpn.store.GeoDataManager
import ru.trueweb.vpn.store.LegalConsentStore
import ru.trueweb.vpn.store.PendingPaymentStore
import ru.trueweb.vpn.store.RoutingStore
import ru.trueweb.vpn.store.ServerStore
import ru.trueweb.vpn.store.ThemeStore
import ru.trueweb.vpn.ui.*
import ru.trueweb.vpn.vpn.TrueWebVpnService
import ru.trueweb.vpn.work.GeoDataRefreshWorker
import ru.trueweb.vpn.work.SubscriptionRefreshWorker

class MainActivity : ComponentActivity() {
    companion object {
        private const val GENERIC_APP_ERROR = "Что-то пошло не так. Отчёт об ошибке уже отправлен разработчику. Попробуйте немного позже."
    }

    private lateinit var sessionStore: SessionStore
    private lateinit var serverStore: ServerStore
    private lateinit var deviceIdentity: DeviceIdentity
    private lateinit var themeStore: ThemeStore
    private lateinit var pendingPaymentStore: PendingPaymentStore
    private lateinit var routingStore: RoutingStore
    private lateinit var legalConsentStore: LegalConsentStore

    private var authenticated by mutableStateOf(false)
    private var privacyAccepted by mutableStateOf(false)
    private var showPrivacyPolicyBeforeConsent by mutableStateOf(false)
    private var authInProgress by mutableStateOf(false)
    private var authError by mutableStateOf<String?>(null)
    private var emailCodeSentTo by mutableStateOf<String?>(null)

    private var subscription by mutableStateOf<SubscriptionInfo?>(null)
    private var servers by mutableStateOf<List<VpnServer>>(emptyList())
    private var pendingVpnMode by mutableStateOf(VpnMode.NORMAL)
    private var themeMode by mutableStateOf(TrueWebThemeMode.DARK)

    private var profileLoading by mutableStateOf(false)
    private var profileError by mutableStateOf<String?>(null)
    private var trialLoading by mutableStateOf(false)

    private var tariffs by mutableStateOf<List<TariffOption>>(emptyList())
    private var deviceProduct by mutableStateOf<DeviceProduct?>(null)
    private var devices by mutableStateOf<List<DeviceItem>>(emptyList())
    private var managementLoading by mutableStateOf(false)
    private var managementError by mutableStateOf<String?>(null)
    private var paymentLoadingProduct by mutableStateOf<String?>(null)
    private var paymentChecking by mutableStateOf(false)
    private var paymentMessage by mutableStateOf<String?>(null)
    private var accountActionLoading by mutableStateOf(false)
    private var accountActionMessage by mutableStateOf<String?>(null)
    private var batteryOptimizationRestricted by mutableStateOf(false)
    private var batteryNoticeDismissed by mutableStateOf(false)
    private var geoDataLastUpdatedMs by mutableLongStateOf(0L)
    private var geoDataRefreshing by mutableStateOf(false)

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                prepareVpnDataAndStart()
            } else {
                Toast.makeText(this, "Без разрешения Android VPN подключение невозможно", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStore = SessionStore(this)
        serverStore = ServerStore(this)
        deviceIdentity = DeviceIdentity(this)
        themeStore = ThemeStore(this)
        pendingPaymentStore = PendingPaymentStore(this)
        routingStore = RoutingStore(this)
        legalConsentStore = LegalConsentStore(this)
        batteryNoticeDismissed = getSharedPreferences("trueweb_ui", MODE_PRIVATE)
            .getBoolean("battery_notice_dismissed", false)
        refreshBatteryOptimizationState()
        geoDataLastUpdatedMs = GeoDataManager.lastSuccessMs(this)
        GeoDataRefreshWorker.schedule(this)
        GeoDataRefreshWorker.refreshIfStale(this)

        authenticated = sessionStore.isAuthenticated
        privacyAccepted = legalConsentStore.privacyAccepted
        servers = serverStore.loadServers()
        themeMode = themeStore.mode

        handleIntent(intent)

        setContent {
            val vpnState by TrueWebVpnService.state.collectAsState()
            val vpnError by TrueWebVpnService.lastError.collectAsState()
            val vpnMode by TrueWebVpnService.mode.collectAsState()
            TrueWebTheme(themeMode) {
                SideEffect {
                    val lightBars = themeMode == TrueWebThemeMode.LIGHT
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = lightBars
                    controller.isAppearanceLightNavigationBars = lightBars
                    @Suppress("DEPRECATION")
                    run {
                        window.statusBarColor = if (lightBars) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                        window.navigationBarColor = if (lightBars) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                    }
                }

                if (!privacyAccepted) {
                    if (showPrivacyPolicyBeforeConsent) {
                        LegalDocumentScreen(
                            title = LegalDocuments.PRIVACY_TITLE,
                            text = LegalDocuments.PRIVACY,
                            onBack = { showPrivacyPolicyBeforeConsent = false }
                        )
                    } else {
                        PrivacyConsentScreen(
                            onAccept = {
                                legalConsentStore.privacyAccepted = true
                                privacyAccepted = true
                            },
                            onDecline = { finish() },
                            onOpenPolicy = { showPrivacyPolicyBeforeConsent = true }
                        )
                    }
                } else if (!authenticated) {
                    AuthScreen(
                        authInProgress = authInProgress,
                        errorText = authError,
                        themeMode = themeMode,
                        emailCodeSentTo = emailCodeSentTo,
                        onProxyClick = { openExternal(AppConfig.TELEGRAM_PROXY_URL) },
                        onTelegramLoginClick = {
                            authError = null
                            sessionStore.telegramLinkPending = false
                            openExternal(AppConfig.TELEGRAM_AUTH_START)
                        },
                        onEmailStart = { startEmailAuth(it) },
                        onEmailVerify = { email, code -> verifyEmailAuth(email, code) },
                        onEmailReset = {
                            emailCodeSentTo = null
                            authError = null
                        },
                        onPasswordLogin = { login, password -> passwordLogin(login, password) }
                    )
                } else {
                    val info = subscription
                    if (info == null) {
                        LoadingScreen(errorText = profileError, onRetry = { loadData(forceServers = true) })
                    } else {
                        HomeScreen(
                            subscription = info,
                            refreshing = profileLoading,
                            trialLoading = trialLoading,
                            errorText = profileError,
                            vpnState = vpnState,
                            vpnError = vpnError,
                            vpnMode = vpnMode,
                            themeMode = themeMode,
                            tariffs = tariffs,
                            deviceProduct = deviceProduct,
                            devices = devices,
                            managementLoading = managementLoading,
                            managementError = managementError,
                            paymentLoadingProduct = paymentLoadingProduct,
                            paymentChecking = paymentChecking,
                            paymentMessage = paymentMessage,
                            hasPendingPayment = pendingPaymentStore.paymentId != null,
                            accountActionLoading = accountActionLoading,
                            accountActionMessage = accountActionMessage,
                            batteryOptimizationRestricted = batteryOptimizationRestricted,
                            showBatteryOptimizationNotice = batteryOptimizationRestricted && !batteryNoticeDismissed,
                            geoDataLastUpdatedMs = geoDataLastUpdatedMs,
                            geoDataRefreshing = geoDataRefreshing,
                            onBatterySettings = { openBatteryOptimizationSettings() },
                            onDismissBatteryNotice = { dismissBatteryNotice() },
                            onGeoDataRefresh = { refreshGeoDataNow() },
                            onConnectClick = { toggleNormalVpn(info, vpnState) },
                            onTrialClick = { activateTrial() },
                            onRefresh = { loadData(forceServers = true) },
                            onManagementRefresh = { loadManagementData() },
                            onPay = { startPayment(it) },
                            onCheckPayment = { checkPendingPayment(poll = true) },
                            onDismissPendingPayment = { dismissPendingPayment() },
                            onDeleteDevice = { deleteDevice(it) },
                            onThemeChanged = { setTheme(it) },
                            showTelegramLink = sessionStore.needsTelegramLink,
                            onLinkTelegram = { beginTelegramLink() },
                            onSetPasswordCredentials = { login, password -> setPasswordCredentials(login, password) },
                            onTelegramGroup = { openExternal(AppConfig.TELEGRAM_GROUP_URL) },
                            onSupport = { openExternal(AppConfig.SUPPORT_URL) },
                            onDeleteAccount = { deleteAccount() },
                            onLogout = { logout() }
                        )
                    }
                }

            }
        }


        if (authenticated) {
            SubscriptionRefreshWorker.schedule(this)
            loadData(forceServers = serverStore.isStale() || !serverStore.hasRequiredNormalServers())
            if (pendingPaymentStore.paymentId != null) checkPendingPayment(poll = false)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBatteryOptimizationState()
        geoDataLastUpdatedMs = GeoDataManager.lastSuccessMs(this)
        GeoDataRefreshWorker.refreshIfStale(this)
        if (this::sessionStore.isInitialized && authenticated && !profileLoading) {
            servers = serverStore.loadServers()
            loadData(forceServers = serverStore.isStale() || !serverStore.hasRequiredNormalServers())
            if (serverStore.desiredRunning) TrueWebVpnService.foregroundCheck(this)
            if (this::pendingPaymentStore.isInitialized && pendingPaymentStore.paymentId != null && !paymentChecking) {
                checkPendingPayment(poll = false)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return

        val isPaymentReturn = uri.scheme == "trueweb" && uri.host == "payment" && uri.path == "/return"
        if (isPaymentReturn) {
            if (authenticated && this::pendingPaymentStore.isInitialized) {
                paymentMessage = "Проверяем оплату…"
                checkPendingPayment(poll = true)
            }
            return
        }

        val isVerifiedAppLink = uri.scheme == "https" && uri.host == "trueweb24.ru" && uri.path == "/mobile-auth/callback"
        val isAppScheme = uri.scheme == "trueweb" && uri.host == "auth" && uri.path == "/callback"
        if (!isVerifiedAppLink && !isAppScheme) return

        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            authError = error
            authInProgress = false
            return
        }

        val code = uri.getQueryParameter("code") ?: return
        authInProgress = true
        authError = null
        val linkingTelegram = authenticated && sessionStore.needsTelegramLink && sessionStore.telegramLinkPending
        Thread {
            val result = if (linkingTelegram) {
                val current = sessionStore.accessToken
                    ?: return@Thread runOnUiThread {
                        authInProgress = false
                        authError = "Сессия не найдена"
                    }
                TrueWebApi.linkTelegram(current, code)
            } else {
                AuthBackend.exchangeCode(code)
            }
            runOnUiThread {
                authInProgress = false
                result.onSuccess { accessToken ->
                    sessionStore.accessToken = accessToken
                    sessionStore.authMethod = "telegram"
                    sessionStore.needsTelegramLink = false
                    sessionStore.telegramLinkPending = false
                    emailCodeSentTo = null
                    authenticated = true
                    subscription = null
                    if (linkingTelegram) {
                        Toast.makeText(this, "Telegram привязан к аккаунту TrueWeb", Toast.LENGTH_LONG).show()
                    }
                    SubscriptionRefreshWorker.schedule(this)
                    val restartAfterLink = linkingTelegram && serverStore.desiredRunning
                    loadData(forceServers = true) {
                        if (restartAfterLink && authenticated) {
                            TrueWebVpnService.stop(this)
                            window.decorView.postDelayed({
                                TrueWebVpnService.startNormal(this)
                            }, 1200L)
                        }
                    }
                }.onFailure {
                    if (linkingTelegram) sessionStore.telegramLinkPending = false
                    authError = if (linkingTelegram) {
                        "Не удалось привязать Telegram: ${cleanError(it)}"
                    } else {
                        "Не удалось завершить вход: ${cleanError(it)}"
                    }
                    if (linkingTelegram) {
                        Toast.makeText(this, authError, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    private fun startEmailAuth(rawEmail: String) {
        if (authInProgress) return
        val email = rawEmail.trim().lowercase()
        if (!email.contains("@") || email.length < 5) {
            authError = "Введите корректный email"
            return
        }
        authInProgress = true
        authError = null
        Thread {
            val result = TrueWebApi.startEmailAuth(email)
            runOnUiThread {
                authInProgress = false
                result.onSuccess {
                    emailCodeSentTo = it.email
                    Toast.makeText(this, "Код отправлен на ${it.email}", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    authError = "Не удалось отправить код: ${cleanError(it)}"
                }
            }
        }.start()
    }

    private fun verifyEmailAuth(email: String, code: String) {
        if (authInProgress) return
        if (code.length != 6) {
            authError = "Введите 6-значный код"
            return
        }
        authInProgress = true
        authError = null
        Thread {
            val result = TrueWebApi.verifyEmailAuth(email, code)
            runOnUiThread {
                authInProgress = false
                result.onSuccess { auth ->
                    sessionStore.accessToken = auth.accessToken
                    sessionStore.authMethod = "email"
                    sessionStore.needsTelegramLink = auth.needsTelegramLink
                    sessionStore.telegramLinkPending = false
                    emailCodeSentTo = null
                    authenticated = true
                    subscription = null
                    SubscriptionRefreshWorker.schedule(this)
                    if (auth.isNew && auth.trialActivated) {
                        Toast.makeText(this, "Готово — пробный доступ уже активирован", Toast.LENGTH_LONG).show()
                    }
                    loadData(forceServers = true)
                }.onFailure {
                    authError = "Не удалось войти: ${cleanError(it)}"
                }
            }
        }.start()
    }

    private fun passwordLogin(login: String, password: String) {
        if (authInProgress) return
        if (login.trim().length < 3 || password.length < 8) {
            authError = "Проверьте логин и пароль"
            return
        }
        authInProgress = true
        authError = null
        Thread {
            val result = TrueWebApi.passwordLogin(login, password)
            runOnUiThread {
                authInProgress = false
                result.onSuccess { auth ->
                    sessionStore.accessToken = auth.accessToken
                    sessionStore.authMethod = "password"
                    sessionStore.needsTelegramLink = auth.needsTelegramLink
                    sessionStore.telegramLinkPending = false
                    authenticated = true
                    subscription = null
                    SubscriptionRefreshWorker.schedule(this)
                    loadData(forceServers = true)
                }.onFailure {
                    authError = "Неверный логин или пароль"
                }
            }
        }.start()
    }

    private fun beginTelegramLink() {
        if (!authenticated || !sessionStore.needsTelegramLink) return
        sessionStore.telegramLinkPending = true
        authError = null
        openExternal(AppConfig.TELEGRAM_AUTH_START)
    }

    private fun loadData(forceServers: Boolean, onComplete: (() -> Unit)? = null) {
        if (profileLoading) return
        val token = sessionStore.accessToken ?: return
        profileLoading = true
        profileError = null

        Thread {
            if (routingStore.isStale()) {
                TrueWebApi.routing(token)
                    .onSuccess { routingStore.save(it) }
                    .onFailure { reportUnexpectedAppError("routing_refresh", it) }
            }

            var serverError: Throwable? = null
            var catalogUpdated = false
            if (forceServers || serverStore.isStale() || !serverStore.hasRequiredNormalServers()) {
                TrueWebApi.servers(token, deviceIdentity)
                    .onSuccess {
                        serverStore.saveCatalog(it)
                        catalogUpdated = true
                    }
                    .onFailure {
                        serverError = it
                    }
            }

            val profileResult = TrueWebApi.me(token)
            runOnUiThread {
                profileLoading = false
                if (catalogUpdated) {
                    servers = serverStore.loadServers()
                            }

                var completed = false
                profileResult.onSuccess {
                    subscription = it
                    profileError = serverError?.let {
                        if (shouldReportUnexpectedError(it)) {
                            reportUnexpectedAppError("servers_refresh", it)
                            GENERIC_APP_ERROR
                        } else {
                            serverErrorText(it)
                        }
                    }
                    completed = !serverError?.message.orEmpty().contains("HTTP 401")
                }.onFailure {
                    if ((it.message ?: "").contains("HTTP 401")) {
                        expireSession()
                    } else {
                        profileError = unexpectedOrExpectedMessage("profile_refresh", it, "Не удалось обновить данные")
                    }
                }

                if (serverError?.message.orEmpty().contains("HTTP 401")) {
                    expireSession()
                    completed = false
                }
                if (completed && authenticated) onComplete?.invoke()
            }
        }.start()
    }

    private fun loadManagementData() {
        if (managementLoading) return
        val token = sessionStore.accessToken ?: return
        managementLoading = true
        managementError = null

        Thread {
            val billing = TrueWebApi.billingCatalog(token)
            val deviceResult = TrueWebApi.devices(token, deviceIdentity)
            runOnUiThread {
                managementLoading = false
                billing.onSuccess {
                    tariffs = it.tariffs
                    deviceProduct = it.deviceProduct
                }.onFailure {
                    managementError = unexpectedOrExpectedMessage("billing_catalog", it, "Не удалось загрузить тарифы")
                }
                deviceResult.onSuccess {
                    devices = it.devices
                }.onFailure {
                    val prefix = if (managementError.isNullOrBlank()) "" else "${managementError}\n"
                    managementError = prefix + "Не удалось загрузить устройства: ${cleanError(it)}"
                }
            }
        }.start()
    }

    private fun activateTrial() {
        if (trialLoading) return
        val token = sessionStore.accessToken ?: return
        trialLoading = true
        profileError = null
        Thread {
            val result = TrueWebApi.activateTrial(token)
            runOnUiThread {
                trialLoading = false
                result.onSuccess {
                    subscription = it
                    Toast.makeText(this, "Пробный доступ активирован на 3 дня", Toast.LENGTH_LONG).show()
                    loadData(forceServers = true)
                    loadManagementData()
                }.onFailure {
                    profileError = unexpectedOrExpectedMessage("trial_activate", it, "Не удалось активировать пробный доступ")
                }
            }
        }.start()
    }

    private fun startPayment(product: String) {
        if (paymentLoadingProduct != null) return
        val token = sessionStore.accessToken ?: return
        paymentLoadingProduct = product
        paymentMessage = null
        managementError = null

        Thread {
            val result = TrueWebApi.createPayment(token, product)
            runOnUiThread {
                paymentLoadingProduct = null
                result.onSuccess { payment ->
                    pendingPaymentStore.paymentId = payment.id
                    paymentMessage = "Ожидаем оплату ${payment.title}"
                    openExternal(payment.confirmationUrl)
                }.onFailure {
                    managementError = unexpectedOrExpectedMessage("payment_create", it, "Не удалось создать платёж")
                }
            }
        }.start()
    }

    private fun checkPendingPayment(poll: Boolean) {
        if (paymentChecking) return
        val token = sessionStore.accessToken ?: return
        val paymentId = pendingPaymentStore.paymentId ?: return
        paymentChecking = true

        Thread {
            var status: PaymentStatus? = null
            var lastError: Throwable? = null
            val attempts = if (poll) 10 else 1
            for (index in 0 until attempts) {
                val result = TrueWebApi.paymentStatus(token, paymentId)
                result.onSuccess { status = it }.onFailure { lastError = it }
                val current = status
                if (current != null && (current.processed || current.status == "canceled")) break
                if (index < attempts - 1) Thread.sleep(1500)
            }

            runOnUiThread {
                paymentChecking = false
                when {
                    status?.processed == true -> {
                        pendingPaymentStore.clear()
                        paymentMessage = null
                        Toast.makeText(this, "Оплата прошла успешно. Подписка обновлена.", Toast.LENGTH_LONG).show()
                        loadData(forceServers = true)
                        loadManagementData()
                    }
                    status?.status == "canceled" -> {
                        pendingPaymentStore.clear()
                        paymentMessage = null
                        Toast.makeText(this, "Платёж отменён", Toast.LENGTH_SHORT).show()
                    }
                    status?.status == "succeeded" -> {
                        paymentMessage = "Оплата получена, завершаем активацию…"
                        loadData(forceServers = true)
                    }
                    lastError != null -> {
                        managementError = unexpectedOrExpectedMessage("payment_status", lastError!!, "Не удалось проверить оплату")
                    }
                    else -> paymentMessage = "Платёж пока не завершён"
                }
            }
        }.start()
    }

    private fun dismissPendingPayment() {
        pendingPaymentStore.clear()
        paymentMessage = null
        managementError = null
        Toast.makeText(
            this,
            "Платёж скрыт. Это не отменяет его в ЮKassa.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun deleteDevice(deviceId: Int) {
        if (managementLoading) return
        val token = sessionStore.accessToken ?: return
        managementLoading = true
        managementError = null
        Thread {
            val result = TrueWebApi.deleteDevice(token, deviceId, deviceIdentity)
            runOnUiThread {
                managementLoading = false
                result.onSuccess {
                    Toast.makeText(this, "Устройство удалено", Toast.LENGTH_SHORT).show()
                    loadManagementData()
                    loadData(forceServers = true)
                }.onFailure {
                    managementError = unexpectedOrExpectedMessage("device_delete", it, "Не удалось удалить устройство")
                }
            }
        }.start()
    }

    private fun toggleNormalVpn(
        info: SubscriptionInfo,
        state: TrueWebVpnService.TunnelState
    ) {
        if (state == TrueWebVpnService.TunnelState.RUNNING || state == TrueWebVpnService.TunnelState.CONNECTING) {
            TrueWebVpnService.stop(this)
            return
        }
        if (!info.active) {
            Toast.makeText(this, "Сначала активируйте пробный период или подписку", Toast.LENGTH_SHORT).show()
            return
        }
        prepareAndStartVpn()
    }

    private fun prepareAndStartVpn() {
        pendingVpnMode = VpnMode.NORMAL
        serverStore.desiredMode = VpnMode.NORMAL

        // Ask Android for the VPN slot before doing profile/network work. If Happ,
        // Incy or another VPN owns the slot, this is where Android transfers it to
        // TrueWeb after the user's confirmation instead of leaving TrueWeb behind it.
        if (hasAnotherVpnNetwork()) {
            Toast.makeText(this, "Переключаем VPN на TrueWeb…", Toast.LENGTH_SHORT).show()
        }
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) vpnPermissionLauncher.launch(prepareIntent)
        else prepareVpnDataAndStart()
    }

    private fun prepareVpnDataAndStart() {
        val token = sessionStore.accessToken ?: return
        Thread {
            var error: Throwable? = null
            if (routingStore.isStale()) {
                TrueWebApi.routing(token)
                    .onSuccess { routingStore.save(it) }
                    .onFailure { reportUnexpectedAppError("routing_preconnect", it) }
            }
            if (serverStore.isStale() || !serverStore.hasRequiredNormalServers()) {
                TrueWebApi.servers(token, deviceIdentity)
                    .onSuccess { serverStore.saveCatalog(it) }
                    .onFailure { error = it }
            }
            runOnUiThread {
                servers = serverStore.loadServers()
                if (error != null) {
                    val e = error!!
                    profileError = if (shouldReportUnexpectedError(e)) {
                        reportUnexpectedAppError("servers_preconnect", e)
                        GENERIC_APP_ERROR
                    } else {
                        serverErrorText(e)
                    }
                    Toast.makeText(this, profileError, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                if (!serverStore.hasRequiredNormalServers()) {
                    profileError = "Не получены основные параметры подключения TrueWeb"
                    Toast.makeText(this, profileError, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                TrueWebVpnService.startNormal(this)
            }
        }.start()
    }

    private fun hasAnotherVpnNetwork(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val trueWebActive = TrueWebVpnService.state.value == TrueWebVpnService.TunnelState.RUNNING ||
            TrueWebVpnService.state.value == TrueWebVpnService.TunnelState.CONNECTING
        if (trueWebActive) return false
        return manager.allNetworks.any { network ->
            val caps = runCatching { manager.getNetworkCapabilities(network) }.getOrNull() ?: return@any false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    private fun setTheme(mode: TrueWebThemeMode) {
        themeStore.mode = mode
        themeMode = mode
    }

    private fun setPasswordCredentials(login: String, password: String) {
        val token = sessionStore.accessToken ?: return
        if (accountActionLoading) return
        accountActionLoading = true
        accountActionMessage = null
        Thread {
            val result = TrueWebApi.setPasswordCredentials(token, login, password)
            runOnUiThread {
                accountActionLoading = false
                result.onSuccess {
                    accountActionMessage = "Логин и пароль сохранены"
                    Toast.makeText(this, "Логин и пароль сохранены", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    accountActionMessage = unexpectedOrExpectedMessage("password_setup", it, "Не удалось сохранить данные для входа")
                    Toast.makeText(this, accountActionMessage, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun deleteAccount() {
        val token = sessionStore.accessToken ?: return
        if (accountActionLoading) return
        accountActionLoading = true
        accountActionMessage = "Удаляем аккаунт…"
        TrueWebVpnService.stop(this)
        Thread {
            val result = TrueWebApi.deleteAccount(token, deviceIdentity)
            runOnUiThread {
                accountActionLoading = false
                result.onSuccess {
                    clearLocalAccount()
                    Toast.makeText(this, "Аккаунт удалён", Toast.LENGTH_LONG).show()
                }.onFailure {
                    accountActionMessage = unexpectedOrExpectedMessage("account_delete", it, "Не удалось удалить аккаунт")
                    Toast.makeText(this, accountActionMessage, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun clearLocalAccount() {
        SubscriptionRefreshWorker.cancel(this)
        sessionStore.clear()
        serverStore.clearRuntime()
        pendingPaymentStore.clear()
        subscription = null
        servers = emptyList()
        pendingVpnMode = VpnMode.NORMAL
        profileError = null
        managementError = null
        accountActionMessage = null
        emailCodeSentTo = null
        authenticated = false
    }

    private fun logout() {
        val token = sessionStore.accessToken
        TrueWebVpnService.stop(this)
        clearLocalAccount()
        if (!token.isNullOrBlank()) Thread { TrueWebApi.logout(token) }.start()
    }

    private fun expireSession() {
        TrueWebVpnService.stop(this)
        SubscriptionRefreshWorker.cancel(this)
        sessionStore.clear()
        authenticated = false
        subscription = null
        authError = "Сессия истекла. Войдите снова."
    }

    private fun unexpectedOrExpectedMessage(stage: String, t: Throwable, prefix: String): String {
        if (!hasPhysicalNetwork()) return "Нет подключения к интернету. Проверьте Wi‑Fi или мобильную сеть."
        return if (shouldReportUnexpectedError(t)) {
            reportUnexpectedAppError(stage, t)
            GENERIC_APP_ERROR
        } else {
            "$prefix: ${cleanError(t)}"
        }
    }

    private fun reportUnexpectedAppError(stage: String, t: Throwable) {
        if (!shouldReportUnexpectedError(t)) return
        val token = sessionStore.accessToken ?: return
        val detail = safeDiagnosticDetail(t)
        Thread {
            TrueWebApi.reportError(
                accessToken = token,
                device = deviceIdentity,
                stage = stage,
                errorType = t.javaClass.simpleName.ifBlank { "Throwable" },
                detail = detail
            )
        }.start()
    }

    private fun shouldReportUnexpectedError(t: Throwable): Boolean {
        if (!hasPhysicalNetwork()) return false
        val raw = (t.message ?: "").lowercase()
        val status = Regex("HTTP\\s+(\\d{3})", RegexOption.IGNORE_CASE)
            .find(t.message ?: "")
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (status != null && status in setOf(400, 401, 403, 404, 409, 422, 429)) return false
        if (
            raw.contains("неверный логин") ||
            raw.contains("неверный пароль") ||
            raw.contains("неверный код") ||
            raw.contains("код ист") ||
            raw.contains("пробн") && raw.contains("уже") ||
            raw.contains("лимит устройств")
        ) return false
        return true
    }

    private fun hasPhysicalNetwork(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.allNetworks.any { network ->
            val caps = runCatching { manager.getNetworkCapabilities(network) }.getOrNull() ?: return@any false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
        }
    }

    private fun safeDiagnosticDetail(t: Throwable): String {
        var value = "${t.javaClass.simpleName}: ${t.message.orEmpty()}"
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
        value = value.replace(Regex("""(?i)(vless|vmess|trojan|ss)://[^\s]+"""), "<vpn-uri-redacted>")
        value = value.replace(Regex("""(?i)bearer\s+[A-Za-z0-9._~+\-/=]+"""), "Bearer <redacted>")
        value = value.replace(Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"""), "<uuid-redacted>")
        value = value.replace(Regex("""[A-Za-z0-9_-]{40,}"""), "<token-redacted>")
        value = value.replace(Regex("""https?://[^\s]*/sub/[^\s]+""", RegexOption.IGNORE_CASE), "<subscription-url-redacted>")
        return value.take(300).ifBlank { t.javaClass.simpleName }
    }

    private fun serverErrorText(t: Throwable): String {
        val msg = cleanError(t)
        return when {
            msg.contains("Лимит устройств", ignoreCase = true) -> msg
            (t.message ?: "").contains("HTTP 403") -> "Лимит устройств исчерпан. Откройте «Управление подпиской» и удалите старое устройство или добавьте слот."
            else -> "Не удалось обновить серверы: $msg"
        }
    }

    private fun refreshBatteryOptimizationState() {
        batteryOptimizationRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val power = getSystemService(PowerManager::class.java)
            power != null && !power.isIgnoringBatteryOptimizations(packageName)
        } else {
            false
        }
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val appUri = Uri.parse("package:$packageName")
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri)
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val opened = runCatching { startActivity(direct); true }.getOrDefault(false)
        if (!opened) runCatching { startActivity(fallback) }
    }

    private fun dismissBatteryNotice() {
        batteryNoticeDismissed = true
        getSharedPreferences("trueweb_ui", MODE_PRIVATE)
            .edit().putBoolean("battery_notice_dismissed", true).apply()
    }

    private fun refreshGeoDataNow() {
        if (geoDataRefreshing) return
        geoDataRefreshing = true
        Thread {
            val result = GeoDataManager.updateNow(this, force = true)
            runOnUiThread {
                geoDataRefreshing = false
                result.onSuccess {
                    geoDataLastUpdatedMs = it
                    Toast.makeText(this, "GeoData обновлена. Новые правила применятся при следующем подключении VPN.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, "Не удалось обновить GeoData. Оставлены текущие базы.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun openExternal(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show() }
    }

    private fun cleanError(t: Throwable): String {
        val raw = t.message ?: "неизвестная ошибка"
        return raw.substringAfter(": ", raw).take(220)
    }
}

@Composable
private fun LoadingScreen(errorText: String?, onRetry: () -> Unit) {
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
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (errorText.isNullOrBlank()) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(18.dp))
                    Text("Загружаем подписку…", color = MaterialTheme.colorScheme.onBackground)
                } else {
                    Text(errorText, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Повторить") }
                }
            }
        }
    }
}
