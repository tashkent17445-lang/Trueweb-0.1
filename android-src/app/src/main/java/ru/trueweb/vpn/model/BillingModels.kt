package ru.trueweb.vpn.model

import org.json.JSONObject

data class TariffOption(
    val code: String,
    val title: String,
    val price: Int,
    val days: Int,
    val badge: String
) {
    companion object {
        fun fromApi(json: JSONObject) = TariffOption(
            code = json.optString("code"),
            title = json.optString("title"),
            price = json.optInt("price", 0),
            days = json.optInt("days", 0),
            badge = json.optString("badge", "")
        )
    }
}

data class DeviceProduct(
    val code: String,
    val title: String,
    val price: Int,
    val days: Int
) {
    companion object {
        fun fromApi(json: JSONObject): DeviceProduct = DeviceProduct(
            code = json.optString("code", "device"),
            title = json.optString("title", "Дополнительное устройство"),
            price = json.optInt("price", 0),
            days = json.optInt("days", 30)
        )
    }
}

data class BillingCatalog(
    val tariffs: List<TariffOption>,
    val deviceProduct: DeviceProduct?
)

data class DeviceItem(
    val id: Int,
    val title: String,
    val details: String,
    val lastSeenMs: Long,
    val isCurrent: Boolean
) {
    companion object {
        fun fromApi(json: JSONObject): DeviceItem = DeviceItem(
            id = json.optInt("id", 0),
            title = json.optString("title", "Устройство"),
            details = json.optString("details", ""),
            lastSeenMs = json.optLong("last_seen_ms", 0L),
            isCurrent = json.optBoolean("is_current", false)
        )
    }
}

data class DeviceCatalog(
    val devices: List<DeviceItem>,
    val used: Int,
    val limit: Int
)

data class PaymentStart(
    val id: String,
    val confirmationUrl: String,
    val product: String,
    val title: String,
    val price: Int
)

data class PaymentStatus(
    val id: String,
    val status: String,
    val processed: Boolean,
    val message: String
)
