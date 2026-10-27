package com.dsusage.app.data

import android.content.Context

class UsageStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): UsageSnapshot {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return UsageSnapshot()
        return UsageSnapshot.fromJson(raw)
    }

    fun save(snapshot: UsageSnapshot) {
        prefs.edit().putString(KEY_SNAPSHOT, snapshot.toJson()).apply()
    }

    fun markError(message: String, sessionExpired: Boolean = false): UsageSnapshot {
        val snapshot = load().copy(
            error = message,
            sessionExpired = sessionExpired
        )
        save(snapshot)
        return snapshot
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "usage_cache"
        const val KEY_SNAPSHOT = "latest_snapshot"
    }
}
