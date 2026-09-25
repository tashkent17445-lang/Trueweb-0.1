package ru.trueweb.vpn.store

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONArray
import ru.trueweb.vpn.model.RoutingPolicy

class RoutingStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("trueweb_routing", Context.MODE_PRIVATE)

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
            excludedPackages = filterExcludedPackages(packages)
        )
    }

    fun save(policy: RoutingPolicy) {
        val packages = filterExcludedPackages(
            policy.excludedPackages.ifEmpty { DEFAULT_EXCLUDED_PACKAGES }
        )
        val arr = JSONArray().apply { packages.forEach { put(it) } }
        prefs.edit()
            .putBoolean("smart_auto", policy.smartAuto)
            .putBoolean("bypass_ru", policy.bypassRu)
            .putString("excluded_packages", arr.toString())
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    /**
     * Browsers must never be excluded at Android VpnService level.
     *
     * A browser can open both Russian and foreign sites, so app-level bypass is too broad.
     * Keeping browsers in the tunnel lets Xray decide per destination:
     * Russian domains/IPs -> direct, everything else -> TrueWeb VPN.
     */
    private fun filterExcludedPackages(packages: List<String>): List<String> {
        val forceVpn = FORCE_VPN_PACKAGES + installedBrowserPackages()
        return packages
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it in forceVpn }
            .distinct()
    }

    private fun installedBrowserPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)

        return runCatching {
            @Suppress("DEPRECATION")
            appContext.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName?.trim()?.takeIf(String::isNotBlank) }
                .toSet()
        }.getOrDefault(emptySet())
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

        // Fallback protection for common browsers. Installed browsers are detected dynamically too.
        val FORCE_VPN_PACKAGES = setOf(
            "com.yandex.browser",
            "ru.mail.browser",
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.huawei.browser",
            "com.mi.globalbrowser",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser",
            "com.duckduckgo.mobile.android",
            "com.android.browser"
        )
    }
}
