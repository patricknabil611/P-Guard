package com.example.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.preferences.PGuardSettings
import com.example.data.repository.ScanRunResult
import com.example.ui.navigation.PGuardRoutes

object NotificationHelper {
    const val CHANNEL_SECURITY_ALERTS = "p_guard_security_alerts"
    const val CHANNEL_SCAN_UPDATES = "p_guard_scan_updates"
    const val EXTRA_TARGET_ROUTE = "p_guard_target_route"

    private const val NOTIFY_ID_SIGNATURE = 1001
    private const val NOTIFY_ID_NEW_APP = 1002
    private const val NOTIFY_ID_SUSPICIOUS = 1003
    private const val NOTIFY_ID_PERMISSION = 1004
    private const val NOTIFY_ID_DEV_MODE = 1005

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val securityAlertsChannel = NotificationChannel(
            CHANNEL_SECURITY_ALERTS,
            context.getString(R.string.channel_security_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_security_alerts_desc)
            enableVibration(true)
        }

        val scanUpdatesChannel = NotificationChannel(
            CHANNEL_SCAN_UPDATES,
            context.getString(R.string.channel_scan_updates_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_scan_updates_desc)
        }

        manager.createNotificationChannel(securityAlertsChannel)
        manager.createNotificationChannel(scanUpdatesChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Sends notifications ONLY when something important changed in a background scan
     * and the matching alert type is enabled in Settings.
     */
    fun dispatchBackgroundScanAlerts(
        context: Context,
        result: ScanRunResult,
        settings: PGuardSettings
    ) {
        if (!result.succeeded) return
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        // 1. Signature changed (highest priority)
        if (settings.notifySignatureChanged && result.signatureChangedAppNames.isNotEmpty()) {
            val first = result.signatureChangedAppNames.first()
            val body = if (result.signatureChangedAppNames.size == 1) {
                context.getString(R.string.notif_signature_body_single, first)
            } else {
                context.getString(
                    R.string.notif_signature_body_plural,
                    result.signatureChangedAppNames.size,
                    first
                )
            }
            postAlert(
                context = context,
                notificationId = NOTIFY_ID_SIGNATURE,
                channelId = CHANNEL_SECURITY_ALERTS,
                title = context.getString(R.string.notif_signature_title),
                body = body,
                targetRoute = PGuardRoutes.SUSPICIOUS_APPS,
                priority = NotificationCompat.PRIORITY_MAX
            )
        }

        // 2. New app installed (show app name)
        if (settings.notifyNewApp && result.newAppNames.isNotEmpty()) {
            val first = result.newAppNames.first()
            val body = if (result.newAppNames.size == 1) {
                context.getString(R.string.notif_new_app_body_single, first)
            } else {
                context.getString(
                    R.string.notif_new_app_body_plural,
                    result.newAppNames.size,
                    first
                )
            }
            postAlert(
                context = context,
                notificationId = NOTIFY_ID_NEW_APP,
                channelId = CHANNEL_SECURITY_ALERTS,
                title = context.getString(R.string.notif_new_app_title),
                body = body,
                targetRoute = PGuardRoutes.NEW_APPS,
                priority = NotificationCompat.PRIORITY_HIGH
            )
        }

        // 3. New suspicious app found
        if (settings.notifyNewSuspicious && result.newlySuspiciousAppNames.isNotEmpty()) {
            val first = result.newlySuspiciousAppNames.first()
            val body = if (result.newlySuspiciousAppNames.size == 1) {
                context.getString(R.string.notif_suspicious_body_single, first)
            } else {
                context.getString(
                    R.string.notif_suspicious_body_plural,
                    result.newlySuspiciousAppNames.size,
                    first
                )
            }
            postAlert(
                context = context,
                notificationId = NOTIFY_ID_SUSPICIOUS,
                channelId = CHANNEL_SECURITY_ALERTS,
                title = context.getString(R.string.notif_suspicious_title),
                body = body,
                targetRoute = PGuardRoutes.SUSPICIOUS_APPS,
                priority = NotificationCompat.PRIORITY_HIGH
            )
        }

        // 4. New risky permission granted
        if (settings.notifyNewRiskyPermission && result.riskyPermissionChangedAppNames.isNotEmpty()) {
            val first = result.riskyPermissionChangedAppNames.first()
            val body = if (result.riskyPermissionChangedAppNames.size == 1) {
                context.getString(R.string.notif_permission_body_single, first)
            } else {
                context.getString(
                    R.string.notif_permission_body_plural,
                    result.riskyPermissionChangedAppNames.size,
                    first
                )
            }
            postAlert(
                context = context,
                notificationId = NOTIFY_ID_PERMISSION,
                channelId = CHANNEL_SECURITY_ALERTS,
                title = context.getString(R.string.notif_permission_title),
                body = body,
                targetRoute = PGuardRoutes.PERMISSION_CHANGES,
                priority = NotificationCompat.PRIORITY_HIGH
            )
        }

        // 5. Developer Options or USB Debugging turned on
        if (settings.notifyDeveloperMode && (result.usbDebuggingTurnedOn || result.developerOptionsTurnedOn)) {
            val body = if (result.usbDebuggingTurnedOn) {
                context.getString(R.string.notif_dev_mode_body_adb)
            } else {
                context.getString(R.string.notif_dev_mode_body_dev)
            }
            postAlert(
                context = context,
                notificationId = NOTIFY_ID_DEV_MODE,
                channelId = CHANNEL_SECURITY_ALERTS,
                title = context.getString(R.string.notif_dev_mode_title),
                body = body,
                targetRoute = PGuardRoutes.DEVELOPER_MODE,
                priority = NotificationCompat.PRIORITY_HIGH
            )
        }
    }

    private fun postAlert(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        body: String,
        targetRoute: String,
        priority: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_ROUTE, targetRoute)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked between check and notify
        }
    }
}
