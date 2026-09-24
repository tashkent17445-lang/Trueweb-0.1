package ru.trueweb.vpn.model

import org.json.JSONArray
import org.json.JSONObject

enum class VpnRole {
    PRIMARY,
    BACKUP,
    WHITELIST,
    UNKNOWN;

    companion object {
        fun fromApi(value: String?): VpnRole = when ((value ?: "").trim().lowercase()) {
            "primary", "main", "main_1" -> PRIMARY
            "backup", "reserve", "fallback" -> BACKUP
            "wl", "whitelist", "white_list" -> WHITELIST
            else -> UNKNOWN
        }
    }
}

enum class VpnMode { NORMAL, WHITELIST }

data class VpnServer(
    val id: String,
    val name: String,
    val protocol: String,
    val uri: String?,
    val available: Boolean,
    val role: VpnRole = VpnRole.UNKNOWN,
    val inboundId: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("protocol", protocol)
        put("uri", uri ?: JSONObject.NULL)
        put("available", available)
        put("role", role.name.lowercase())
        put("inbound_id", inboundId)
    }

    companion object {
        fun fromJson(obj: JSONObject): VpnServer = VpnServer(
            id = obj.optString("id"),
            name = obj.optString("name", "TrueWeb"),
            protocol = obj.optString("protocol", "vless"),
            uri = if (obj.isNull("uri")) null else obj.optString("uri", "").takeIf { it.isNotBlank() && it != "null" },
            available = obj.optBoolean("available", true),
            role = VpnRole.fromApi(obj.optString("role", "")),
            inboundId = obj.optInt("inbound_id", 0)
        )

        fun listFromJson(array: JSONArray): List<VpnServer> = buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val server = fromJson(obj)
                if (server.id.isNotBlank()) add(server)
            }
        }
    }
}

data class ServerCatalog(
    val servers: List<VpnServer>,
    val devicesUsed: Int,
    val devicesLimit: Int,
    val refreshIntervalSeconds: Long
) {
    companion object {
        fun fromApi(root: JSONObject): ServerCatalog {
            val devices = root.optJSONObject("devices") ?: JSONObject()
            return ServerCatalog(
                servers = VpnServer.listFromJson(root.optJSONArray("servers") ?: JSONArray()),
                devicesUsed = devices.optInt("used", 0),
                devicesLimit = devices.optInt("limit", 0),
                refreshIntervalSeconds = root.optLong("refresh_interval_seconds", 3600L).coerceAtLeast(900L)
            )
        }
    }
}
