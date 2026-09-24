package ru.trueweb.vpn.i18n

import java.util.Locale

object L10n {
    fun isRussian(): Boolean =
        Locale.getDefault().language.equals("ru", ignoreCase = true)

    fun t(ru: String, en: String): String =
        if (isRussian()) ru else en

    fun days(value: Int): String =
        if (isRussian()) "$value дн." else "$value days"

    fun bytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L).toDouble()
        val kib = 1024.0
        val mib = kib * 1024.0
        val gib = mib * 1024.0
        return if (isRussian()) {
            when {
                safe >= gib -> String.format(Locale.US, "%.1f ГБ", safe / gib)
                safe >= mib -> String.format(Locale.US, "%.1f МБ", safe / mib)
                safe >= kib -> String.format(Locale.US, "%.1f КБ", safe / kib)
                else -> "${safe.toLong()} Б"
            }
        } else {
            when {
                safe >= gib -> String.format(Locale.US, "%.1f GB", safe / gib)
                safe >= mib -> String.format(Locale.US, "%.1f MB", safe / mib)
                safe >= kib -> String.format(Locale.US, "%.1f KB", safe / kib)
                else -> "${safe.toLong()} B"
            }
        }
    }
}
