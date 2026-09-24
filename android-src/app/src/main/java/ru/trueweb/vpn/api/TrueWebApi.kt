package ru.trueweb.vpn.api

import org.json.JSONObject
import ru.trueweb.vpn.AppConfig
import ru.trueweb.vpn.BuildConfig
import ru.trueweb.vpn.model.*
import ru.trueweb.vpn.store.DeviceIdentity
import java.net.HttpURLConnection
import java.net.URL

object TrueWebApi {
    data class EmailStartResult(
        val email: String,
        val expiresIn: Int,
        val resendAfter: Int
    )

    data class EmailAuthResult(
        val accessToken: String,
        val isNew: Boolean,
        val trialActivated: Boolean,
        val needsTelegramLink: Boolean
    )

    fun startEmailAuth(email: String): Result<EmailStartResult> = runCatching {
        val root = request(
            method = "POST",
            path = "/auth/email/start",
            body = JSONObject().put("email", email.trim()).toString()
        )
        EmailStartResult(
            email = root.optString("email", email.trim()),
            expiresIn = root.optInt("expires_in", 600),
            resendAfter = root.optInt("resend_after", 60)
        )
    }

    fun verifyEmailAuth(email: String, code: String): Result<EmailAuthResult> = runCatching {
        val root = request(
            method = "POST",
            path = "/auth/email/verify",
            body = JSONObject()
                .put("email", email.trim())
                .put("code", code.trim())
                .toString()
        )
        EmailAuthResult(
            accessToken = root.getString("access_token"),
            isNew = root.optBoolean("is_new", false),
            trialActivated = root.optBoolean("trial_activated", false),
            needsTelegramLink = root.optBoolean("needs_telegram_link", true)
        )
    }


    data class PasswordAuthResult(
        val accessToken: String,
        val needsTelegramLink: Boolean
    )

    fun passwordLogin(login: String, password: String): Result<PasswordAuthResult> = runCatching {
        val root = request(
            method = "POST",
            path = "/auth/password/login",
            body = JSONObject()
                .put("login", login.trim())
                .put("password", password)
                .toString()
        )
        PasswordAuthResult(
            accessToken = root.getString("access_token"),
            needsTelegramLink = root.optBoolean("needs_telegram_link", false)
        )
    }

    fun setPasswordCredentials(accessToken: String, login: String, password: String): Result<Unit> = runCatching {
        request(
            method = "POST",
            path = "/account/password",
            accessToken = accessToken,
            body = JSONObject()
                .put("login", login.trim())
                .put("password", password)
                .toString()
        )
        Unit
    }

    fun deleteAccount(accessToken: String, device: DeviceIdentity): Result<Unit> = runCatching {
        request(
            method = "DELETE",
            path = "/account",
            accessToken = accessToken,
            extraHeaders = deviceHeaders(device)
        )
        Unit
    }

    fun reportError(
        accessToken: String,
        device: DeviceIdentity,
        stage: String,
        errorType: String,
        detail: String
    ): Result<Unit> = runCatching {
        request(
            method = "POST",
            path = "/errors/report",
            accessToken = accessToken,
            extraHeaders = deviceHeaders(device),
            body = JSONObject()
                .put("stage", stage.take(64))
                .put("error_type", errorType.take(64))
                .put("detail", detail.take(300))
                .toString()
        )
        Unit
    }

    fun linkTelegram(accessToken: String, telegramCode: String): Result<String> = runCatching {
        request(
            method = "POST",
            path = "/auth/email/link-telegram",
            accessToken = accessToken,
            body = JSONObject().put("code", telegramCode.trim()).toString()
        ).getString("access_token")
    }

    fun exchangeCode(code: String): Result<String> = runCatching {
        request(
            method = "POST",
            path = "/auth/exchange",
            body = JSONObject().put("code", code).toString()
        ).getString("access_token")
    }

    fun me(accessToken: String): Result<SubscriptionInfo> = runCatching {
        SubscriptionInfo.fromApi(request("GET", "/me", accessToken = accessToken))
    }

    fun servers(accessToken: String, device: DeviceIdentity): Result<ServerCatalog> = runCatching {
        val json = request(
            method = "GET",
            path = "/servers",
            accessToken = accessToken,
            extraHeaders = deviceHeaders(device)
        )
        ServerCatalog.fromApi(json)
    }

    fun activateTrial(accessToken: String): Result<SubscriptionInfo> = runCatching {
        SubscriptionInfo.fromApi(request("POST", "/trial", accessToken = accessToken, body = "{}"))
    }

    fun routing(accessToken: String): Result<RoutingPolicy> = runCatching {
        RoutingPolicy.fromApi(request("GET", "/routing", accessToken = accessToken))
    }

    fun billingCatalog(accessToken: String): Result<BillingCatalog> = runCatching {
        val root = request("GET", "/tariffs", accessToken = accessToken)
        val array = root.optJSONArray("tariffs")
        val tariffs = buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(TariffOption.fromApi(item))
                }
            }
        }
        val device = root.optJSONObject("device_product")?.let(DeviceProduct::fromApi)
        BillingCatalog(tariffs, device)
    }

    fun devices(accessToken: String, device: DeviceIdentity): Result<DeviceCatalog> = runCatching {
        val root = request(
            "GET",
            "/devices",
            accessToken = accessToken,
            extraHeaders = deviceHeaders(device)
        )
        val array = root.optJSONArray("devices")
        val devices = buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(DeviceItem.fromApi(item))
                }
            }
        }
        DeviceCatalog(
            devices = devices,
            used = root.optInt("used", devices.size),
            limit = root.optInt("limit", 0)
        )
    }

    fun deleteDevice(accessToken: String, deviceId: Int, device: DeviceIdentity): Result<Unit> = runCatching {
        request(
            "DELETE",
            "/devices/$deviceId",
            accessToken = accessToken,
            extraHeaders = deviceHeaders(device)
        )
        Unit
    }

    fun createPayment(accessToken: String, product: String): Result<PaymentStart> = runCatching {
        val root = request(
            "POST",
            "/payment/create",
            accessToken = accessToken,
            body = JSONObject().put("product", product).toString()
        )
        PaymentStart(
            id = root.getString("payment_id"),
            confirmationUrl = root.getString("confirmation_url"),
            product = root.optString("product", product),
            title = root.optString("title", "Оплата TrueWeb"),
            price = root.optInt("price", 0)
        )
    }

    fun paymentStatus(accessToken: String, paymentId: String): Result<PaymentStatus> = runCatching {
        val root = request("GET", "/payment/$paymentId", accessToken = accessToken)
        PaymentStatus(
            id = root.optString("payment_id", paymentId),
            status = root.optString("status", "pending"),
            processed = root.optBoolean("processed", false),
            message = root.optString("message", "")
        )
    }

    fun logout(accessToken: String): Result<Unit> = runCatching {
        request("POST", "/logout", accessToken = accessToken, body = "{}")
        Unit
    }

    private fun deviceHeaders(device: DeviceIdentity) = mapOf(
        "x-hwid" to device.hwid,
        "x-device-os" to device.osName,
        "x-device-model" to device.model,
        "x-ver-os" to device.osVersion
    )

    private fun request(
        method: String,
        path: String,
        accessToken: String? = null,
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONObject {
        val connection = URL("${AppConfig.API_BASE}$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "TrueWeb-Android/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME)
            if (!accessToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            extraHeaders.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse {
                JSONObject().put("error", text.take(500))
            }

            if (responseCode !in 200..299) {
                val message = json.optString("error", "HTTP $responseCode")
                error("HTTP $responseCode: $message")
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}
