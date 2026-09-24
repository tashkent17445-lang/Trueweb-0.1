package ru.trueweb.vpn.store

import android.content.Context
import ru.trueweb.vpn.ui.TrueWebThemeMode

class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("trueweb_theme", Context.MODE_PRIVATE)

    var mode: TrueWebThemeMode
        get() = when (prefs.getString("mode", TrueWebThemeMode.DARK.name)) {
            TrueWebThemeMode.LIGHT.name -> TrueWebThemeMode.LIGHT
            TrueWebThemeMode.DARK.name, "AMOLED" -> TrueWebThemeMode.DARK
            else -> TrueWebThemeMode.DARK
        }
        set(value) = prefs.edit().putString("mode", value.name).apply()
}
