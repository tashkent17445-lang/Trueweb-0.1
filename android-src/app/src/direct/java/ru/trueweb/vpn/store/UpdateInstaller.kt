package ru.trueweb.vpn.store

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

object UpdateInstaller {
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
        verifyUpdatePackage(context, apk).getOrThrow()

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

    /**
     * SHA-256 from latest.json protects the downloaded bytes. This check adds a
     * second trust boundary: the downloaded APK must also be the TrueWeb package
     * and be signed by the same signing certificate as the already-installed app.
     */
    fun verifyUpdatePackage(context: Context, apk: File): Result<Unit> = runCatching {
        require(apk.isFile && apk.length() > 0L) { "UPDATE_APK_MISSING" }

        val packageManager = context.packageManager
        val archiveInfo = getArchivePackageInfo(packageManager, apk)
            ?: error("UPDATE_APK_PACKAGE_INFO")

        require(archiveInfo.packageName == context.packageName) {
            "UPDATE_APK_PACKAGE_NAME"
        }

        val installedInfo = getInstalledPackageInfo(packageManager, context.packageName)
        val installedSigners = signerDigests(installedInfo)
        val archiveSigners = signerDigests(archiveInfo)

        require(installedSigners.isNotEmpty()) { "UPDATE_INSTALLED_SIGNER_MISSING" }
        require(archiveSigners.isNotEmpty()) { "UPDATE_APK_SIGNER_MISSING" }
        require(installedSigners == archiveSigners) { "UPDATE_APK_SIGNER_MISMATCH" }
    }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(packageManager: PackageManager, apk: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return packageManager.getPackageInfo(packageName, flags)
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners?.toList().orEmpty()
            } else {
                signingInfo.signingCertificateHistory?.toList().orEmpty()
            }
        } else {
            info.signatures?.toList().orEmpty()
        }

        return signatures
            .map { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }
            .toSet()
    }
}
