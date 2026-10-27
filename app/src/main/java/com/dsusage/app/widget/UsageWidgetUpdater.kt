package com.dsusage.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object UsageWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(
            CompactUsageWidgetProvider::class.java,
            StandardUsageWidgetProvider::class.java,
            DashboardUsageWidgetProvider::class.java
        ).forEach { providerClass ->
            val component = ComponentName(context, providerClass)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = android.content.Intent(
                    AppWidgetManager.ACTION_APPWIDGET_UPDATE
                ).apply {
                    setComponent(component)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}

