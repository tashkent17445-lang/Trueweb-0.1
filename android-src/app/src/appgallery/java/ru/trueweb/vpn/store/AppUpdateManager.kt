package ru.trueweb.vpn.store

import android.content.Context
import java.io.File

object AppUpdateManager {
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val sha256: String,
        val notes: String
    )

    fun check(context: Context, force: Boolean = false): Result<UpdateInfo?> =
        Result.success(null)

    fun download(context: Context, update: UpdateInfo): Result<File> =
        Result.failure(UnsupportedOperationException("Self-update is disabled in AppGallery build"))
}
