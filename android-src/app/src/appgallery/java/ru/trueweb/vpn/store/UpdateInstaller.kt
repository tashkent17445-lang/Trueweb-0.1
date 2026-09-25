package ru.trueweb.vpn.store

import android.content.Context
import android.content.Intent
import java.io.File

object UpdateInstaller {
    fun canInstallPackages(context: Context): Boolean = false
    fun permissionIntent(context: Context): Intent? = null
    fun install(context: Context, apk: File): Result<Unit> =
        Result.failure(UnsupportedOperationException("Self-update is disabled in AppGallery build"))
}
