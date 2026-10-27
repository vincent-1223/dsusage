package com.dsusage.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UsageSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return runCatching {
            UsageSyncManager.sync(applicationContext)
            Result.success()
        }.getOrElse { error ->
            UsageSyncManager.handleSyncError(applicationContext, error)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
