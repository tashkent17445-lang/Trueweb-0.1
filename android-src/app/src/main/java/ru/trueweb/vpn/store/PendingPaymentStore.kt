package ru.trueweb.vpn.store

import android.content.Context

class PendingPaymentStore(context: Context) {
    private val prefs = context.getSharedPreferences("trueweb_payments", Context.MODE_PRIVATE)

    var paymentId: String?
        get() = prefs.getString("pending_payment_id", null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value.isNullOrBlank()) prefs.edit().remove("pending_payment_id").apply()
            else prefs.edit().putString("pending_payment_id", value).apply()
        }

    fun clear() {
        prefs.edit().remove("pending_payment_id").apply()
    }
}
