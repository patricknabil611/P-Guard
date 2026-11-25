package com.example.data.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

enum class RootCheckTrigger {
    SU_BINARY_FOUND,
    ROOT_MANAGER_APP_INSTALLED,
    TEST_KEYS_BUILD_TAGS,
    WRITABLE_SYSTEM_PARTITION
}

data class RootDetectionResult(
    val isRooted: Boolean = false,
    val triggeredChecks: List<RootCheckTrigger> = emptyList(),
    val details: List<String> = emptyList()
)

interface RootDetector {
    fun detectRoot(): RootDetectionResult
}

class DeviceRootDetector(
    private val context: Context
) : RootDetector {

    override fun detectRoot(): RootDetectionResult {
        val triggers = mutableListOf<RootCheckTrigger>()
        val details = mutableListOf<String>()

        // 1. su binary in common paths
        val foundSuPaths = SU_BINARY_PATHS.filter { path ->
            try {
                File(path).exists()
            } catch (_: Exception) {
                false
            }
        }
        if (foundSuPaths.isNotEmpty()) {
            triggers += RootCheckTrigger.SU_BINARY_FOUND
            details += foundSuPaths.first()
        }

        // 2. Known root management packages (Magisk, SuperSU, KingRoot, etc.)
        val installedRootApps = ROOT_MANAGER_PACKAGES.filter { pkg -> isPackageInstalled(pkg) }
        if (installedRootApps.isNotEmpty()) {
            triggers += RootCheckTrigger.ROOT_MANAGER_APP_INSTALLED
            details += installedRootApps.joinToString(", ")
        }

        // 3. test-keys in Build.TAGS
        val buildTags = Build.TAGS.orEmpty()
        if (buildTags.contains("test-keys", ignoreCase = true)) {
            triggers += RootCheckTrigger.TEST_KEYS_BUILD_TAGS
            details += "Build.TAGS=$buildTags"
        }

        // 4. Writable /system partition
        if (isSystemPartitionWritable()) {
            triggers += RootCheckTrigger.WRITABLE_SYSTEM_PARTITION
            details += "/system mounted rw"
        }

        return RootDetectionResult(
            isRooted = triggers.isNotEmpty(),
            triggeredChecks = triggers,
            details = details
        )
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isSystemPartitionWritable(): Boolean {
        try {
            if (File("/system").canWrite()) return true
        } catch (_: Exception) {
            // Ignore
        }

        return try {
            val mounts = File("/proc/mounts")
            if (!mounts.canRead()) return false
            mounts.useLines { lines ->
                lines.any { line ->
                    val parts = line.split(' ')
                    if (parts.size < 4) return@any false
                    val mountPoint = parts[1]
                    val options = parts[3].split(',')
                    (mountPoint == "/system" || mountPoint == "/system_root") && "rw" in options
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private val SU_BINARY_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/system/sd/xbin/su",
            "/system/app/Superuser.apk",
            "/vendor/bin/su",
            "/product/bin/su"
        )

        private val ROOT_MANAGER_PACKAGES = listOf(
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
            "io.github.huskydg.magisk",
            "eu.chainfire.supersu",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.koushikdutta.superuser",
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "me.weishu.kernelsu",
            "me.bmax.apatch"
        )
    }
}
