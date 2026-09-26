package ru.trueweb.vpn.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("trueweb_session", Context.MODE_PRIVATE)
    private val securePrefs = appContext.getSharedPreferences("trueweb_session_secure", Context.MODE_PRIVATE)

    var accessToken: String?
        get() {
            val encrypted = securePrefs.getString(KEY_ACCESS_TOKEN_ENCRYPTED, null)
            if (!encrypted.isNullOrBlank()) {
                val decrypted = runCatching { decrypt(encrypted) }.getOrNull()
                if (!decrypted.isNullOrBlank()) return decrypted

                // A key can be invalidated by the OS/device security state. Never keep
                // an unreadable token around indefinitely; force a clean re-login.
                securePrefs.edit().remove(KEY_ACCESS_TOKEN_ENCRYPTED).apply()
            }

            // One-time migration from TrueWeb <= 0.9.8 where the bearer token lived
            // as plaintext in ordinary SharedPreferences.
            val legacy = prefs.getString(KEY_ACCESS_TOKEN_LEGACY, null)
            if (!legacy.isNullOrBlank()) {
                runCatching {
                    saveEncrypted(legacy)
                    prefs.edit().remove(KEY_ACCESS_TOKEN_LEGACY).apply()
                }
                return legacy
            }
            return null
        }
        set(value) {
            if (value.isNullOrBlank()) {
                securePrefs.edit().remove(KEY_ACCESS_TOKEN_ENCRYPTED).apply()
                prefs.edit().remove(KEY_ACCESS_TOKEN_LEGACY).apply()
                return
            }

            // Keystore is available on every supported TrueWeb Android version.
            // Keep a compatibility fallback only for devices with a broken/inaccessible
            // keystore so authentication does not become impossible.
            runCatching {
                saveEncrypted(value)
                prefs.edit().remove(KEY_ACCESS_TOKEN_LEGACY).apply()
            }.onFailure {
                securePrefs.edit().remove(KEY_ACCESS_TOKEN_ENCRYPTED).apply()
                prefs.edit().putString(KEY_ACCESS_TOKEN_LEGACY, value).apply()
            }
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

    var telegramAuthState: String?
        get() = prefs.getString("telegram_auth_state", null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove("telegram_auth_state")
                else putString("telegram_auth_state", value)
            }.apply()
        }

    val isAuthenticated: Boolean get() = !accessToken.isNullOrBlank()
    val isEmailAccount: Boolean get() = authMethod == "email"

    fun clear() {
        prefs.edit().clear().apply()
        securePrefs.edit().clear().apply()
    }

    private fun saveEncrypted(value: String) {
        securePrefs.edit()
            .putString(KEY_ACCESS_TOKEN_ENCRYPTED, encrypt(value))
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(1 + cipher.iv.size + encrypted.size)
        payload[0] = cipher.iv.size.toByte()
        System.arraycopy(cipher.iv, 0, payload, 1, cipher.iv.size)
        System.arraycopy(encrypted, 0, payload, 1 + cipher.iv.size, encrypted.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Encrypted session payload is empty" }

        val ivSize = payload[0].toInt() and 0xff
        require(ivSize in 12..32 && payload.size > 1 + ivSize) { "Invalid encrypted session payload" }

        val iv = payload.copyOfRange(1, 1 + ivSize)
        val encrypted = payload.copyOfRange(1 + ivSize, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN_LEGACY = "access_token"
        private const val KEY_ACCESS_TOKEN_ENCRYPTED = "access_token_v2"
        private const val KEY_ALIAS = "trueweb_session_aes_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
