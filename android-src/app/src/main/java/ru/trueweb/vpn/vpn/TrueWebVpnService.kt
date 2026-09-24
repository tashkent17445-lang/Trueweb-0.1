package ru.trueweb.vpn.vpn

import ru.trueweb.vpn.i18n.L10n
import ru.trueweb.vpn.i18n.L10n.t

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.core.app.NotificationCompat
import go.Seq
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import ru.trueweb.vpn.MainActivity
import ru.trueweb.vpn.api.TrueWebApi
import ru.trueweb.vpn.auth.SessionStore
import ru.trueweb.vpn.R
import ru.trueweb.vpn.model.VpnMode
import ru.trueweb.vpn.model.VpnRole
import ru.trueweb.vpn.model.VpnServer
import ru.trueweb.vpn.store.DeviceIdentity
import ru.trueweb.vpn.store.GeoDataManager
import ru.trueweb.vpn.store.RoutingStore
import ru.trueweb.vpn.store.ServerStore
import ru.trueweb.vpn.work.GeoDataRefreshWorker
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TrueWeb 0.8.7 automatic connection policy:
 * - Try primary inbound 1, then backup inbound 18.
 * - WL is an internal emergency path and is selected automatically only when
 *   both ordinary entries are unreachable and WL itself is reachable.
 * - While WL is active, ordinary entries are re-checked roughly every 3 minutes.
 *   Return to normal only after 3 consecutive successful probes.
 * - If the active path dies, immediately re-evaluate primary -> backup -> WL.
 * - Physical network loss is not treated as a server failure; the desired VPN
 *   state is preserved and reconnection resumes when Wi-Fi/LTE returns.
 */
class TrueWebVpnService : VpnService() {
    enum class TunnelState { STOPPED, CONNECTING, RUNNING, ERROR }

    companion object {
        const val ACTION_START_NORMAL = "ru.trueweb.vpn.START_NORMAL"
        const val ACTION_START_WHITELIST = "ru.trueweb.vpn.START_WHITELIST"
        const val ACTION_STOP = "ru.trueweb.vpn.STOP"
        const val ACTION_FOREGROUND_CHECK = "ru.trueweb.vpn.FOREGROUND_CHECK"

        private const val CHANNEL_ID = "trueweb_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val TCP_PROBE_TIMEOUT_MS = 2500
        private const val NORMAL_HEALTH_SECONDS = 60L
        private const val WL_RECOVERY_MIN_SECONDS = 170L
        private const val WL_RECOVERY_MAX_SECONDS = 211L
        private const val STABLE_PROBE_COUNT = 3
        private const val STABLE_PROBE_GAP_MS = 4000L

        private val _state = MutableStateFlow(TunnelState.STOPPED)
        val state: StateFlow<TunnelState> = _state

        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError

        private val _mode = MutableStateFlow(VpnMode.NORMAL)
        val mode: StateFlow<VpnMode> = _mode

        private val _activeRole = MutableStateFlow(VpnRole.UNKNOWN)
        val activeRole: StateFlow<VpnRole> = _activeRole

        private val _whitelistSuggested = MutableStateFlow(false)
        val whitelistSuggested: StateFlow<Boolean> = _whitelistSuggested

        fun startNormal(context: Context) {
            context.startForegroundService(
                Intent(context, TrueWebVpnService::class.java).setAction(ACTION_START_NORMAL)
            )
        }

        fun startWhitelist(context: Context) {
            context.startForegroundService(
                Intent(context, TrueWebVpnService::class.java).setAction(ACTION_START_WHITELIST)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrueWebVpnService::class.java).setAction(ACTION_STOP))
        }

