package ru.trueweb.vpn.store

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

object UpdateInstaller {
    private const val EXPECTED_PACKAGE = "ru.trueweb.vpn"
    private const val EXPECTED_CERT_SHA256 =
        "d16a23080bf0c343a603880c64f9d012fe74f398abe2b5c442427c0f1afe5ec2"

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${context.packageName}")
            )
        } else {
            null
        }

    fun install(context: Context, apk: File): Result<Unit> = runCatching {
        verifyDownloadedApk(context, apk)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun verifyDownloadedApk(context: Context, apk: File) {
        require(apk.isFile && apk.length() > 0L) { "UPDATE_APK_MISSING" }

        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("UPDATE_APK_INVALID")

        require(info.packageName == EXPECTED_PACKAGE) { "UPDATE_APK_PACKAGE" }

        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: error("UPDATE_APK_SIGNING_INFO")
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }

        require(signatures.isNotEmpty()) { "UPDATE_APK_NO_SIGNATURE" }

        val trusted = signatures.any { signature ->
            sha256(signature.toByteArray()).equals(EXPECTED_CERT_SHA256, ignoreCase = true)
        }
        require(trusted) { "UPDATE_APK_SIGNER" }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
