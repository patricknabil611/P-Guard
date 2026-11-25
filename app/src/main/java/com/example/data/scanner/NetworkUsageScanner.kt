package com.example.data.scanner

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build

enum class DataUsageWindow(val durationMillis: Long) {
    TWENTY_FOUR_HOURS(24L * 60L * 60L * 1_000L),
    SEVEN_DAYS(7L * 24L * 60L * 60L * 1_000L)
}

data class AppBackgroundDataUsage(
    val packageName: String,
    val mobileBackgroundBytes: Long,
    val wifiBackgroundBytes: Long
) {
    val totalBackgroundBytes: Long
        get() = mobileBackgroundBytes + wifiBackgroundBytes
}

data class NetworkUsageSnapshot(
    val hasUsagePermission: Boolean = false,
    val window: DataUsageWindow = DataUsageWindow.TWENTY_FOUR_HOURS,
    val apps: List<AppBackgroundDataUsage> = emptyList(),
    val queriedAtMillis: Long = 0L
)

interface NetworkUsageScanner {
    fun queryBackgroundDataUsage(
        window: DataUsageWindow,
        nowMillis: Long = System.currentTimeMillis()
    ): NetworkUsageSnapshot

    fun queryBackgroundDataForPackage(
        packageName: String,
        window: DataUsageWindow = DataUsageWindow.TWENTY_FOUR_HOURS,
        nowMillis: Long = System.currentTimeMillis()
    ): AppBackgroundDataUsage?

    companion object {
        const val HIGH_BACKGROUND_DATA_24H_BYTES: Long = 50L * 1024L * 1024L // 50 MB
    }
}

class DeviceNetworkUsageScanner(
    private val context: Context,
    private val usageActivityScanner: UsageActivityScanner
) : NetworkUsageScanner {

    override fun queryBackgroundDataUsage(
        window: DataUsageWindow,
        nowMillis: Long
    ): NetworkUsageSnapshot {
        val hasPermission = usageActivityScanner.hasUsageAccessPermission()
        if (!hasPermission) {
            return NetworkUsageSnapshot(
                hasUsagePermission = false,
                window = window,
                apps = emptyList(),
                queriedAtMillis = nowMillis
            )
        }

        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return NetworkUsageSnapshot(
                hasUsagePermission = true,
                window = window,
                apps = emptyList(),
                queriedAtMillis = nowMillis
            )

        val startTime = (nowMillis - window.durationMillis).coerceAtLeast(0L)
        val mobileByUid = querySummaryByUid(
            networkStatsManager = networkStatsManager,
            networkType = ConnectivityManager.TYPE_MOBILE,
            subscriberId = null,
            startTime = startTime,
            endTime = nowMillis
        )
        val wifiByUid = querySummaryByUid(
            networkStatsManager = networkStatsManager,
            networkType = ConnectivityManager.TYPE_WIFI,
            subscriberId = "",
            startTime = startTime,
            endTime = nowMillis
        )

        val uidToPackages = buildUidToPackageMap()
        val allUids = mobileByUid.keys + wifiByUid.keys

        val perPackageMobile = mutableMapOf<String, Long>()
        val perPackageWifi = mutableMapOf<String, Long>()

        for (uid in allUids) {
            val mobile = mobileByUid[uid] ?: 0L
            val wifi = wifiByUid[uid] ?: 0L
            if (mobile + wifi <= 0L) continue

            val packages = uidToPackages[uid]
                ?: resolvePackagesForUidFallback(uid)
            val primaryPkg = packages.firstOrNull() ?: continue

            perPackageMobile[primaryPkg] = (perPackageMobile[primaryPkg] ?: 0L) + mobile
            perPackageWifi[primaryPkg] = (perPackageWifi[primaryPkg] ?: 0L) + wifi
        }

        val allPackages = perPackageMobile.keys + perPackageWifi.keys
        val results = allPackages.map { pkg ->
            AppBackgroundDataUsage(
                packageName = pkg,
                mobileBackgroundBytes = perPackageMobile[pkg] ?: 0L,
                wifiBackgroundBytes = perPackageWifi[pkg] ?: 0L
            )
        }
            .filter { it.totalBackgroundBytes > 0L }
            .sortedByDescending { it.totalBackgroundBytes }

        return NetworkUsageSnapshot(
            hasUsagePermission = true,
            window = window,
            apps = results,
            queriedAtMillis = nowMillis
        )
    }

    override fun queryBackgroundDataForPackage(
        packageName: String,
        window: DataUsageWindow,
        nowMillis: Long
    ): AppBackgroundDataUsage? {
        if (!usageActivityScanner.hasUsageAccessPermission()) return null
        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return null

        val uid = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                ).uid
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0).uid
            }
        } catch (_: Exception) {
            return null
        }

        val startTime = (nowMillis - window.durationMillis).coerceAtLeast(0L)
        val mobile = queryUidBackgroundBytes(
            networkStatsManager,
            ConnectivityManager.TYPE_MOBILE,
            null,
            uid,
            startTime,
            nowMillis
        )
        val wifi = queryUidBackgroundBytes(
            networkStatsManager,
            ConnectivityManager.TYPE_WIFI,
            "",
            uid,
            startTime,
            nowMillis
        )
        return AppBackgroundDataUsage(
            packageName = packageName,
            mobileBackgroundBytes = mobile,
            wifiBackgroundBytes = wifi
        )
    }

    @Suppress("DEPRECATION")
    private fun querySummaryByUid(
        networkStatsManager: NetworkStatsManager,
        networkType: Int,
        subscriberId: String?,
        startTime: Long,
        endTime: Long
    ): Map<Int, Long> {
        val result = mutableMapOf<Int, Long>()
        var stats: NetworkStats? = null
        try {
            stats = networkStatsManager.querySummary(networkType, subscriberId, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats != null && stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                // STATE_DEFAULT tracks background network usage (non-foreground)
                if (bucket.state == NetworkStats.Bucket.STATE_DEFAULT) {
                    val bytes = (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
                    if (bytes > 0L) {
                        result[bucket.uid] = (result[bucket.uid] ?: 0L) + bytes
                    }
                }
            }
        } catch (_: Exception) {
            // Some OEMs/Android versions may restrict mobile summary without carrier/subscriber privileges
        } finally {
            try {
                stats?.close()
            } catch (_: Exception) {
                // Ignore
            }
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun queryUidBackgroundBytes(
        networkStatsManager: NetworkStatsManager,
        networkType: Int,
        subscriberId: String?,
        uid: Int,
        startTime: Long,
        endTime: Long
    ): Long {
        var total = 0L
        var stats: NetworkStats? = null
        try {
            stats = networkStatsManager.queryDetailsForUidTagState(
                networkType,
                subscriberId,
                startTime,
                endTime,
                uid,
                NetworkStats.Bucket.TAG_NONE,
                NetworkStats.Bucket.STATE_DEFAULT
            )
            val bucket = NetworkStats.Bucket()
            while (stats != null && stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                total += (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
            }
        } catch (_: Exception) {
            // Ignore restricted transport queries
        } finally {
            try {
                stats?.close()
            } catch (_: Exception) {
                // Ignore
            }
        }
        return total
    }

    private fun buildUidToPackageMap(): Map<Int, List<String>> {
        return try {
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstalledApplications(0)
            }
            apps.groupBy({ it.uid }, { it.packageName })
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun resolvePackagesForUidFallback(uid: Int): List<String> {
        return try {
            context.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
