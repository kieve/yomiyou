package ca.kieve.yomiyou.kavita

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ca.kieve.yomiyou.home.services
import java.util.concurrent.TimeUnit

/** Sends the places queued for Kavita once there's a network, trying again later while Kavita can't be reached. */
class KavitaSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.i(KavitaBooks.SYNC, "worker: running (attempt ${runAttemptCount + 1})")
        return if (applicationContext.services.kavita.sendQueued()) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "kavita-positions"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<KavitaSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
            Log.i(KavitaBooks.SYNC, "worker: scheduled, waiting for a network")
        }
    }
}