        fun foregroundCheck(context: Context) {
            context.startService(
                Intent(context, TrueWebVpnService::class.java).setAction(ACTION_FOREGROUND_CHECK)
            )
        }
    }

    private val starting = AtomicBoolean(false)
    private val store by lazy { ServerStore(applicationContext) }
    private val routingStore by lazy { RoutingStore(applicationContext) }
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val physicalNetworks = ConcurrentHashMap.newKeySet<Network>()

    private var tunFd: ParcelFileDescriptor? = null
    private var core: CoreController? = null
    private var normalHealthFuture: ScheduledFuture<*>? = null
    private var whitelistRecoveryFuture: ScheduledFuture<*>? = null

    @Volatile
    private var physicalNetwork: Network? = null

    private val reconnectRunnable = Runnable {
        if (store.desiredRunning) startTunnelAsync()
    }

    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(code: Long, message: String?): Long = 0
    }

    private val physicalNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            physicalNetworks.add(network)

            val previous = physicalNetwork
            val selected = selectPhysicalNetwork()
            physicalNetwork = selected
            selected?.let { runCatching { setUnderlyingNetworks(arrayOf(it)) } }

            if (store.desiredRunning && (previous != selected || _state.value != TunnelState.RUNNING)) {
                mainHandler.removeCallbacks(reconnectRunnable)
                mainHandler.postDelayed(reconnectRunnable, 1200L)
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (isPhysicalNetwork(capabilities)) {
                physicalNetworks.add(network)
            } else {
                physicalNetworks.remove(network)
            }

            val selected = selectPhysicalNetwork()
            if (selected != physicalNetwork) {
                physicalNetwork = selected
                selected?.let { runCatching { setUnderlyingNetworks(arrayOf(it)) } }
                if (store.desiredRunning) {
                    mainHandler.removeCallbacks(reconnectRunnable)
                    mainHandler.postDelayed(reconnectRunnable, 1200L)
                }
            }
        }

        override fun onLost(network: Network) {
            val wasUnderlying = physicalNetwork == network
            physicalNetworks.remove(network)

            if (!wasUnderlying) {
                return
            }

            physicalNetwork = selectPhysicalNetwork()
            if (!store.desiredRunning) return
            cancelHealthSchedules()
            Thread {
                stopCoreOnly()
                store.activeRole = VpnRole.UNKNOWN
                _activeRole.value = VpnRole.UNKNOWN
                _state.value = TunnelState.CONNECTING
                _lastError.value = null
                updateNotification(t("Ждём сеть…", "Waiting for network…"))
            }.start()

            physicalNetwork?.let {
                runCatching { setUnderlyingNetworks(arrayOf(it)) }
                mainHandler.removeCallbacks(reconnectRunnable)
                mainHandler.postDelayed(reconnectRunnable, 1200L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        L10n.initialize(this)
        createChannel()
        initializeCore()
        registerPhysicalNetworkCallback()
        _mode.value = store.desiredMode
        _activeRole.value = store.activeRole
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                store.desiredRunning = false
                store.activeRole = VpnRole.UNKNOWN
                cancelHealthSchedules()
                mainHandler.removeCallbacks(reconnectRunnable)
                stopTunnel(clearError = true)
                _mode.value = VpnMode.NORMAL
                _activeRole.value = VpnRole.UNKNOWN
                _whitelistSuggested.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START_NORMAL -> {
                store.desiredRunning = true
                store.desiredMode = VpnMode.NORMAL
                _mode.value = VpnMode.NORMAL
                _whitelistSuggested.value = false
                startForeground(NOTIFICATION_ID, notification(t("Подключение…", "Connecting…")))
                startTunnelAsync()
            }

            ACTION_START_WHITELIST -> {
                // Compatibility with older UI intents: 0.8.7 always uses automatic routing.
                store.desiredRunning = true
                store.desiredMode = VpnMode.NORMAL
                _mode.value = VpnMode.NORMAL
                _whitelistSuggested.value = false
                startForeground(NOTIFICATION_ID, notification(t("Подключение…", "Connecting…")))
                startTunnelAsync()
            }

            ACTION_FOREGROUND_CHECK -> {
                if (store.desiredRunning && store.desiredMode == VpnMode.NORMAL) {
                    Thread { normalHealthCheck(foreground = true) }.start()
                }
            }

            else -> {
                if (!store.desiredRunning) return START_NOT_STICKY
                _mode.value = store.desiredMode
                startForeground(NOTIFICATION_ID, notification(t("Восстанавливаем подключение…", "Restoring connection…")))
                startTunnelAsync()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cancelHealthSchedules()
        mainHandler.removeCallbacks(reconnectRunnable)
        runCatching { connectivity.unregisterNetworkCallback(physicalNetworkCallback) }
        scheduler.shutdownNow()
        stopTunnel(clearError = false)
        super.onDestroy()
    }

    override fun onRevoke() {
        store.desiredRunning = false
        store.activeRole = VpnRole.UNKNOWN
        cancelHealthSchedules()
        stopTunnel(clearError = false)
        _lastError.value = t("TrueWeb отключён другим VPN-приложением. Нажмите кнопку подключения, чтобы снова включить TrueWeb.", "TrueWeb was disconnected by another VPN app. Tap Connect to enable TrueWeb again.")
        updateNotification(t("Отключено другим VPN-приложением", "Disconnected by another VPN app"))
        stopSelf()
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (store.desiredRunning) updateNotification(t("TrueWeb работает в фоне", "TrueWeb is running in the background"))
        super.onTaskRemoved(rootIntent)
    }

    private fun registerPhysicalNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val active = connectivity.activeNetwork
        val activeCaps = active?.let(connectivity::getNetworkCapabilities)
        if (active != null && isPhysicalNetwork(activeCaps)) {
            physicalNetworks.add(active)
            physicalNetwork = active
        }

        runCatching { connectivity.registerNetworkCallback(request, physicalNetworkCallback) }
    }

    private fun isPhysicalNetwork(capabilities: NetworkCapabilities?): Boolean {
        return capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }

    private fun selectPhysicalNetwork(): Network? {
        val candidates = physicalNetworks.mapNotNull { network ->
            val caps = runCatching { connectivity.getNetworkCapabilities(network) }.getOrNull()
            if (caps != null && isPhysicalNetwork(caps)) network to caps else null
        }

        val stale = physicalNetworks.filter { network ->
            candidates.none { it.first == network }
        }
        if (stale.isNotEmpty()) physicalNetworks.removeAll(stale.toSet())

        return candidates.maxByOrNull { (_, caps) ->
            var score = 0
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) score += 100
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) score += 30
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) score += 20
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) score += 10
            score
        }?.first
    }

    @Synchronized
    private fun initializeCore() {
        if (core != null) return
        Seq.setContext(applicationContext)
        ensureGeoAssets()
        val base = filesDir.absolutePath
        val hwid = DeviceIdentity(this).hwid
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(hwid.toByteArray(Charsets.UTF_8))
        val xudpBaseKey = Base64.encodeToString(
            keyBytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        Libv2ray.initCoreEnv(base, xudpBaseKey)
        core = Libv2ray.newCoreController(callback)
    }

    private fun startTunnelAsync(target: VpnServer? = null) {
        if (!starting.compareAndSet(false, true)) return
        Thread {
            try {
                cancelHealthSchedules()
                _lastError.value = null
                _state.value = TunnelState.CONNECTING
                _mode.value = store.desiredMode
                updateNotification(t("Подключение…", "Connecting…"))

                val network = physicalNetwork ?: selectPhysicalNetwork().also { physicalNetwork = it }
                if (network == null) {
                    stopCoreOnly()
                    store.activeRole = VpnRole.UNKNOWN
                    _activeRole.value = VpnRole.UNKNOWN
                    _state.value = TunnelState.CONNECTING
                    updateNotification(t("Ждём сеть…", "Waiting for network…"))
                    return@Thread
                }
                runCatching { setUnderlyingNetworks(arrayOf(network)) }

                // No manual WL mode in 0.8.7. With no explicit target always
                // evaluate the full automatic chain: primary -> backup -> WL.
                val selected = target ?: chooseAutomaticServer()

                if (selected == null) {
                    markAllPathsUnavailable()
                    return@Thread
                }

                val selectedMode = if (selected.role == VpnRole.WHITELIST) VpnMode.WHITELIST else VpnMode.NORMAL
                store.desiredMode = selectedMode
                _mode.value = selectedMode

                initializeCore()
                stopCoreOnly()

                val routingPolicy = routingStore.load()
                val built = XrayConfigBuilder.build(
                    server = selected,
                    smartAuto = routingPolicy.smartAuto,
                    bypassRu = routingPolicy.bypassRu
                )
                tunFd = establishVpnInterface()

                val controller = core ?: error("Xray-core не инициализирован")
                controller.startLoop(built.json, tunFd!!.fd)
                if (!controller.isRunning) error("Xray-core не запустился")

                store.activeRole = selected.role
                _activeRole.value = selected.role
                _state.value = TunnelState.RUNNING
                _lastError.value = null
                _whitelistSuggested.value = false
                updateNotification(if (selectedMode == VpnMode.WHITELIST) t("VPN подключён · резервный режим", "VPN connected · fallback mode") else t("VPN подключён", "VPN connected"))
                GeoDataRefreshWorker.refreshIfStale(applicationContext)
                if (selectedMode == VpnMode.WHITELIST) scheduleWhitelistRecovery()
                else scheduleNormalHealth()
            } catch (e: Throwable) {
                reportUnexpectedError("vpn_start", e)
                _lastError.value = userErrorMessage()
                _state.value = TunnelState.ERROR
                updateNotification(t("Ошибка подключения. Отчёт отправлен разработчику", "Connection error. A report was sent to the developer"))
                stopCoreOnly()
                // Re-evaluate all paths soon. If the current WL path crashed this
                // gives ordinary entries an immediate chance before WL is retried.
                scheduleNormalHealth(8L)
            } finally {
                starting.set(false)
            }
        }.start()
    }

    private fun userErrorMessage(): String =
        t(
            "Что-то пошло не так. Отчёт об ошибке уже отправлен разработчику. Попробуйте подключиться немного позже.",
            "Something went wrong. An error report has been sent to the developer. Please try connecting again later."
        )

    private fun chooseNormalServer(): VpnServer? {
        val primary = store.primaryServer()
        if (primary != null && probeReachable(primary)) return primary
        val backup = store.backupServer()
        if (backup != null && probeReachable(backup)) return backup
        return null
    }

    private fun chooseWhitelistServer(): VpnServer? =
        store.whitelistServers().firstOrNull { probeReachable(it) }

    private fun chooseAutomaticServer(): VpnServer? =
        chooseNormalServer() ?: chooseWhitelistServer()

    private fun normalHealthCheck(foreground: Boolean = false) {
        if (!store.desiredRunning || store.desiredMode != VpnMode.NORMAL || starting.get()) return
        if ((physicalNetwork ?: selectPhysicalNetwork().also { physicalNetwork = it }) == null) return

        if (_state.value != TunnelState.RUNNING) {
            startTunnelAsync()
            return
        }

        val role = store.activeRole
        val active = store.serverForRole(role)
        if (active != null && probeReachable(active)) {
            _whitelistSuggested.value = false
            return
        }

        // One confirmation avoids switching on a single short radio hiccup.
        if (!foreground) {
            Thread.sleep(2500L)
            if (active != null && probeReachable(active)) {
                _whitelistSuggested.value = false
                return
            }
        }

        val alternate = when (role) {
            VpnRole.PRIMARY -> store.backupServer()
            VpnRole.BACKUP -> store.primaryServer()
            else -> store.primaryServer() ?: store.backupServer()
        }
        if (alternate != null && probeReachable(alternate)) {
            startTunnelAsync(alternate)
            return
        }

        // If the active role was unknown, make sure both ordinary entries were tried.
        if (role == VpnRole.UNKNOWN) {
            val secondary = store.backupServer()
            if (secondary != null && secondary.id != alternate?.id && probeReachable(secondary)) {
                startTunnelAsync(secondary)
                return
            }
        }

        val wl = chooseWhitelistServer()
        if (wl != null) {
            switchNormalToWhitelist(wl)
        } else {
            markAllPathsUnavailable()
        }
    }

    private fun switchNormalToWhitelist(server: VpnServer) {
        store.desiredMode = VpnMode.WHITELIST
        _mode.value = VpnMode.WHITELIST
        _whitelistSuggested.value = false
        startTunnelAsync(server)
    }

    private fun markAllPathsUnavailable() {
        stopCoreOnly()
        store.activeRole = VpnRole.UNKNOWN
        _activeRole.value = VpnRole.UNKNOWN
        _lastError.value = userErrorMessage()
        _state.value = TunnelState.ERROR
        _whitelistSuggested.value = false
        updateNotification(t("Не удалось подключиться. Повторяем попытку…", "Could not connect. Retrying…"))
        scheduleNormalHealth(15L)
    }

    private fun scheduleNormalHealth(delaySeconds: Long = NORMAL_HEALTH_SECONDS) {
        normalHealthFuture?.cancel(false)
        whitelistRecoveryFuture?.cancel(false)
        if (!store.desiredRunning) return
        normalHealthFuture = scheduler.schedule({
            try {
                if (_state.value == TunnelState.RUNNING && store.desiredMode == VpnMode.NORMAL) {
                    normalHealthCheck(foreground = false)
                } else if (_state.value != TunnelState.RUNNING) {
                    startTunnelAsync()
                }
            } catch (e: Throwable) {
                reportUnexpectedError("health_check", e)
            } finally {
                if (store.desiredRunning && !(store.desiredMode == VpnMode.WHITELIST && _state.value == TunnelState.RUNNING)) {
                    scheduleNormalHealth()
                }
            }
        }, delaySeconds, TimeUnit.SECONDS)
    }

    private fun scheduleWhitelistRecovery() {
        whitelistRecoveryFuture?.cancel(false)
        normalHealthFuture?.cancel(false)
        if (!store.desiredRunning || store.desiredMode != VpnMode.WHITELIST) return
        val delay = ThreadLocalRandom.current().nextLong(
            WL_RECOVERY_MIN_SECONDS,
            WL_RECOVERY_MAX_SECONDS
        )
        whitelistRecoveryFuture = scheduler.schedule({
            try {
                whitelistRecoveryCheck()
            } catch (e: Throwable) {
                reportUnexpectedError("wl_recovery", e)
            } finally {
                if (store.desiredRunning && store.desiredMode == VpnMode.WHITELIST) {
                    scheduleWhitelistRecovery()
                }
            }
        }, delay, TimeUnit.SECONDS)
    }

    private fun whitelistRecoveryCheck() {
        if (!store.desiredRunning || store.desiredMode != VpnMode.WHITELIST || starting.get()) return
        if ((physicalNetwork ?: selectPhysicalNetwork().also { physicalNetwork = it }) == null) return

        val primary = store.primaryServer()
        if (primary != null && stableReachable(primary)) {
            switchWhitelistToNormal(primary)
            return
        }

        val backup = store.backupServer()
        if (backup != null && stableReachable(backup)) {
            switchWhitelistToNormal(backup)
            return
        }

        // Normal access is still closed. Stay on WL. If the current WL entry itself
        // died and another WL entry exists, fail over only inside the WL pool.
        val activeWl = if (store.activeRole == VpnRole.WHITELIST) {
            store.whitelistServers().firstOrNull { it.id == store.serverForRole(VpnRole.WHITELIST)?.id }
        } else null
        if (activeWl != null && probeReachable(activeWl)) return

        val replacement = store.whitelistServers().firstOrNull { probeReachable(it) }
        if (replacement != null) {
            startTunnelAsync(replacement)
        } else {
            // Active WL died: immediately run the full primary -> backup -> WL chain.
            store.desiredMode = VpnMode.NORMAL
            _mode.value = VpnMode.NORMAL
            startTunnelAsync()
        }
    }

    private fun switchWhitelistToNormal(server: VpnServer) {
        store.desiredMode = VpnMode.NORMAL
        _mode.value = VpnMode.NORMAL
        _whitelistSuggested.value = false
        startTunnelAsync(server)
    }

    private fun stableReachable(server: VpnServer): Boolean {
        repeat(STABLE_PROBE_COUNT) { index ->
            if (!probeReachable(server)) return false
            if (index < STABLE_PROBE_COUNT - 1) Thread.sleep(STABLE_PROBE_GAP_MS)
        }
        return true
    }

    private fun probeReachable(server: VpnServer): Boolean {
        val raw = server.uri ?: return false
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return false
        val port = uri.port.takeIf { it > 0 } ?: 443
        val network = physicalNetwork ?: selectPhysicalNetwork().also { physicalNetwork = it }

        return runCatching {
            val socket: Socket = network?.socketFactory?.createSocket() ?: Socket()
            socket.use { it.connect(InetSocketAddress(host, port), TCP_PROBE_TIMEOUT_MS) }
            true
        }.getOrDefault(false)
    }

    private fun reportUnexpectedError(stage: String, e: Throwable) {
        val token = SessionStore(applicationContext).accessToken ?: return
        val detail = safeDiagnosticDetail(e)
        Thread {
            TrueWebApi.reportError(
                accessToken = token,
                device = DeviceIdentity(applicationContext),
                stage = stage,
                errorType = e.javaClass.simpleName.ifBlank { "Throwable" },
                detail = detail
            )
        }.start()
    }

    private fun safeDiagnosticDetail(e: Throwable): String {
        var value = (e.message ?: "").replace('\n', ' ').replace('\r', ' ').trim()
        // Never forward connection credentials, authorization values or long
        // token-like strings to diagnostics.
        value = value.replace(Regex("""(?i)(vless|vmess|trojan|ss)://[^\s]+"""), "<vpn-uri-redacted>")
        value = value.replace(Regex("""(?i)bearer\s+[A-Za-z0-9._~+\-/=]+"""), "Bearer <redacted>")
        value = value.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "<uuid-redacted>")
        value = value.replace(Regex("[A-Za-z0-9_-]{40,}"), "<token-redacted>")
        return value.take(300).ifBlank { e.javaClass.simpleName }
    }

    private fun establishVpnInterface(): ParcelFileDescriptor {
        val builder = Builder()
            .setSession("TrueWeb")
            .setMtu(1500)
            .addAddress("172.16.0.2", 30)
            .addRoute("0.0.0.0", 0)
            .addAddress("fdfe:dcba:9876::2", 126)
            .addRoute("::", 0)
            .addDnsServer("77.88.8.8")
            .addDnsServer("1.1.1.1")

        // Xray belongs to this app UID; excluding our own process keeps its outbound
        // sockets and health probes on the physical network and avoids a VPN loop.
        runCatching { builder.addDisallowedApplication(packageName) }

        // Russian apps are excluded at Android VpnService level in every TrueWeb mode,
        // including WL. They never enter the tunnel and therefore do not see a VPN IP.
        val policy = routingStore.load()
        if (policy.smartAuto) {
            policy.excludedPackages.forEach { pkg ->
                if (pkg != packageName) {
                    runCatching { builder.addDisallowedApplication(pkg) }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        physicalNetwork?.let { runCatching { setUnderlyingNetworks(arrayOf(it)) } }
        return builder.establish() ?: error("Android не создал VPN-интерфейс")
    }

    private fun ensureGeoAssets() {
        GeoDataManager.ensureBundledAssets(applicationContext)
    }

    @Synchronized
    private fun stopCoreOnly() {
        runCatching {
            val controller = core
            if (controller != null && controller.isRunning) controller.stopLoop()
        }
        runCatching { tunFd?.close() }
        tunFd = null
    }

    private fun stopTunnel(clearError: Boolean) {
        stopCoreOnly()
        if (clearError) _lastError.value = null
        _state.value = TunnelState.STOPPED
    }

    private fun cancelHealthSchedules() {
        normalHealthFuture?.cancel(false)
        whitelistRecoveryFuture?.cancel(false)
        normalHealthFuture = null
        whitelistRecoveryFuture = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "TrueWeb VPN", NotificationManager.IMPORTANCE_LOW).apply {
                    description = t("Подключение TrueWeb VPN", "TrueWeb VPN connection")
                    setShowBadge(false)
                }
            )
        }
    }

    private fun notification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TrueWeb")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_trueweb)
            .setContentIntent(pending)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }
}
