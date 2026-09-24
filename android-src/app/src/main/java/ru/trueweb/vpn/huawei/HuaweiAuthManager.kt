package ru.trueweb.vpn.huawei

import android.app.Activity
import android.content.Intent
import com.huawei.hms.common.ApiException
import com.huawei.hms.support.account.request.AccountAuthParams
import com.huawei.hms.support.account.request.AccountAuthParamsHelper
import com.huawei.hms.support.account.AccountAuthManager

object HuaweiAuthManager {
    const val REQUEST_CODE_SIGN_IN = 9107

    fun signInIntent(activity: Activity): Intent {
        val params = AccountAuthParamsHelper(AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
            .setAuthorizationCode()
            .createParams()
        return AccountAuthManager.getService(activity, params).signInIntent
    }

    fun parseAuthorizationCode(data: Intent?, resultCode: Int): Result<String> = runCatching {
        if (data == null) {
            error("Huawei sign-in failed [resultCode=$resultCode, data=null]")
        }

        val task = AccountAuthManager.parseAuthResultFromIntent(data)
        if (!task.isSuccessful) {
            val cause = task.exception
            val apiCode = (cause as? ApiException)?.statusCode
            val exceptionType = cause?.javaClass?.name ?: "null"
            val extraKeys = data.extras?.keySet()?.sorted()?.joinToString(",")?.take(180).orEmpty()

            val detail = buildString {
                append("Huawei sign-in failed [resultCode=").append(resultCode)
                append(", apiCode=").append(apiCode ?: "null")
                append(", exception=").append(exceptionType)
                append(", extras=").append(if (extraKeys.isBlank()) "none" else extraKeys)
                append("]")
                cause?.message?.takeIf { it.isNotBlank() }?.let {
                    append(": ").append(it.take(180))
                }
            }
            throw IllegalStateException(detail, cause)
        }

        val account = task.result ?: error("Huawei account result is empty [resultCode=$resultCode]")
        account.authorizationCode?.takeIf { it.isNotBlank() }
            ?: error("Huawei authorization code is empty [resultCode=$resultCode]")
    }
}
