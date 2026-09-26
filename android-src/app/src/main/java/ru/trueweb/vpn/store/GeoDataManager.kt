package ru.trueweb.vpn.store

import android.content.Context
import org.json.JSONObject
import ru.trueweb.vpn.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object GeoDataManager {
    private const val PREFS = "trueweb_geodata"
    private const val KEY_LAST_SUCCESS = "last_success_ms"
    const val REFRESH_INTERVAL_MS = 72L * 60L * 60L * 1000L

    private const val GEOIP_REPO = "v2fly/geoip"
    private const val GEOIP_ASSET = "geoip.dat"
    private const val GEOSITE_REPO = "v2fly/domain-list-community"
    private const val GEOSITE_ASSET = "dlc.dat"
    private const val MIN_VALID_BYTES = 64L * 1024L

    private data class VerifiedAsset(
        val url: String,
        val sha256: String
    )

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
     * Download metadata first and verify each release asset by the SHA-256 digest
     * published by GitHub. A failed request or digest mismatch never replaces the
     * currently working GeoData pair.
     */
    fun updateNow(context: Context, force: Boolean = false): Result<Long> = runCatching {
        ensureBundledAssets(context)
        if (!force && !isStale(context)) return@runCatching lastSuccessMs(context)

        val geoipTmp = File(context.filesDir, "geoip.dat.download")
        val geositeTmp = File(context.filesDir, "geosite.dat.download")
        geoipTmp.delete()
        geositeTmp.delete()

        try {
            val geoip = resolveLatestAsset(GEOIP_REPO, GEOIP_ASSET)
            val geosite = resolveLatestAsset(GEOSITE_REPO, GEOSITE_ASSET)

            downloadVerified(geoip, geoipTmp)
            downloadVerified(geosite, geositeTmp)

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

    private fun resolveLatestAsset(repository: String, assetName: String): VerifiedAsset {
        val apiUrl = "https://api.github.com/repos/$repository/releases/latest"
        var connection: HttpURLConnection? = null
        try {
            connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "TrueWeb-Android/${BuildConfig.VERSION_NAME} GeoData")
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            val code = connection.responseCode
            require(code in 200..299) { "GeoData metadata HTTP $code" }

            val root = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                JSONObject(reader.readText())
            }
            val assets = root.optJSONArray("assets") ?: error("GeoData release assets missing")

            for (i in 0 until assets.length()) {
                val item = assets.optJSONObject(i) ?: continue
                if (item.optString("name") != assetName) continue

                val url = item.optString("browser_download_url").trim()
                val digest = item.optString("digest").trim().lowercase()
                val sha256 = digest.removePrefix("sha256:")

                require(url.isNotBlank()) { "GeoData asset URL missing" }
                require(URL(url).protocol.equals("https", ignoreCase = true)) { "GeoData asset URL is not HTTPS" }
                require(sha256.matches(Regex("[0-9a-f]{64}"))) { "GeoData SHA-256 digest missing" }

                return VerifiedAsset(url, sha256)
            }

            error("GeoData asset $assetName not found")
        } finally {
            connection?.disconnect()
        }
    }

    private fun downloadVerified(asset: VerifiedAsset, target: File) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(asset.url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 12_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "TrueWeb-Android/${BuildConfig.VERSION_NAME} GeoData")
            connection.setRequestProperty("Accept", "application/octet-stream,*/*;q=0.5")

            val code = connection.responseCode
            require(code in 200..299) { "GeoData HTTP $code" }

            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual.equals(asset.sha256, ignoreCase = true)) { "GeoData SHA-256 mismatch" }
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
