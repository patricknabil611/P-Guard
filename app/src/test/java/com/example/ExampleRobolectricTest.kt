package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PGuardDatabase
import com.example.data.repository.SecurityScanRepository
import com.example.data.scanner.PackageScanner
import com.example.data.scanner.RiskLevel
import com.example.data.scanner.RiskyPermission
import com.example.data.scanner.ScannedPackageInfo
import com.example.data.scanner.SuspiciousReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: PGuardDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name matches P Guard`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("P Guard", appName)
    }

    @Test
    fun `baseline scan records snapshot and subsequent scan detects newly installed apps`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var installed = listOf(
            fakePkg("com.example.base", "Base App", isSystem = false),
            fakePkg("com.android.systemui", "System UI", isSystem = true)
        )
        val fakeScanner = object : PackageScanner {
            override val ownPackageName: String = "com.aistudio.pguard.vkzrmn"
            override fun scanInstalledPackages(): List<ScannedPackageInfo> = installed
        }

        val repository = SecurityScanRepository(
            scanDao = database.scanDao(),
            packageScanner = fakeScanner,
            ioDispatcher = dispatcher
        )

        // First scan: baseline (0 new apps flagged)
        repository.runScan(force = true)
        val firstScan = repository.latestScan.first()
        assertTrue(firstScan != null && firstScan.isBaseline)
        assertEquals(2, firstScan?.totalAppsCount)
        assertEquals(0, repository.unreviewedNewApps.first().size)

        // Second scan: a new sideloaded app appears
        installed = installed + fakePkg("com.new.utility", "New Utility", isSystem = false)
        repository.runScan(force = true)

        val secondScan = repository.latestScan.first()
        assertFalse(secondScan!!.isBaseline)
        assertEquals(3, secondScan.totalAppsCount)
        assertEquals(1, secondScan.newAppsDetectedCount)

        val unreviewed = repository.unreviewedNewApps.first()
        assertEquals(1, unreviewed.size)
        assertEquals("com.new.utility", unreviewed.first().packageName)

        // Mark reviewed clears attention list
        repository.markAppReviewed("com.new.utility")
        assertEquals(0, repository.unreviewedNewApps.first().size)
        assertEquals(1, repository.reviewedOrRemovedApps.first().size)
    }

    @Test
    fun `suspicious evaluator flags spyware signals and respects trusted apps`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val now = System.currentTimeMillis()
        val oldInstallTime = now - (30L * 24 * 60 * 60 * 1_000)

        val installed = listOf(
            // Clean Galaxy Store app
            fakePkg(
                packageName = "com.samsung.notes",
                appName = "Galaxy Notes",
                isSystem = false,
                installer = "com.sec.android.app.samsungapps",
                installTime = oldInstallTime
            ),
            // High-risk hidden sideloaded app installed recently with 3+ permissions + accessibility
            fakePkg(
                packageName = "com.sketchy.tracker",
                appName = "System Update",
                isSystem = false,
                installer = null,
                installTime = now - (2L * 24 * 60 * 60 * 1_000),
                hasLauncher = false,
                riskyPerms = listOf(
                    RiskyPermission.CAMERA,
                    RiskyPermission.MICROPHONE,
                    RiskyPermission.SMS,
                    RiskyPermission.OVERLAY
                ),
                hasAccessibility = true
            ),
            // Low-risk sideloaded app (single mild signal)
            fakePkg(
                packageName = "com.sideload.simple",
                appName = "Simple Tool",
                isSystem = false,
                installer = "com.pstore.app",
                installTime = oldInstallTime
            )
        )

        val fakeScanner = object : PackageScanner {
            override val ownPackageName: String = "com.aistudio.pguard.vkzrmn"
            override fun scanInstalledPackages(): List<ScannedPackageInfo> = installed
        }

        val repository = SecurityScanRepository(
            scanDao = database.scanDao(),
            packageScanner = fakeScanner,
            ioDispatcher = dispatcher
        )

        repository.runScan(force = true)

        val assessments = repository.securityAssessments.first().associateBy { it.snapshot.packageName }

        // Galaxy Store app with no risky perms is clean
        val galaxyApp = assessments["com.samsung.notes"]!!
        assertFalse(galaxyApp.isSuspicious)
        assertEquals(RiskLevel.NONE, galaxyApp.riskLevel)

        // High-risk app triggers all expected reasons
        val sketchy = assessments["com.sketchy.tracker"]!!
        assertTrue(sketchy.isActiveWarning)
        assertEquals(RiskLevel.HIGH, sketchy.riskLevel)
        assertTrue(sketchy.reasons.any { it is SuspiciousReason.Sideloaded })
        assertTrue(sketchy.reasons.any { it is SuspiciousReason.RiskyPermissions })
        assertTrue(sketchy.reasons.any { it is SuspiciousReason.HiddenNoLauncher })
        assertTrue(sketchy.reasons.any { it is SuspiciousReason.AccessibilityEnabled })
        assertTrue(sketchy.reasons.any { it is SuspiciousReason.RecentWithManyPermissions })

        // Low-risk sideloaded app
        val simple = assessments["com.sideload.simple"]!!
        assertTrue(simple.isActiveWarning)
        assertEquals(RiskLevel.LOW, simple.riskLevel)

        // Mark simple tool as trusted -> excluded from active warnings
        repository.markAppTrusted("com.sideload.simple", "Simple Tool")
        val afterTrust = repository.securityAssessments.first().associateBy { it.snapshot.packageName }
        assertTrue(afterTrust["com.sideload.simple"]!!.isTrusted)
        assertFalse(afterTrust["com.sideload.simple"]!!.isActiveWarning)

        // Removing trust restores active warning
        repository.removeTrustedApp("com.sideload.simple")
        val afterUntrust = repository.securityAssessments.first().associateBy { it.snapshot.packageName }
        assertFalse(afterUntrust["com.sideload.simple"]!!.isTrusted)
        assertTrue(afterUntrust["com.sideload.simple"]!!.isActiveWarning)
    }

    @Test
    fun `dashboard attention count includes USB debugging warning and device status refreshes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var devStatus = com.example.data.scanner.DeveloperModeStatus(
            isDeveloperOptionsEnabled = true,
            isUsbDebuggingEnabled = true
        )
        var usageGranted = false

        val repo = com.example.data.repository.DeviceStatusRepository(
            developerModeReader = object : com.example.data.scanner.DeveloperModeReader {
                override fun readStatus() = devStatus
            },
            usageActivityScanner = object : com.example.data.scanner.UsageActivityScanner {
                override fun hasUsageAccessPermission(): Boolean = usageGranted
                override fun queryRecentActivity(
                    window: com.example.data.scanner.UsageTimeWindow,
                    nowMillis: Long
                ) = com.example.data.scanner.UsageActivitySnapshot(
                    hasUsagePermission = usageGranted,
                    window = window,
                    recentApps = if (usageGranted) {
                        listOf(
                            com.example.data.scanner.RecentAppUsage(
                                packageName = "com.example.chat",
                                lastUsedMillis = nowMillis - 60_000L,
                                totalForegroundMillis = 120_000L,
                                hasActiveForegroundService = false
                            )
                        )
                    } else {
                        emptyList()
                    }
                )

                override fun queryLastUsedTimeMillis(packageName: String, nowMillis: Long): Long? =
                    if (usageGranted) nowMillis - 60_000L else null
            },
            ioDispatcher = dispatcher
        )

        repo.refreshSystemStatus()
        assertTrue(repo.developerModeStatus.value.isUsbDebuggingEnabled)
        assertFalse(repo.hasUsageAccess.value)

        val stateWithUsbOn = com.example.ui.dashboard.DashboardUiState(
            suspiciousAppsCount = 2,
            unreviewedNewAppsCount = 1,
            developerModeStatus = repo.developerModeStatus.value
        )
        assertEquals(4, stateWithUsbOn.totalAttentionCount)

        // Turning off USB debugging & granting usage access updates on refresh
        devStatus = com.example.data.scanner.DeveloperModeStatus(
            isDeveloperOptionsEnabled = false,
            isUsbDebuggingEnabled = false
        )
        usageGranted = true
        repo.refreshSystemStatus()

        assertFalse(repo.developerModeStatus.value.isUsbDebuggingEnabled)
        assertTrue(repo.hasUsageAccess.value)
        assertEquals(1, repo.dashboardRecentUsage.value.recentApps.size)

        val stateWithUsbOff = stateWithUsbOn.copy(developerModeStatus = repo.developerModeStatus.value)
        assertEquals(3, stateWithUsbOff.totalAttentionCount)
    }

    private fun fakePkg(
        packageName: String,
        appName: String,
        isSystem: Boolean,
        installer: String? = if (isSystem) null else "com.android.vending",
        installTime: Long = 1_700_000_000_000L,
        hasLauncher: Boolean = true,
        riskyPerms: List<RiskyPermission> = emptyList(),
        hasAccessibility: Boolean = false,
        isDeviceAdmin: Boolean = false,
        hasNotificationListener: Boolean = false
    ) = ScannedPackageInfo(
        packageName = packageName,
        appName = appName,
        versionName = "1.0",
        versionCode = 1L,
        firstInstallTimeMillis = installTime,
        lastUpdateTimeMillis = installTime,
        installerPackageName = installer,
        isSystemApp = isSystem,
        hasLauncherIcon = hasLauncher,
        riskyPermissions = riskyPerms,
        hasAccessibilityAccess = hasAccessibility,
        isDeviceAdmin = isDeviceAdmin,
        hasNotificationListener = hasNotificationListener,
        hasOverlayPermission = RiskyPermission.OVERLAY in riskyPerms
    )
}
