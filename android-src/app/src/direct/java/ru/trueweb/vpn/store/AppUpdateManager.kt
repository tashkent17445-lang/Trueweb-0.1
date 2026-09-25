package ru.trueweb.vpn.store

import android.content.Context
import org.json.JSONObject
import ru.trueweb.vpn.AppConfig
import ru.trueweb.vpn.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object AppUpdateManager {
    private const val PREFS = "trueweb_app_update"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val sha256: String,
        val notes: String
    )

    fun check(context: Context, force: Boolean = false): Result<UpdateInfo?> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force) {
            val last = prefs.getLong(KEY_LAST_CHECK, 0L)
            if (last > 0L && now - last < AUTO_CHECK_INTERVAL_MS) return@runCatching null
        }

        val connection = URL(AppConfig.APP_UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("User-Agent", "TrueWeb-Android/${BuildConfig.VERSION_NAME}")

            val code = connection.responseCode
            if (code !in 200..299) error("UPDATE_MANIFEST_HTTP_$code")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            val versionCode = when {
                root.has("version_code") -> root.optInt("version_code", 0)
                root.has("versionCode") -> root.optInt("versionCode", 0)
                else -> 0
            }
            val versionName = root.optString("version", root.optString("version_name", "")).trim()
            val url = root.optString("url", "").trim()
            val sha256 = root.optString("sha256", "").trim().lowercase()
            val notes = root.optString("notes", "").trim()

            require(versionCode > 0) { "UPDATE_MANIFEST_VERSION_CODE" }
            require(versionName.isNotBlank()) { "UPDATE_MANIFEST_VERSION" }
            require(url.isNotBlank()) { "UPDATE_MANIFEST_URL" }
            require(sha256.matches(Regex("[0-9a-f]{64}"))) { "UPDATE_MANIFEST_SHA256" }

            val downloadUrl = URL(url)
            require(downloadUrl.protocol.equals("https", ignoreCase = true)) { "UPDATE_URL_NOT_HTTPS" }
            require(
                downloadUrl.host.equals("trueweb24.ru", ignoreCase = true) ||
                    downloadUrl.host.equals("start.trueweb24.ru", ignoreCase = true)
            ) { "UPDATE_URL_HOST" }

            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

            if (versionCode > BuildConfig.VERSION_CODE) {
                UpdateInfo(versionCode, versionName, url, sha256, notes)
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    fun download(context: Context, update: UpdateInfo): Result<File> = runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeVersion = update.versionName.replace(Regex("[^0-9A-Za-z._-]+"), "_")
        val finalFile = File(dir, "TrueWeb-$safeVersion.apk")
        val partFile = File(dir, "TrueWeb-$safeVersion.apk.part")
        partFile.delete()

        val connection = URL(update.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/octet-stream,*/*")
            connection.setRequestProperty("User-Agent", "TrueWeb-Android/${BuildConfig.VERSION_NAME}")

            val code = connection.responseCode
            if (code !in 200..299) error("UPDATE_APK_HTTP_$code")

            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                partFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            require(partFile.length() > 1024L * 1024L) { "UPDATE_APK_TOO_SMALL" }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual.equals(update.sha256, ignoreCase = true)) { "UPDATE_APK_SHA256" }

            finalFile.delete()
            require(partFile.renameTo(finalFile)) { "UPDATE_APK_RENAME" }
            finalFile
        } finally {
            connection.disconnect()
            if (partFile.exists()) partFile.delete()
        }
    }
}
