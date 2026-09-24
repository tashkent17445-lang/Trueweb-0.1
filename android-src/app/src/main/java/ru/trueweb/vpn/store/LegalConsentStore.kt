package ru.trueweb.vpn.store

import android.content.Context

class LegalConsentStore(context: Context) {
    private val prefs = context.getSharedPreferences("trueweb_legal", Context.MODE_PRIVATE)

    var privacyAccepted: Boolean
        get() = prefs.getBoolean("privacy_accepted_v1", false)
        set(value) = prefs.edit().putBoolean("privacy_accepted_v1", value).apply()
}
