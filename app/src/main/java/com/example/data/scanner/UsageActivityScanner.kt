package com.example.data.scanner

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

enum class UsageTimeWindow(val durationMillis: Long) {
    FIFTEEN_MINUTES(15L * 60L * 1_000L),
    ONE_HOUR(60L * 60L * 1_000L),
    TWENTY_FOUR_HOURS(24L * 60L * 60L * 1_000L)
}

data class RecentAppUsage(
    val packageName: String,
    val lastUsedMillis: Long,
    val totalForegroundMillis: Long,
    val hasActiveForegroundService: Boolean
)

data class ActiveForegroundServiceInfo(
    val packageName: String,
    val serviceClassName: String?
)

data class UsageActivitySnapshot(
    val hasUsagePermission: Boolean = false,
    val window: UsageTimeWindow = UsageTimeWindow.ONE_HOUR,
    val recentApps: List<RecentAppUsage> = emptyList(),
    val activeForegroundServices: List<ActiveForegroundServiceInfo> = emptyList(),
    val queriedAtMillis: Long = 0L
)

interface UsageActivityScanner {
    fun hasUsageAccessPermission(): Boolean
    fun queryRecentActivity(window: UsageTimeWindow, nowMillis: Long = System.currentTimeMillis()): UsageActivitySnapshot
    fun queryLastUsedTimeMillis(packageName: String, nowMillis: Long = System.currentTimeMillis()): Long?
}

class DeviceUsageActivityScanner(
    private val context: Context
) : UsageActivityScanner {

    override fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
        } catch (_: Exception) {
            return false
        }

        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    override fun queryRecentActivity(
        window: UsageTimeWindow,
        nowMillis: Long
    ): UsageActivitySnapshot {
        val granted = hasUsageAccessPermission()
        val runningServices = queryAllowedRunningForegroundServices()

        if (!granted) {
            return UsageActivitySnapshot(
                hasUsagePermission = false,
                window = window,
                recentApps = emptyList(),
                activeForegroundServices = runningServices,
                queriedAtMillis = nowMillis
            )
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return UsageActivitySnapshot(
                hasUsagePermission = true,
                window = window,
                recentApps = emptyList(),
                activeForegroundServices = runningServices,
                queriedAtMillis = nowMillis
            )

        val startTime = (nowMillis - window.durationMillis).coerceAtLeast(0L)

        // Track exact foreground sessions & foreground service starts via UsageEvents
        val lastUsedByPackage = mutableMapOf<String, Long>()
        val foregroundMillisByPackage = mutableMapOf<String, Long>()
        val openSessionStart = mutableMapOf<String, Long>()
        val activeFgServicePackages = mutableMapOf<String, String?>()

        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, nowMillis)
            val event = UsageEvents.Event()
            while (usageEvents != null && usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val ts = event.timeStamp

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        openSessionStart[pkg] = ts
                        val prev = lastUsedByPackage[pkg] ?: 0L
                        if (ts > prev) lastUsedByPackage[pkg] = ts
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val prev = lastUsedByPackage[pkg] ?: 0L
                        if (ts > prev) lastUsedByPackage[pkg] = ts
                        val sessionStart = openSessionStart.remove(pkg)
                        if (sessionStart != null && ts >= sessionStart) {
                            foregroundMillisByPackage[pkg] =
                                (foregroundMillisByPackage[pkg] ?: 0L) + (ts - sessionStart)
                        }
                    }

                    UsageEvents.Event.FOREGROUND_SERVICE_START -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            activeFgServicePackages[pkg] = event.className
                            val prev = lastUsedByPackage[pkg] ?: 0L
                            if (ts > prev) lastUsedByPackage[pkg] = ts
                        }
                    }

                    UsageEvents.Event.FOREGROUND_SERVICE_STOP -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            activeFgServicePackages.remove(pkg)
                        }
                    }
                }
            }

            // Close any activity still currently in foreground at nowMillis
            for ((pkg, sessionStart) in openSessionStart) {
                if (nowMillis >= sessionStart) {
                    foregroundMillisByPackage[pkg] =
                        (foregroundMillisByPackage[pkg] ?: 0L) + (nowMillis - sessionStart)
                    val prev = lastUsedByPackage[pkg] ?: 0L
                    if (nowMillis > prev) lastUsedByPackage[pkg] = nowMillis
                }
            }
        } catch (_: Exception) {
            // Fall through to queryUsageStats fallback
        }

        // Supplement with queryUsageStats for apps already open across the start boundary
        try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                nowMillis
            )
            stats?.forEach { stat ->
                val pkg = stat.packageName ?: return@forEach
                if (stat.lastTimeUsed in startTime..nowMillis) {
                    val prev = lastUsedByPackage[pkg] ?: 0L
                    if (stat.lastTimeUsed > prev) {
                        lastUsedByPackage[pkg] = stat.lastTimeUsed
                    }
                    if (!foregroundMillisByPackage.containsKey(pkg) && stat.totalTimeInForeground > 0L) {
                        foregroundMillisByPackage[pkg] =
                            stat.totalTimeInForeground.coerceAtMost(window.durationMillis)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore
        }

        // Merge foreground services reported by ActivityManager with those observed via UsageEvents
        val mergedFgServices = linkedMapOf<String, ActiveForegroundServiceInfo>()
        runningServices.forEach { svc ->
            mergedFgServices[svc.packageName] = svc
        }
        activeFgServicePackages.forEach { (pkg, className) ->
            mergedFgServices.putIfAbsent(pkg, ActiveForegroundServiceInfo(pkg, className))
        }

        val recentApps = lastUsedByPackage.entries
            .map { (pkg, lastUsed) ->
                RecentAppUsage(
                    packageName = pkg,
                    lastUsedMillis = lastUsed,
                    totalForegroundMillis = foregroundMillisByPackage[pkg] ?: 0L,
                    hasActiveForegroundService = mergedFgServices.containsKey(pkg)
                )
            }
            .sortedByDescending { it.lastUsedMillis }

        return UsageActivitySnapshot(
            hasUsagePermission = true,
            window = window,
            recentApps = recentApps,
            activeForegroundServices = mergedFgServices.values.toList(),
            queriedAtMillis = nowMillis
        )
    }

    override fun queryLastUsedTimeMillis(packageName: String, nowMillis: Long): Long? {
        if (!hasUsageAccessPermission()) return null
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val thirtyDaysAgo = (nowMillis - 30L * 24L * 60L * 60L * 1_000L).coerceAtLeast(0L)
        return try {
            val aggregated = usageStatsManager.queryAndAggregateUsageStats(thirtyDaysAgo, nowMillis)
            aggregated[packageName]?.lastTimeUsed?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun queryAllowedRunningForegroundServices(): List<ActiveForegroundServiceInfo> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return emptyList()
            am.getRunningServices(Int.MAX_VALUE)
                .orEmpty()
                .filter { it.foreground }
                .mapNotNull { info ->
                    val pkg = info.service?.packageName ?: return@mapNotNull null
                    ActiveForegroundServiceInfo(
                        packageName = pkg,
                        serviceClassName = info.service?.shortClassName
                    )
                }
                .distinctBy { it.packageName }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
