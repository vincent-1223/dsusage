package com.dsusage.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dsusage.app.MainActivity
import com.dsusage.app.R
import com.dsusage.app.data.UsageSnapshot
import com.dsusage.app.data.UsageStore
import com.dsusage.app.sync.UsageSyncScheduler
import com.dsusage.app.util.Formatters

abstract class BaseUsageWidgetProvider : AppWidgetProvider() {
    abstract val layoutId: Int
    abstract val kind: WidgetKind

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = UsageStore(context).load()
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(
                appWidgetId,
                buildViews(context, snapshot, appWidgetId)
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            UsageSyncScheduler.enqueueNow(context)
        }
    }

    private fun buildViews(
        context: Context,
        snapshot: UsageSnapshot,
        appWidgetId: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        val hasToken = com.dsusage.app.data.SecureTokenStore(context).hasToken()
        val status = when {
            !hasToken && !snapshot.hasData -> "等待登录"
            !hasToken && snapshot.hasData -> "缓存数据"
            snapshot.sessionExpired -> "登录已过期"
            snapshot.error != null && snapshot.hasData -> "同步异常"
            snapshot.error != null -> snapshot.error.take(12)
            else -> "已同步"
        }
        val isHealthy = snapshot.error == null && hasToken

        when (kind) {
            WidgetKind.COMPACT -> {
                views.setTextViewText(
                    R.id.compact_balance,
                    if (snapshot.hasData) Formatters.money(snapshot.balance, snapshot.currency) else "--"
                )
                views.setTextViewText(
                    R.id.compact_cost,
                    if (snapshot.hasData) {
                        "今日 ${Formatters.money(snapshot.todayCost, snapshot.currency)}"
                    } else {
                        "今日 --"
                    }
                )
                views.setTextViewText(R.id.compact_status, status)
                val size = context.resources.displayMetrics
                views.setImageViewBitmap(
                    R.id.compact_spark,
                    TrendRender.renderBars(
                        snapshot,
                        (size.density * 72).toInt(),
                        (size.density * 26).toInt(),
                        isNightMode(context)
                    )
                )
            }

            WidgetKind.STANDARD -> {
                views.setTextViewText(
                    R.id.standard_balance,
                    if (snapshot.hasData) Formatters.money(snapshot.balance, snapshot.currency) else "--"
                )
                views.setTextViewText(
                    R.id.standard_today_cost,
                    if (snapshot.hasData) Formatters.money(snapshot.todayCost, snapshot.currency) else "--"
                )
                views.setTextViewText(
                    R.id.standard_requests,
                    if (snapshot.hasData) Formatters.integer(snapshot.requestCount) else "--"
                )
                views.setTextViewText(R.id.standard_status, status)
                views.setTextViewText(R.id.standard_updated, Formatters.updated(snapshot.updatedAt))
                val size = context.resources.displayMetrics
                views.setImageViewBitmap(
                    R.id.standard_spark,
                    TrendRender.renderBars(
                        snapshot,
                        (size.density * 188).toInt(),
                        (size.density * 34).toInt(),
                        isNightMode(context)
                    )
                )
            }

            WidgetKind.DASHBOARD -> {
                views.setTextViewText(
                    R.id.dashboard_balance,
                    if (snapshot.hasData) Formatters.money(snapshot.balance, snapshot.currency) else "--"
                )
                views.setTextViewText(
                    R.id.dashboard_today_cost,
                    if (snapshot.hasData) Formatters.money(snapshot.todayCost, snapshot.currency) else "--"
                )
                views.setTextViewText(
                    R.id.dashboard_requests,
                    if (snapshot.hasData) Formatters.integer(snapshot.requestCount) else "--"
                )
                views.setTextViewText(
                    R.id.dashboard_tokens,
                    if (snapshot.hasData) Formatters.tokens(snapshot.tokenCount) else "--"
                )
                views.setTextViewText(R.id.dashboard_status, status)
                views.setTextViewText(R.id.dashboard_updated, Formatters.updated(snapshot.updatedAt))
                views.setTextViewText(
                    R.id.dashboard_month_cost,
                    if (snapshot.hasData) {
                        "累计 ${Formatters.money(snapshot.totalCost, snapshot.currency)}"
                    } else {
                        "累计 --"
                    }
                )
                val size = context.resources.displayMetrics
                views.setImageViewBitmap(
                    R.id.dashboard_chart,
                    TrendRender.renderBars(
                        snapshot,
                        (size.density * 286).toInt(),
                        (size.density * 84).toInt(),
                        isNightMode(context),
                        line = true
                    )
                )
            }
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)

        val refreshIntent = Intent(context, this::class.java).setAction(ACTION_REFRESH)
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 10_000,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val refreshViewId = when (kind) {
            WidgetKind.COMPACT -> R.id.compact_refresh
            WidgetKind.STANDARD -> R.id.standard_refresh
            WidgetKind.DASHBOARD -> R.id.dashboard_refresh
        }
        views.setOnClickPendingIntent(refreshViewId, refreshPendingIntent)

        val statusId = when (kind) {
            WidgetKind.COMPACT -> R.id.compact_status
            WidgetKind.STANDARD -> R.id.standard_status
            WidgetKind.DASHBOARD -> R.id.dashboard_status
        }
        views.setTextColor(
            statusId,
            context.getColor(if (isHealthy) R.color.widget_positive else R.color.widget_warning)
        )
        return views
    }

    private fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        const val ACTION_REFRESH = "com.dsusage.app.action.REFRESH_WIDGET"
    }
}

