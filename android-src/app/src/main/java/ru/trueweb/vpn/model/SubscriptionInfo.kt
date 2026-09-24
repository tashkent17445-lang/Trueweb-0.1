package ru.trueweb.vpn.model

import org.json.JSONObject
import java.util.Locale

/** Data returned by GET /api/mobile/me. */
data class SubscriptionInfo(
    val active: Boolean,
    val enabled: Boolean,
    val trialPending: Boolean,
    val trialAvailable: Boolean,
    val unlimited: Boolean,
    val expiresLabel: String,
    val daysLeft: Int,
    val trafficUsedBytes: Long,
    val devicesUsed: Int,
    val devicesLimit: Int,
    val serverName: String,
    val lastOnlineMs: Long,
    val subscriptionUrl: String?
) {
    val trafficUsed: String get() = formatBytes(trafficUsedBytes)

    val devicesLabel: String
        get() = if (devicesLimit <= 0) "$devicesUsed / ∞" else "$devicesUsed из $devicesLimit"

    companion object {
        fun fromApi(root: JSONObject): SubscriptionInfo {
            val subscription = root.getJSONObject("subscription")
            val vpn = root.optJSONObject("vpn") ?: JSONObject()
            val server = vpn.optJSONObject("server") ?: JSONObject()
            return SubscriptionInfo(
                active = subscription.optBoolean("active", false),
                enabled = subscription.optBoolean("enabled", false),
                trialPending = subscription.optBoolean("trial_pending", false),
                trialAvailable = subscription.optBoolean("trial_available", false),
                unlimited = subscription.optBoolean("unlimited", false),
                expiresLabel = subscription.optString("expires_label", "нет данных"),
                daysLeft = subscription.optInt("days_left", 0),
                trafficUsedBytes = subscription.optLong("traffic_used_bytes", 0L),
                devicesUsed = subscription.optInt("devices_used", 0),
                devicesLimit = subscription.optInt("devices_limit", 0),
                serverName = server.optString("name", "Автовыбор"),
                lastOnlineMs = subscription.optLong("last_online_ms", 0L),
                subscriptionUrl = vpn.optString("subscription_url", "").takeIf { it.isNotBlank() }
            )
        }

        private fun formatBytes(bytes: Long): String {
            val safe = bytes.coerceAtLeast(0L).toDouble()
            val gib = 1024.0 * 1024.0 * 1024.0
            val mib = 1024.0 * 1024.0
            val kib = 1024.0
            val value = when {
                safe >= gib -> String.format(Locale.US, "%.1f ГБ", safe / gib)
                safe >= mib -> String.format(Locale.US, "%.1f МБ", safe / mib)
                safe >= kib -> String.format(Locale.US, "%.1f КБ", safe / kib)
                else -> "${safe.toLong()} Б"
            }
            return value.replace('.', ',')
        }
    }
}
