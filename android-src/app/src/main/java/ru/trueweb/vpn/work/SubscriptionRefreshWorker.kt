package ru.trueweb.vpn.work

import android.content.Context
import androidx.work.*
import ru.trueweb.vpn.api.TrueWebApi
import ru.trueweb.vpn.auth.SessionStore
import ru.trueweb.vpn.store.DeviceIdentity
import ru.trueweb.vpn.store.ServerStore
import ru.trueweb.vpn.store.RoutingStore
import java.util.concurrent.TimeUnit

class SubscriptionRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val token = SessionStore(applicationContext).accessToken ?: return Result.success()
        val routingStore = RoutingStore(applicationContext)
        if (routingStore.isStale()) {
            TrueWebApi.routing(token).onSuccess { routingStore.save(it) }
        }
        val result = TrueWebApi.servers(token, DeviceIdentity(applicationContext))
        return result.fold(
            onSuccess = {
                ServerStore(applicationContext).saveCatalog(it)
                Result.success()
            },
            onFailure = { error ->
                val message = error.message ?: ""
                if (message.contains("HTTP 401")) {
                    SessionStore(applicationContext).clear()
                    Result.success()
                } else if (message.contains("HTTP 403")) {
                    // Device limit is permanent until the user frees/buys a slot.
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        )
    }

    companion object {
        private const val UNIQUE_NAME = "trueweb_subscription_refresh"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
