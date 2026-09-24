package ru.trueweb.vpn.auth

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("trueweb_session", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove("access_token") else putString("access_token", value)
            }.apply()
        }

    var authMethod: String
        get() = prefs.getString("auth_method", "telegram") ?: "telegram"
        set(value) = prefs.edit().putString("auth_method", value).apply()

    var telegramLinkPending: Boolean
        get() = prefs.getBoolean("telegram_link_pending", false)
        set(value) = prefs.edit().putBoolean("telegram_link_pending", value).apply()

    var needsTelegramLink: Boolean
        get() = prefs.getBoolean("needs_telegram_link", false)
        set(value) = prefs.edit().putBoolean("needs_telegram_link", value).apply()

    val isAuthenticated: Boolean get() = !accessToken.isNullOrBlank()
    val isEmailAccount: Boolean get() = authMethod == "email"

    fun clear() {
        prefs.edit().clear().apply()
    }
}
