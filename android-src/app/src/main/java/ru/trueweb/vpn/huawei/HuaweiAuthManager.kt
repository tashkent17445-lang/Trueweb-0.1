package ru.trueweb.vpn.huawei

import android.app.Activity
import android.content.Intent
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
            throw task.exception ?: IllegalStateException("Huawei sign-in failed")
        }
        val account = task.result ?: error("Huawei account result is empty")
        account.authorizationCode?.takeIf { it.isNotBlank() }
            ?: error("Huawei authorization code is empty")
    }
}
