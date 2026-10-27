package com.dsusage.app.sync

import android.content.Context
import com.dsusage.app.data.SecureTokenStore
import com.dsusage.app.data.UsageSnapshot
import com.dsusage.app.data.UsageStore
import com.dsusage.app.network.DeepSeekUsageClient
import com.dsusage.app.network.SessionExpiredException
import com.dsusage.app.widget.UsageWidgetUpdater

class LoginRequiredException : IllegalStateException("请先登录 DeepSeek")

object UsageSyncManager {
    private val client = DeepSeekUsageClient()

    suspend fun sync(context: Context): UsageSnapshot {
        val appContext = context.applicationContext
        val token = SecureTokenStore(appContext).get()
            ?: throw LoginRequiredException()
        val snapshot = client.fetchUsage(token)
        UsageStore(appContext).save(snapshot)
        UsageWidgetUpdater.updateAll(appContext)
        return snapshot
    }

    suspend fun verifySession(context: Context, token: String): Boolean {
        return client.verifySession(token)
    }

    fun handleSyncError(context: Context, error: Throwable) {
        val appContext = context.applicationContext
        val expired = error is SessionExpiredException
        val message = when (error) {
            is LoginRequiredException -> "请先登录 DeepSeek"
            is SessionExpiredException -> "登录已过期，请重新登录"
            else -> error.message ?: "同步失败，请稍后重试"
        }
        UsageStore(appContext).markError(message, expired)
        UsageWidgetUpdater.updateAll(appContext)
    }
}

