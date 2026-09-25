package ru.trueweb.vpn.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import ru.trueweb.vpn.store.GeoDataManager
import java.util.concurrent.TimeUnit

class GeoDataRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val result = GeoDataManager.updateNow(applicationContext, force = false)
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC_NAME = "trueweb_geodata_refresh_72h"
        private const val OPPORTUNISTIC_NAME = "trueweb_geodata_refresh_if_stale"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GeoDataRefreshWorker>(
                72, TimeUnit.HOURS,
                12, TimeUnit.HOURS
            )
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** Queue a cheap stale-check after a successful VPN connection/app resume. */
        fun refreshIfStale(context: Context) {
            if (!GeoDataManager.isStale(context)) return
            val request = OneTimeWorkRequestBuilder<GeoDataRefreshWorker>()
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                OPPORTUNISTIC_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
