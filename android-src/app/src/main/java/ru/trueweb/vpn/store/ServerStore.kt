package ru.trueweb.vpn.store

import android.content.Context
import org.json.JSONArray
import ru.trueweb.vpn.model.ServerCatalog
import ru.trueweb.vpn.model.VpnMode
import ru.trueweb.vpn.model.VpnRole
import ru.trueweb.vpn.model.VpnServer

class ServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("trueweb_vpn", Context.MODE_PRIVATE)

    var desiredRunning: Boolean
        get() = prefs.getBoolean("desired_running", false)
        set(value) = prefs.edit().putBoolean("desired_running", value).apply()

    var desiredMode: VpnMode
        get() = runCatching {
            VpnMode.valueOf(prefs.getString("desired_mode", VpnMode.NORMAL.name) ?: VpnMode.NORMAL.name)
        }.getOrDefault(VpnMode.NORMAL)
        set(value) = prefs.edit().putString("desired_mode", value.name).apply()

    var activeRole: VpnRole
        get() = runCatching {
            VpnRole.valueOf(prefs.getString("active_role", VpnRole.UNKNOWN.name) ?: VpnRole.UNKNOWN.name)
        }.getOrDefault(VpnRole.UNKNOWN)
        set(value) = prefs.edit().putString("active_role", value.name).apply()

    val updatedAtMs: Long get() = prefs.getLong("servers_updated_at", 0L)

    fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean =
        updatedAtMs <= 0L || nowMs - updatedAtMs >= 60L * 60L * 1000L

    fun loadServers(): List<VpnServer> {
        val raw = prefs.getString("servers_json", null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { VpnServer.listFromJson(JSONArray(raw)) }
            .getOrDefault(emptyList())
    }

    fun saveCatalog(catalog: ServerCatalog) {
        val normalized = catalog.servers
            .filter { it.available && it.protocol.equals("vless", ignoreCase = true) && !it.uri.isNullOrBlank() }
            .distinctBy { it.id }
        val array = JSONArray().apply { normalized.forEach { put(it.toJson()) } }
        prefs.edit()
            .putString("servers_json", array.toString())
            .putLong("servers_updated_at", System.currentTimeMillis())
            .apply()
    }

    fun primaryServer(): VpnServer? = loadServers().firstOrNull {
        it.role == VpnRole.PRIMARY || it.inboundId == PRIMARY_INBOUND_ID
    }

    fun backupServer(): VpnServer? = loadServers().firstOrNull {
        it.role == VpnRole.BACKUP || it.inboundId == BACKUP_INBOUND_ID
    }

    fun whitelistServers(): List<VpnServer> = loadServers().filter {
        it.role == VpnRole.WHITELIST
    }

    fun serverForRole(role: VpnRole): VpnServer? = when (role) {
        VpnRole.PRIMARY -> primaryServer()
        VpnRole.BACKUP -> backupServer()
        VpnRole.WHITELIST -> whitelistServers().firstOrNull()
        VpnRole.UNKNOWN -> null
    }

    fun hasRequiredNormalServers(): Boolean = primaryServer() != null && backupServer() != null

    fun clearRuntime() {
        prefs.edit()
            .remove("servers_json")
            .remove("servers_updated_at")
            .putBoolean("desired_running", false)
            .putString("desired_mode", VpnMode.NORMAL.name)
            .putString("active_role", VpnRole.UNKNOWN.name)
            .apply()
    }

    companion object {
        const val PRIMARY_INBOUND_ID = 1
        const val BACKUP_INBOUND_ID = 18
    }
}
