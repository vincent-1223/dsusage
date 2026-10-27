package com.dsusage.app

import android.app.Application
import com.dsusage.app.sync.UsageSyncScheduler

class DsUsageApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UsageSyncScheduler.ensurePeriodic(this)
    }
}

