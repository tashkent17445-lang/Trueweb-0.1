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
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var accessToken: String?
        get() {
            val encrypted = prefs.getString(KEY_ACCESS_TOKEN_ENCRYPTED, null)
            if (!encrypted.isNullOrBlank()) {
                return runCatching { decrypt(encrypted) }
                    .getOrElse {
                        prefs.edit().remove(KEY_ACCESS_TOKEN_ENCRYPTED).apply()
                        null
                    }
            }

            // One-time migration from releases that stored the bearer token as plain text.
            val legacy = prefs.getString(KEY_ACCESS_TOKEN_LEGACY, null)
            if (!legacy.isNullOrBlank()) {
                accessToken = legacy
                return legacy
            }
            return null
        }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) {
                    remove(KEY_ACCESS_TOKEN_ENCRYPTED)
                    remove(KEY_ACCESS_TOKEN_LEGACY)
                } else {
                    putString(KEY_ACCESS_TOKEN_ENCRYPTED, encrypt(value))
                    remove(KEY_ACCESS_TOKEN_LEGACY)
                }
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

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(cipher.iv.size + ciphertext.size)
        System.arraycopy(cipher.iv, 0, packed, 0, cipher.iv.size)
        System.arraycopy(ciphertext, 0, packed, cipher.iv.size, ciphertext.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val packed = Base64.decode(value, Base64.NO_WRAP)
        require(packed.size > IV_SIZE_BYTES) { "Encrypted session token is invalid" }

        val iv = packed.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = packed.copyOfRange(IV_SIZE_BYTES, packed.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
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
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "trueweb_session"
        private const val KEY_ACCESS_TOKEN_LEGACY = "access_token"
        private const val KEY_ACCESS_TOKEN_ENCRYPTED = "access_token_enc_v1"
        private const val KEY_ALIAS = "trueweb_access_token_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
