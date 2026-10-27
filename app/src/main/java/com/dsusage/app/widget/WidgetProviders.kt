package com.dsusage.app.widget

import com.dsusage.app.R

class CompactUsageWidgetProvider : BaseUsageWidgetProvider() {
    override val layoutId: Int = R.layout.widget_compact
    override val kind: WidgetKind = WidgetKind.COMPACT
}

class StandardUsageWidgetProvider : BaseUsageWidgetProvider() {
    override val layoutId: Int = R.layout.widget_standard
    override val kind: WidgetKind = WidgetKind.STANDARD
}

class DashboardUsageWidgetProvider : BaseUsageWidgetProvider() {
    override val layoutId: Int = R.layout.widget_dashboard
    override val kind: WidgetKind = WidgetKind.DASHBOARD
}

