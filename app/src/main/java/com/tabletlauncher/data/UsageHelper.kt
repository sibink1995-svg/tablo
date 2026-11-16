package com.tabletlauncher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** Optional "Usage access" support: improves suggestions with real screen-time data. */
object UsageHelper {

    @Suppress("DEPRECATION")
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground minutes per package over the last 7 days. */
    fun minutesLastWeek(ctx: Context): Map<String, Double> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 7L * 24 * 60 * 60 * 1000
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: return emptyMap()
        return stats
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { it.totalTimeInForeground } / 60_000.0 }
    }
}
