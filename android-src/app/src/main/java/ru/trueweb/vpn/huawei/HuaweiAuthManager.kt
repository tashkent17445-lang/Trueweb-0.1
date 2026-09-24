package ru.trueweb.vpn.huawei

import android.app.Activity
import android.content.Intent
import com.huawei.hms.common.ApiException
import com.huawei.hms.support.account.request.AccountAuthParams
import com.huawei.hms.support.account.request.AccountAuthParamsHelper
import com.huawei.hms.support.account.AccountAuthManager

object HuaweiAuthManager {
    fun signInIntent(activity: Activity): Intent {
        val params = AccountAuthParamsHelper(AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
            .setAuthorizationCode()
            .createParams()
        return AccountAuthManager.getService(activity, params).signInIntent
    }

    fun parseAuthorizationCode(data: Intent?): Result<String> = runCatching {
        requireNotNull(data) { "Huawei sign-in returned no data" }
        val task = AccountAuthManager.parseAuthResultFromIntent(data)
        if (!task.isSuccessful) {
            val cause = task.exception
            val code = (cause as? ApiException)?.statusCode
            val detail = buildString {
                append("Huawei sign-in failed")
                if (code != null) append(" (code=").append(code).append(")")
                cause?.message?.takeIf { it.isNotBlank() }?.let {
                    append(": ").append(it)
                }
            }
            throw IllegalStateException(detail, cause)
        }
        val account = task.result ?: error("Huawei account result is empty")
        account.authorizationCode?.takeIf { it.isNotBlank() }
            ?: error("Huawei authorization code is empty")
    }
}
