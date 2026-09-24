package ru.trueweb.vpn.store

import android.content.Context
import android.os.Build
import java.util.UUID

class DeviceIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("trueweb_device", Context.MODE_PRIVATE)

    val hwid: String
        get() {
            val current = prefs.getString("install_hwid", null)
            if (!current.isNullOrBlank()) return current
            val generated = UUID.randomUUID().toString().lowercase()
            prefs.edit().putString("install_hwid", generated).apply()
            return generated
        }

    val osName: String get() = "Android"
    val osVersion: String get() = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()
    val model: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android device" }
}
