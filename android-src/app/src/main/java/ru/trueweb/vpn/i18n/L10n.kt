package ru.trueweb.vpn.i18n

import android.content.Context
import java.util.Locale

enum class LanguageMode {
    AUTO,
    ENGLISH,
    RUSSIAN
}

object L10n {
    private const val PREFS_NAME = "trueweb_language"
    private const val KEY_MODE = "mode"

    @Volatile
    private var selectedMode: LanguageMode = LanguageMode.AUTO

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val saved = appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_MODE, LanguageMode.AUTO.name)

        selectedMode = runCatching { LanguageMode.valueOf(saved ?: LanguageMode.AUTO.name) }
            .getOrDefault(LanguageMode.AUTO)
    }

    val mode: LanguageMode
        get() = selectedMode

    fun setMode(mode: LanguageMode) {
        selectedMode = mode
        appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_MODE, mode.name)
            ?.apply()
    }

    fun isRussian(): Boolean = when (selectedMode) {
        LanguageMode.RUSSIAN -> true
        LanguageMode.ENGLISH -> false
        LanguageMode.AUTO -> Locale.getDefault().language.equals("ru", ignoreCase = true)
    }

    fun t(ru: String, en: String): String =
        if (isRussian()) ru else en

    fun days(value: Int): String =
        if (isRussian()) "$value дн." else "$value days"

    fun serverText(value: String): String {
        if (isRussian()) return value

        val normalized = value.trim()
        return when {
            normalized.equals("Дополнительное устройство", ignoreCase = true) ->
                "Additional device"

            Regex("""^Дополнительное устройство на\s+(\d+)\s+дн(?:я|ей)?$""", RegexOption.IGNORE_CASE)
                .matches(normalized) -> {
                val days = Regex("""\d+""").find(normalized)?.value ?: ""
                "Additional device for $days days"
            }

            Regex("""^(\d+)\s+дн(?:я|ей)?$""", RegexOption.IGNORE_CASE)
                .matches(normalized) -> {
                val days = Regex("""\d+""").find(normalized)?.value ?: ""
                "$days days"
            }

            normalized.equals("Оплата TrueWeb", ignoreCase = true) ->
                "TrueWeb payment"

            else -> value
        }
    }

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
