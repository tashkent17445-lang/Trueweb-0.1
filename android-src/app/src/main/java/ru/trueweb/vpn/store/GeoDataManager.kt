package ru.trueweb.vpn.store

import android.content.Context
import ru.trueweb.vpn.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object GeoDataManager {
    private const val PREFS = "trueweb_geodata"
    private const val KEY_LAST_SUCCESS = "last_success_ms"
    const val REFRESH_INTERVAL_MS = 72L * 60L * 60L * 1000L

    private const val GEOIP_URL = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat"
    private const val GEOSITE_URL = "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat"
    private const val MIN_VALID_BYTES = 64L * 1024L

    fun lastSuccessMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SUCCESS, 0L)

    fun isStale(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = lastSuccessMs(context)
        return last <= 0L || nowMs - last >= REFRESH_INTERVAL_MS
    }

    /**
     * Keep bundled GeoData as an offline fallback. Unlike 0.8.4 this does not
     * overwrite a newer downloaded database every time the VPN service starts.
     */
    fun ensureBundledAssets(context: Context) {
        listOf("geoip.dat", "geosite.dat").forEach { name ->
            val target = File(context.filesDir, name)
            if (target.exists() && target.length() >= MIN_VALID_BYTES) return@forEach
            val tmp = File(context.filesDir, "$name.bundled.tmp")
            runCatching {
                context.assets.open(name).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                require(tmp.length() >= MIN_VALID_BYTES) { "$name is too small" }
                replaceFile(tmp, target)
            }.onFailure {
                tmp.delete()
                // Let Xray surface the normal startup error if the packaged asset
                // is genuinely missing/corrupt; do not destroy an existing file.
            }
        }
    }

    /**
     * Downloads both databases first and only then replaces the working pair.
     * A failed/blocked GitHub request therefore never breaks the currently
     * installed GeoData.
     */
    fun updateNow(context: Context, force: Boolean = false): Result<Long> = runCatching {
        ensureBundledAssets(context)
        if (!force && !isStale(context)) return@runCatching lastSuccessMs(context)

        val geoipTmp = File(context.filesDir, "geoip.dat.download")
        val geositeTmp = File(context.filesDir, "geosite.dat.download")
        geoipTmp.delete()
        geositeTmp.delete()

        try {
            download(GEOIP_URL, geoipTmp)
            download(GEOSITE_URL, geositeTmp)
            require(geoipTmp.length() >= MIN_VALID_BYTES) { "geoip.dat download is too small" }
            require(geositeTmp.length() >= MIN_VALID_BYTES) { "geosite.dat download is too small" }

            val geoipTarget = File(context.filesDir, "geoip.dat")
            val geositeTarget = File(context.filesDir, "geosite.dat")
            val geoipBackup = File(context.filesDir, "geoip.dat.previous")
            val geositeBackup = File(context.filesDir, "geosite.dat.previous")

            backupCurrent(geoipTarget, geoipBackup)
            backupCurrent(geositeTarget, geositeBackup)

            try {
                replaceFile(geoipTmp, geoipTarget)
                replaceFile(geositeTmp, geositeTarget)
            } catch (e: Throwable) {
                restoreBackup(geoipBackup, geoipTarget)
                restoreBackup(geositeBackup, geositeTarget)
                throw e
            }

            geoipBackup.delete()
            geositeBackup.delete()
            val now = System.currentTimeMillis()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SUCCESS, now).apply()
            now
        } finally {
            geoipTmp.delete()
            geositeTmp.delete()
        }
    }

    private fun download(url: String, target: File) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 12_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "TrueWeb-Android/${BuildConfig.VERSION_NAME} GeoData")
            connection.setRequestProperty("Accept", "application/octet-stream,*/*;q=0.5")
            val code = connection.responseCode
            require(code in 200..299) { "GeoData HTTP $code" }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun backupCurrent(source: File, backup: File) {
        backup.delete()
        if (source.exists()) source.copyTo(backup, overwrite = true)
    }

    private fun restoreBackup(backup: File, target: File) {
        if (backup.exists()) backup.copyTo(target, overwrite = true)
    }

    private fun replaceFile(source: File, target: File) {
        val staging = File(target.parentFile, "${target.name}.replace")
        staging.delete()
        source.copyTo(staging, overwrite = true)
        if (target.exists() && !target.delete()) error("Cannot replace ${target.name}")
        if (!staging.renameTo(target)) {
            staging.copyTo(target, overwrite = true)
            staging.delete()
        }
    }
}
