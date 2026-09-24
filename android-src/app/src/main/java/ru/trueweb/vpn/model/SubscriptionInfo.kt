package ru.trueweb.vpn.model

import ru.trueweb.vpn.i18n.L10n
import ru.trueweb.vpn.i18n.L10n.t

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
        get() = if (devicesLimit <= 0) "$devicesUsed / ∞" else t("$devicesUsed из $devicesLimit", "$devicesUsed of $devicesLimit")

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
                expiresLabel = subscription.optString("expires_label", t("нет данных", "no data")),
                daysLeft = subscription.optInt("days_left", 0),
                trafficUsedBytes = subscription.optLong("traffic_used_bytes", 0L),
                devicesUsed = subscription.optInt("devices_used", 0),
                devicesLimit = subscription.optInt("devices_limit", 0),
                serverName = server.optString("name", t("Автовыбор", "Auto-select")),
                lastOnlineMs = subscription.optLong("last_online_ms", 0L),
                subscriptionUrl = vpn.optString("subscription_url", "").takeIf { it.isNotBlank() }
            )
        }

        private fun formatBytes(bytes: Long): String {
            return L10n.bytes(bytes)
        }
    }
}
