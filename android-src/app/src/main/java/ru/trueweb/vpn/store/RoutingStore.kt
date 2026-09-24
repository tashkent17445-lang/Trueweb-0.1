package ru.trueweb.vpn.store

import android.content.Context
import org.json.JSONArray
import ru.trueweb.vpn.model.RoutingPolicy

class RoutingStore(context: Context) {
    private val prefs = context.getSharedPreferences("trueweb_routing", Context.MODE_PRIVATE)

    val updatedAtMs: Long
        get() = prefs.getLong("updated_at", 0L)

    fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean =
        updatedAtMs <= 0L || nowMs - updatedAtMs >= 6L * 60L * 60L * 1000L

    fun load(): RoutingPolicy {
        val raw = prefs.getString("excluded_packages", null)
        val packages = if (raw.isNullOrBlank()) {
            DEFAULT_EXCLUDED_PACKAGES
        } else {
            runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val value = arr.optString(i).trim()
                        if (value.isNotBlank()) add(value)
                    }
                }
            }.getOrDefault(DEFAULT_EXCLUDED_PACKAGES)
        }
        return RoutingPolicy(
            smartAuto = prefs.getBoolean("smart_auto", true),
            bypassRu = prefs.getBoolean("bypass_ru", true),
            // Browsers must always stay inside TrueWeb. A browser can open any site,
            // so excluding the whole app is too broad even when the server sends it.
            excludedPackages = packages
                .filterNot { it in FORCE_VPN_PACKAGES }
                .distinct()
        )
    }

    fun save(policy: RoutingPolicy) {
        val packages = (policy.excludedPackages.ifEmpty { DEFAULT_EXCLUDED_PACKAGES })
            .filterNot { it in FORCE_VPN_PACKAGES }
            .distinct()
        val arr = JSONArray().apply { packages.forEach { put(it) } }
        prefs.edit()
            .putBoolean("smart_auto", policy.smartAuto)
            .putBoolean("bypass_ru", policy.bypassRu)
            .putString("excluded_packages", arr.toString())
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    companion object {
        // Safe local fallback. The server can replace this list without rebuilding the APK.
        val DEFAULT_EXCLUDED_PACKAGES = listOf(
            "ru.sberbankmobile",
            "ru.vtb24.mobilebanking.android",
            "ru.alfabank.mobile.android",
            "com.idamob.tinkoff.android",
            "ru.nspk.mirpay",
            "ru.rostel",
            "ru.ozon.app.android",
            "ru.wildberries",
            "com.avito.android",
            "ru.yandex.taxi",
            "ru.yandex.yandexmaps"
        )

        // Russian browsers are intentionally never excluded from the VPN tunnel.
        // This also overrides an older cached/server routing list after app update.
        val FORCE_VPN_PACKAGES = setOf(
            "com.yandex.browser", // Yandex Browser
            "ru.mail.browser"     // Atom Browser
        )
    }
}
