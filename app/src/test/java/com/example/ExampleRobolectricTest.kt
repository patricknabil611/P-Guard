package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PGuardDatabase
import com.example.data.local.entity.HistoryAppEntry
import com.example.data.local.entity.HistoryChecklistEntry
import com.example.data.local.entity.HistoryPermissionChangeEntry
import com.example.data.local.entity.HistorySuspiciousEntry
import com.example.data.local.entity.ScanHistoryDetails
import com.example.data.local.entity.ScanHistoryEntity
import com.example.data.preferences.BackgroundScanInterval
import com.example.data.preferences.PGuardSettings
import com.example.data.repository.DeviceStatusRepository
import com.example.data.repository.SecurityScanRepository
import com.example.report.PdfReportGenerator
import com.example.security.AppLockManager
import com.example.worker.NotificationHelper
import com.example.data.scanner.AppBackgroundDataUsage
import com.example.data.scanner.ChecklistStatus
import com.example.data.scanner.DataUsageWindow
import com.example.data.scanner.DeveloperModeReader
import com.example.data.scanner.DeveloperModeStatus
import com.example.data.scanner.DeviceKeyboardInspector
import com.example.data.scanner.DevicePostureReader
import com.example.data.scanner.DevicePostureSnapshot
import com.example.data.scanner.EnabledKeyboardInfo
import com.example.data.scanner.KeyboardInspector
import com.example.data.scanner.KeyboardPublisher
import com.example.data.scanner.NetworkUsageScanner
import com.example.data.scanner.NetworkUsageSnapshot
import com.example.data.scanner.PackageScanner
import com.example.data.scanner.RecentAppUsage
import com.example.data.scanner.RiskLevel
import com.example.data.scanner.RiskyPermission
import com.example.data.scanner.RootCheckTrigger
import com.example.data.scanner.RootDetectionResult
import com.example.data.scanner.ScannedPackageInfo
import com.example.data.scanner.SecurityChecklistId
import com.example.data.scanner.SecurityScoreEvaluator
import com.example.data.scanner.SuspiciousReason
import com.example.data.scanner.UsageActivityScanner
import com.example.data.scanner.UsageActivitySnapshot
import com.example.data.scanner.UsageTimeWindow
import com.example.ui.common.AppSortOption
import com.example.ui.common.sortAssessments
import com.example.ui.dashboard.DashboardUiState
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

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
    fun `signature change flags HIGH risk sorted at top and detects newly granted permissions`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val certA = "AA:BB:CC:DD:11:22:33:44:55:66:77:88:99:00:AA:BB"
        val certB = "FF:EE:DD:CC:11:22:33:44:55:66:77:88:99:00:AA:BB"

        var installed = listOf(
            fakePkg(
                packageName = "com.bank.wallet",
                appName = "Wallet",
                isSystem = false,
                signingCertSha256 = certA,
                grantedPermissions = listOf("android.permission.VIBRATE")
            ),
            fakePkg(
                packageName = "com.other.heavy",
                appName = "Heavy App",
                isSystem = false,
                installer = null,
                hasLauncher = false,
                hasAccessibility = true,
                signingCertSha256 = certA,
                grantedPermissions = listOf("android.permission.CAMERA")
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

        // Baseline scan + mark wallet as trusted
        repository.runScan(force = true)
        repository.markAppTrusted("com.bank.wallet", "Wallet")

        // Second scan: Wallet's signing certificate changes AND gains SMS + CAMERA permissions
        installed = listOf(
            installed[0].copy(
                signingCertSha256 = certB,
                grantedPermissions = listOf(
                    "android.permission.VIBRATE",
                    "android.permission.READ_SMS",
                    "android.permission.CAMERA"
                )
            ),
            installed[1]
        )
        repository.runScan(force = true)

        // Signature change detected, forces HIGH risk, stays active even though previously trusted, sorts to top
        val sigChanges = repository.activeSignatureChanges.first()
        assertEquals(1, sigChanges.size)
        assertEquals("com.bank.wallet", sigChanges.first().packageName)

        val assessments = repository.securityAssessments.first()
        val sortedByRisk = sortAssessments(assessments.filter { it.isActiveWarning }, AppSortOption.RISK)
        assertEquals("com.bank.wallet", sortedByRisk.first().snapshot.packageName)
        assertEquals(RiskLevel.HIGH, sortedByRisk.first().riskLevel)
        assertTrue(sortedByRisk.first().hasSignatureChanged)
        assertTrue(sortedByRisk.first().reasons.contains(SuspiciousReason.SignatureChanged))

        // Permission changes detected and risky permissions identified
        val permChanges = repository.unreviewedPermissionChanges.first()
        assertEquals(1, permChanges.size)
        assertEquals("com.bank.wallet", permChanges.first().packageName)
        assertTrue(permChanges.first().addedPermissionsCsv.contains("android.permission.READ_SMS"))
        assertTrue(RiskyPermission.isRiskyAndroidPermission("android.permission.READ_SMS"))
        assertFalse(RiskyPermission.isRiskyAndroidPermission("android.permission.VIBRATE"))

        // Reviewing permission changes moves them out of unreviewed
        repository.markAllPermissionChangesReviewed()
        assertEquals(0, repository.unreviewedPermissionChanges.first().size)
        assertEquals(1, repository.reviewedPermissionChanges.first().size)
    }

    @Test
    fun `security score evaluator weights serious risks and treats VPN as informational`() {
        val today = LocalDate.of(2026, 6, 1)

        // Perfect device with VPN on should still score 100
        val cleanPosture = DevicePostureSnapshot(
            isDeviceSecure = true,
            securityPatchDate = "2026-05-01",
            unknownAppInstallerPackages = listOf("com.trusted.store"),
            userCaCertificateCount = 0,
            isVpnActive = true,
            rootResult = RootDetectionResult(isRooted = false)
        )
        val cleanReport = SecurityScoreEvaluator.evaluate(
            posture = cleanPosture,
            developerMode = DeveloperModeStatus(false, false),
            untrustedSuspiciousCount = 0,
            signatureChangeCount = 0,
            trustedPackages = setOf("com.trusted.store"),
            today = today
        )
        assertEquals(100, cleanReport.score)
        assertEquals(9, cleanReport.items.size)
        assertEquals(
            ChecklistStatus.INFO,
            cleanReport.items.first { it.id == SecurityChecklistId.VPN_ACTIVE }.status
        )

        // Risky device: no screen lock (-18), 8-month-old patch (-10), untrusted installer (-8),
        // user CA cert (-15), ADB on (-10), rooted (-20), 1 suspicious app (-4), 1 signature change (-15) -> 0
        val riskyPosture = DevicePostureSnapshot(
            isDeviceSecure = false,
            securityPatchDate = "2025-09-01",
            unknownAppInstallerPackages = listOf("com.unknown.installer"),
            userCaCertificateCount = 1,
            isVpnActive = false,
            rootResult = RootDetectionResult(
                isRooted = true,
                triggeredChecks = listOf(RootCheckTrigger.SU_BINARY_FOUND)
            )
        )
        val riskyReport = SecurityScoreEvaluator.evaluate(
            posture = riskyPosture,
            developerMode = DeveloperModeStatus(true, true),
            untrustedSuspiciousCount = 1,
            signatureChangeCount = 1,
            trustedPackages = emptySet(),
            today = today
        )
        assertEquals(0, riskyReport.score)
        assertEquals(
            ChecklistStatus.FAIL,
            riskyReport.items.first { it.id == SecurityChecklistId.ROOT_STATUS }.status
        )
        assertEquals(
            ChecklistStatus.FAIL,
            riskyReport.items.first { it.id == SecurityChecklistId.SECURITY_PATCH }.status
        )

        // Patch 4 months old -> WARNING
        assertEquals(
            ChecklistStatus.WARNING,
            SecurityScoreEvaluator.evaluateSecurityPatchStatus("2026-02-01", today)
        )
    }

    @Test
    fun `keyboard publisher classifier recognizes Google, Samsung, SwiftKey and flags third-party`() {
        assertEquals(
            KeyboardPublisher.GOOGLE,
            DeviceKeyboardInspector.classifyKeyboardPublisher("com.google.android.inputmethod.latin")
        )
        assertEquals(
            KeyboardPublisher.SAMSUNG,
            DeviceKeyboardInspector.classifyKeyboardPublisher("com.samsung.android.honeyboard")
        )
        assertEquals(
            KeyboardPublisher.MICROSOFT,
            DeviceKeyboardInspector.classifyKeyboardPublisher("com.touchtype.swiftkey")
        )
        assertEquals(
            KeyboardPublisher.THIRD_PARTY,
            DeviceKeyboardInspector.classifyKeyboardPublisher("com.shady.emoji.keyboard")
        )
    }

    @Test
    fun `dashboard attention count includes USB debugging warning and device status refreshes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var devStatus = DeveloperModeStatus(
            isDeveloperOptionsEnabled = true,
            isUsbDebuggingEnabled = true
        )
        var usageGranted = false

        val repo = DeviceStatusRepository(
            developerModeReader = object : DeveloperModeReader {
                override fun readStatus() = devStatus
            },
            usageActivityScanner = object : UsageActivityScanner {
                override fun hasUsageAccessPermission(): Boolean = usageGranted
                override fun queryRecentActivity(
                    window: UsageTimeWindow,
                    nowMillis: Long
                ) = UsageActivitySnapshot(
                    hasUsagePermission = usageGranted,
                    window = window,
                    recentApps = if (usageGranted) {
                        listOf(
                            RecentAppUsage(
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
            keyboardInspector = object : KeyboardInspector {
                override fun getEnabledKeyboards(): List<EnabledKeyboardInfo> = listOf(
                    EnabledKeyboardInfo(
                        id = "gboard",
                        packageName = "com.google.android.inputmethod.latin",
                        displayName = "Gboard",
                        isDefault = true,
                        publisher = KeyboardPublisher.GOOGLE
                    )
                )
            },
            networkUsageScanner = object : NetworkUsageScanner {
                override fun queryBackgroundDataUsage(
                    window: DataUsageWindow,
                    nowMillis: Long
                ): NetworkUsageSnapshot = NetworkUsageSnapshot(
                    hasUsagePermission = usageGranted,
                    window = window,
                    apps = emptyList(),
                    queriedAtMillis = nowMillis
                )

                override fun queryBackgroundDataForPackage(
                    packageName: String,
                    window: DataUsageWindow,
                    nowMillis: Long
                ): AppBackgroundDataUsage? = null
            },
            devicePostureReader = object : DevicePostureReader {
                override fun readPosture(): DevicePostureSnapshot = DevicePostureSnapshot()
            },
            ioDispatcher = dispatcher
        )

        repo.refreshSystemStatus()
        assertTrue(repo.developerModeStatus.value.isUsbDebuggingEnabled)
        assertFalse(repo.hasUsageAccess.value)

        val stateWithUsbOn = DashboardUiState(
            suspiciousAppsCount = 2,
            unreviewedNewAppsCount = 1,
            developerModeStatus = repo.developerModeStatus.value
        )
        assertEquals(4, stateWithUsbOn.totalAttentionCount)

        // Turning off USB debugging & granting usage access updates on refresh
        devStatus = DeveloperModeStatus(
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

    @Test
    fun `scan history records detailed runs, prunes entries older than 90 days, and clears cleanly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var installed = listOf(
            fakePkg("com.example.base", "Base App", isSystem = false)
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

        // Baseline scan
        val firstResult = repository.runScanDetailed(
            force = true,
            isBackground = false,
            developerModeStatus = DeveloperModeStatus(false, false)
        )
        assertTrue(firstResult.succeeded)
        assertTrue(firstResult.historyEntryId > 0L)

        // Second scan (background): 1 new sideloaded app + developer options turned on
        installed = installed + fakePkg(
            packageName = "com.new.sideload",
            appName = "Side Utility",
            isSystem = false,
            installer = null
        )
        val secondResult = repository.runScanDetailed(
            force = true,
            isBackground = true,
            developerModeStatus = DeveloperModeStatus(
                isDeveloperOptionsEnabled = true,
                isUsbDebuggingEnabled = false
            ),
            previousDevOptionsOn = false,
            previousUsbDebuggingOn = false,
            hasDevBaseline = true
        )
        assertTrue(secondResult.succeeded)
        assertEquals(listOf("Side Utility"), secondResult.newAppNames)
        assertEquals(listOf("Side Utility"), secondResult.newlySuspiciousAppNames)
        assertTrue(secondResult.developerOptionsTurnedOn)
        assertFalse(secondResult.usbDebuggingTurnedOn)

        val historyList = repository.scanHistory.first()
        assertEquals(2, historyList.size)
        val newest = historyList.first()
        assertTrue(newest.isBackground)
        assertEquals(1, newest.newAppsCount)
        assertEquals(1, newest.newSuspiciousCount)
        assertTrue(newest.devModeAlert)
        assertTrue(newest.summaryText.contains("1 new app"))

        // Insert a 95-day-old history entry and verify 90-day auto-prune removes only the old entry
        val now = System.currentTimeMillis()
        val ninetyFiveDaysAgo = now - (95L * 24L * 60L * 60L * 1_000L)
        database.scanDao().insertScanHistory(
            ScanHistoryEntity(
                scannedAtMillis = ninetyFiveDaysAgo,
                isBackground = true,
                securityScore = 92,
                totalAppsCount = 10,
                summaryText = "Old scan",
                detailsJson = ScanHistoryDetails().toJson()
            )
        )
        assertEquals(3, repository.scanHistory.first().size)

        repository.pruneExpiredScanHistory(nowMillis = now)
        val afterPrune = repository.scanHistory.first()
        assertEquals(2, afterPrune.size)
        assertTrue(afterPrune.none { it.summaryText == "Old scan" })

        // Clearing scan history removes history rows without removing package snapshots
        repository.clearAllScanHistory()
        assertEquals(0, repository.scanHistory.first().size)
        assertEquals(2, repository.latestSnapshotPackages.first().size)
    }

    @Test
    fun `scan history details JSON round-trip and summary formatting work accurately`() {
        val details = ScanHistoryDetails(
            developerOptionsEnabled = true,
            usbDebuggingEnabled = true,
            isRooted = false,
            checklist = listOf(
                HistoryChecklistEntry("SCREEN_LOCK", "Screen lock", "PASS", "PIN enabled")
            ),
            suspiciousApps = listOf(
                HistorySuspiciousEntry("Sketchy", "com.sketchy", "HIGH", listOf("Sideloaded", "Hidden"))
            ),
            hiddenApps = listOf(HistoryAppEntry("Sketchy", "com.sketchy", "Unknown source")),
            newApps = listOf(HistoryAppEntry("NewApp", "com.new.app", "com.android.vending")),
            permissionChanges = listOf(
                HistoryPermissionChangeEntry(
                    appName = "Wallet",
                    packageName = "com.bank.wallet",
                    addedPermissions = listOf("android.permission.READ_SMS"),
                    hasRiskyPermission = true
                )
            )
        )

        val decoded = ScanHistoryDetails.fromJson(details.toJson())
        assertEquals(details, decoded)

        val summary = ScanHistoryDetails.buildSummaryText(
            newAppsCount = 2,
            signatureChangesCount = 1,
            newSuspiciousCount = 0,
            permissionChangesCount = 1,
            devModeTurnedOn = false
        )
        assertEquals("1 signature change, 2 new apps, 1 permission change", summary)
    }

    @Test
    fun `app lock manager locks on open and after 1 minute in background`() {
        var currentTime = 1_000_000L
        val manager = AppLockManager(clockMillis = { currentTime })

        // Session start with lock enabled -> locked immediately
        manager.onSessionStarted(appLockEnabled = true)
        assertTrue(manager.isLocked.value)

        // Unlocking clears lock state
        manager.unlock()
        assertFalse(manager.isLocked.value)

        // Backgrounded for 30 seconds (< 1 minute) -> remains unlocked
        manager.onAppBackgrounded(appLockEnabled = true)
        currentTime += 30_000L
        manager.onAppForegrounded(appLockEnabled = true)
        assertFalse(manager.isLocked.value)

        // Backgrounded for 60 seconds (>= 1 minute) -> re-locks
        manager.onAppBackgrounded(appLockEnabled = true)
        currentTime += AppLockManager.LOCK_TIMEOUT_MILLIS
        manager.onAppForegrounded(appLockEnabled = true)
        assertTrue(manager.isLocked.value)

        // Turning off lock immediately unlocks
        manager.onLockSettingChanged(enabled = false)
        assertFalse(manager.isLocked.value)
    }

    @Test
    fun `pdf report filename formats ISO date and settings defaults match specification`() {
        val fixedMillis = ZonedDateTime.of(2026, 6, 26, 14, 30, 0, 0, ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals("P-Guard-Report-2026-06-26.pdf", PdfReportGenerator.defaultReportFilename(fixedMillis))

        val defaults = PGuardSettings()
        assertEquals(BackgroundScanInterval.HOURS_6, defaults.scanInterval)
        assertFalse(defaults.appLockEnabled)
        assertTrue(defaults.notifyNewApp)
        assertTrue(defaults.notifySignatureChanged)
        assertTrue(defaults.notifyNewSuspicious)
        assertTrue(defaults.notifyNewRiskyPermission)
        assertTrue(defaults.notifyDeveloperMode)

        assertEquals(BackgroundScanInterval.OFF, BackgroundScanInterval.fromHours(0))
        assertEquals(BackgroundScanInterval.HOURS_3, BackgroundScanInterval.fromHours(3))
        assertEquals(BackgroundScanInterval.HOURS_6, BackgroundScanInterval.fromHours(6))
        assertEquals(BackgroundScanInterval.HOURS_12, BackgroundScanInterval.fromHours(12))
        assertEquals(BackgroundScanInterval.HOURS_24, BackgroundScanInterval.fromHours(24))
    }

    @Test
    fun `notification helper creates high importance alerts and low importance updates channels`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.ensureChannels(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alertsChannel = manager.getNotificationChannel(NotificationHelper.CHANNEL_SECURITY_ALERTS)
        val updatesChannel = manager.getNotificationChannel(NotificationHelper.CHANNEL_SCAN_UPDATES)

        assertTrue(alertsChannel != null)
        assertEquals("Security alerts", alertsChannel!!.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, alertsChannel.importance)

        assertTrue(updatesChannel != null)
        assertEquals("Scan updates", updatesChannel!!.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_LOW, updatesChannel.importance)
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
        hasNotificationListener: Boolean = false,
        signingCertSha256: String = "AA:BB:CC:DD:EE:FF",
        grantedPermissions: List<String> = emptyList()
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
        hasOverlayPermission = RiskyPermission.OVERLAY in riskyPerms,
        signingCertSha256 = signingCertSha256,
        grantedPermissions = grantedPermissions
    )
}
