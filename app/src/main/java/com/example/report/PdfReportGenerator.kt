package com.example.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.local.entity.ScanHistoryDetails
import com.example.data.local.entity.ScanHistoryEntity
import com.example.ui.common.formatScanDateTime
import com.example.ui.common.formatShortPermissionName
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)
    private const val BOTTOM_LIMIT = PAGE_HEIGHT - 54f

    private val TEAL_PRIMARY = Color.parseColor("#0D9488")
    private const val TEAL_LIGHT = 0xFF2DD4BF.toInt()
    private const val TEAL_DARK = 0xFF0F766E.toInt()
    private val TEXT_PRIMARY = Color.parseColor("#0F172A")
    private val TEXT_SECONDARY = Color.parseColor("#475569")
    private val BORDER_COLOR = Color.parseColor("#E2E8F0")
    private val SURFACE_BG = Color.parseColor("#F8FAFC")
    private val STATUS_GREEN = Color.parseColor("#059669")
    private val STATUS_AMBER = Color.parseColor("#D97706")
    private val STATUS_RED = Color.parseColor("#DC2626")

    private val filenameDateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    fun defaultReportFilename(epochMillis: Long = System.currentTimeMillis()): String {
        val dateStr = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(filenameDateFormatter)
        return "P-Guard-Report-$dateStr.pdf"
    }

    fun writePdfToUri(
        context: Context,
        uri: Uri,
        history: ScanHistoryEntity
    ): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                generatePdf(history, out)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun createShareablePdfUri(
        context: Context,
        history: ScanHistoryEntity
    ): Uri? {
        return try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(reportsDir, defaultReportFilename(history.scannedAtMillis))
            FileOutputStream(file).use { out ->
                if (!generatePdf(history, out)) return null
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            null
        }
    }

    fun generatePdf(history: ScanHistoryEntity, outputStream: OutputStream): Boolean {
        val details = ScanHistoryDetails.fromJson(history.detailsJson)
        val document = PdfDocument()
        try {
            val writer = PageWriter(document)
            writer.drawHeader(history)
            writer.drawScoreAndOverview(history, details)

            // 1. Security Checklist
            writer.drawSectionHeader("Security Checklist")
            if (details.checklist.isEmpty()) {
                writer.drawMutedLine("No checklist items recorded.")
            } else {
                details.checklist.forEach { item ->
                    val color = when (item.status) {
                        "PASS" -> STATUS_GREEN
                        "WARNING" -> STATUS_AMBER
                        "FAIL" -> STATUS_RED
                        else -> TEAL_PRIMARY
                    }
                    writer.drawStatusLine(
                        badge = item.status,
                        badgeColor = color,
                        title = item.title,
                        subtitle = item.explanation
                    )
                }
            }

            // 2. Suspicious Apps
            writer.drawSectionHeader("Suspicious Apps (${details.suspiciousApps.size})")
            if (details.suspiciousApps.isEmpty()) {
                writer.drawMutedLine("No untrusted suspicious apps flagged.")
            } else {
                details.suspiciousApps.forEach { app ->
                    val riskColor = when (app.riskLevel) {
                        "HIGH" -> STATUS_RED
                        "MEDIUM" -> STATUS_AMBER
                        else -> TEXT_SECONDARY
                    }
                    val reasonsLine = app.reasons.joinToString(" · ")
                    writer.drawStatusLine(
                        badge = app.riskLevel,
                        badgeColor = riskColor,
                        title = "${app.appName} (${app.packageName})",
                        subtitle = reasonsLine
                    )
                }
            }

            // 3. Hidden Apps
            writer.drawSectionHeader("Hidden User Apps (${details.hiddenApps.size})")
            if (details.hiddenApps.isEmpty()) {
                writer.drawMutedLine("No hidden user-installed apps detected.")
            } else {
                details.hiddenApps.forEach { app ->
                    writer.drawBulletLine(
                        title = app.appName,
                        subtitle = "${app.packageName} · Source: ${app.detail}"
                    )
                }
            }

            // 4. New Apps
            writer.drawSectionHeader("New Apps Detected (${details.newApps.size})")
            if (details.newApps.isEmpty()) {
                writer.drawMutedLine("No new apps detected in this scan.")
            } else {
                details.newApps.forEach { app ->
                    writer.drawBulletLine(
                        title = app.appName,
                        subtitle = "${app.packageName} · Source: ${app.detail}"
                    )
                }
            }

            // 5. Permission Changes
            writer.drawSectionHeader("Permission Changes (${details.permissionChanges.size})")
            if (details.permissionChanges.isEmpty()) {
                writer.drawMutedLine("No newly granted permissions detected in this scan.")
            } else {
                details.permissionChanges.forEach { change ->
                    val formattedPerms = change.addedPermissions.joinToString(", ") {
                        formatShortPermissionName(it)
                    }
                    writer.drawStatusLine(
                        badge = if (change.hasRiskyPermission) "RISKY" else "ADDED",
                        badgeColor = if (change.hasRiskyPermission) STATUS_AMBER else TEXT_SECONDARY,
                        title = "${change.appName} (${change.packageName})",
                        subtitle = "+ $formattedPerms"
                    )
                }
            }

            // 6. Developer Mode & Integrity
            writer.drawSectionHeader("Developer Mode & Device Integrity")
            writer.drawStatusLine(
                badge = if (details.developerOptionsEnabled) "ON" else "OFF",
                badgeColor = if (details.developerOptionsEnabled) STATUS_AMBER else STATUS_GREEN,
                title = "Developer Options",
                subtitle = if (details.developerOptionsEnabled) "Enabled on device" else "Disabled"
            )
            writer.drawStatusLine(
                badge = if (details.usbDebuggingEnabled) "ON" else "OFF",
                badgeColor = if (details.usbDebuggingEnabled) STATUS_RED else STATUS_GREEN,
                title = "USB Debugging (ADB)",
                subtitle = if (details.usbDebuggingEnabled) "Enabled — turn off when not developing" else "Disabled"
            )
            writer.drawStatusLine(
                badge = if (details.isRooted) "ROOTED" else "CLEAN",
                badgeColor = if (details.isRooted) STATUS_RED else STATUS_GREEN,
                title = "Root Detection",
                subtitle = if (details.isRooted) {
                    "Triggered: ${details.rootTriggers.joinToString(", ")}"
                } else {
                    "Not rooted"
                }
            )

            writer.finishCurrentPage()
            document.writeTo(outputStream)
            outputStream.flush()
            return true
        } finally {
            document.close()
        }
    }

    private class PageWriter(private val document: PdfDocument) {
        private var pageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas = Canvas()
        private var y = 40f

        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEAL_PRIMARY
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BORDER_COLOR
            strokeWidth = 1f
        }
        private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SURFACE_BG
            style = Paint.Style.FILL
        }
        private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BORDER_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        init {
            startNewPage()
        }

        private fun startNewPage() {
            finishCurrentPage()
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            currentPage = document.startPage(pageInfo)
            canvas = currentPage!!.canvas
            canvas.drawColor(Color.WHITE)
            y = 44f
        }

        fun finishCurrentPage() {
            val page = currentPage ?: return
            // Draw page footer
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_SECONDARY
                textSize = 8.5f
            }
            canvas.drawLine(MARGIN_X, PAGE_HEIGHT - 36f, PAGE_WIDTH - MARGIN_X, PAGE_HEIGHT - 36f, dividerPaint)
            canvas.drawText(
                "P Guard v${BuildConfig.VERSION_NAME} · On-device security report · Page $pageNumber",
                MARGIN_X,
                PAGE_HEIGHT - 20f,
                footerPaint
            )
            document.finishPage(page)
            currentPage = null
        }

        private fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > BOTTOM_LIMIT) {
                startNewPage()
            }
        }

        fun drawHeader(history: ScanHistoryEntity) {
            // Draw P Guard shield icon (36x36)
            val iconSize = 36f
            val iconRect = RectF(MARGIN_X, y, MARGIN_X + iconSize, y + iconSize)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    iconRect.left,
                    iconRect.top,
                    iconRect.right,
                    iconRect.bottom,
                    TEAL_LIGHT,
                    TEAL_DARK,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(iconRect, 9f, 9f, bgPaint)

            // Shield path scaled into iconRect
            val cx = iconRect.centerX()
            val top = iconRect.top + 6f
            val shieldPath = Path().apply {
                moveTo(cx, top)
                lineTo(iconRect.right - 7f, top + 4f)
                lineTo(iconRect.right - 7f, iconRect.centerY() + 2f)
                cubicTo(
                    iconRect.right - 7f,
                    iconRect.bottom - 9f,
                    cx + 5f,
                    iconRect.bottom - 6f,
                    cx,
                    iconRect.bottom - 5f
                )
                cubicTo(
                    cx - 5f,
                    iconRect.bottom - 6f,
                    iconRect.left + 7f,
                    iconRect.bottom - 9f,
                    iconRect.left + 7f,
                    iconRect.centerY() + 2f
                )
                lineTo(iconRect.left + 7f, top + 4f)
                close()
            }
            val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawPath(shieldPath, shieldPaint)

            val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEAL_PRIMARY
                textSize = 15f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText("P", cx, iconRect.centerY() + 6f, letterPaint)

            // Title & metadata
            val textX = MARGIN_X + iconSize + 12f
            canvas.drawText("P Guard Security Report", textX, y + 16f, titlePaint)
            val scanType = if (history.isBackground) "Background scan" else "Manual scan"
            canvas.drawText(
                "Scanned: ${formatScanDateTime(history.scannedAtMillis)} · $scanType · ${history.totalAppsCount} apps",
                textX,
                y + 31f,
                bodyPaint
            )
            y += iconSize + 14f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, dividerPaint)
            y += 14f
        }

        fun drawScoreAndOverview(history: ScanHistoryEntity, details: ScanHistoryDetails) {
            val cardHeight = 70f
            ensureSpace(cardHeight + 12f)
            val rect = RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + cardHeight)
            canvas.drawRoundRect(rect, 12f, 12f, cardBgPaint)
            canvas.drawRoundRect(rect, 12f, 12f, cardStrokePaint)

            val scoreColor = when {
                history.securityScore >= 80 -> STATUS_GREEN
                history.securityScore >= 50 -> STATUS_AMBER
                else -> STATUS_RED
            }

            // Score circle
            val circleCenterX = MARGIN_X + 40f
            val circleCenterY = y + cardHeight / 2f
            val radius = 22f
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = BORDER_COLOR
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = scoreColor
                style = Paint.Style.STROKE
                strokeWidth = 5f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawCircle(circleCenterX, circleCenterY, radius, trackPaint)
            val arcRect = RectF(
                circleCenterX - radius,
                circleCenterY - radius,
                circleCenterX + radius,
                circleCenterY + radius
            )
            canvas.drawArc(arcRect, -90f, 360f * (history.securityScore / 100f), false, progressPaint)

            val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = scoreColor
                textSize = 14f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText(history.securityScore.toString(), circleCenterX, circleCenterY + 5f, scoreTextPaint)

            val textX = MARGIN_X + 78f
            val statusHeadline = when {
                history.securityScore >= 80 -> "Strong security posture (${history.securityScore}/100)"
                history.securityScore >= 50 -> "Some items need review (${history.securityScore}/100)"
                else -> "Critical risks need attention (${history.securityScore}/100)"
            }
            canvas.drawText(statusHeadline, textX, y + 26f, bodyBoldPaint.apply { textSize = 12f })
            bodyBoldPaint.textSize = 10.5f

            val summary = history.summaryText.ifBlank { "No changes detected in this scan" }
            canvas.drawText("Summary: $summary", textX, y + 43f, bodyPaint)
            canvas.drawText(
                "Suspicious: ${details.suspiciousApps.size} · Hidden: ${details.hiddenApps.size} · Rooted: ${if (details.isRooted) "Yes" else "No"}",
                textX,
                y + 57f,
                bodyPaint
            )

            y += cardHeight + 14f
        }

        fun drawSectionHeader(title: String) {
            ensureSpace(26f)
            y += 6f
            canvas.drawText(title, MARGIN_X, y + 12f, sectionPaint)
            y += 18f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, dividerPaint)
            y += 10f
        }

        fun drawMutedLine(text: String) {
            ensureSpace(16f)
            canvas.drawText(text, MARGIN_X, y + 10f, bodyPaint)
            y += 16f
        }

        fun drawStatusLine(badge: String, badgeColor: Int, title: String, subtitle: String) {
            val wrappedSubtitle = wrapText(subtitle, bodyPaint, CONTENT_WIDTH - 68f)
            val needed = 16f + (wrappedSubtitle.size * 12.5f) + 6f
            ensureSpace(needed)

            // Draw badge pill
            badgePaint.color = badgeColor
            val badgeWidth = 58f
            val pillRect = RectF(MARGIN_X, y, MARGIN_X + badgeWidth, y + 15f)
            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(28, Color.red(badgeColor), Color.green(badgeColor), Color.blue(badgeColor))
            }
            canvas.drawRoundRect(pillRect, 4f, 4f, pillBg)
            badgePaint.textAlign = Paint.Align.CENTER
            canvas.drawText(badge.take(7), pillRect.centerX(), y + 10.5f, badgePaint)
            badgePaint.textAlign = Paint.Align.LEFT

            val textX = MARGIN_X + badgeWidth + 10f
            canvas.drawText(truncateText(title, bodyBoldPaint, CONTENT_WIDTH - 68f), textX, y + 11f, bodyBoldPaint)
            y += 15f
            wrappedSubtitle.forEach { line ->
                canvas.drawText(line, textX, y + 10f, bodyPaint)
                y += 12.5f
            }
            y += 5f
        }

        fun drawBulletLine(title: String, subtitle: String) {
            val wrappedSubtitle = wrapText(subtitle, bodyPaint, CONTENT_WIDTH - 16f)
            val needed = 14f + (wrappedSubtitle.size * 12.5f) + 5f
            ensureSpace(needed)

            canvas.drawText("• ${truncateText(title, bodyBoldPaint, CONTENT_WIDTH - 16f)}", MARGIN_X, y + 10f, bodyBoldPaint)
            y += 14f
            wrappedSubtitle.forEach { line ->
                canvas.drawText(line, MARGIN_X + 10f, y + 10f, bodyPaint)
                y += 12.5f
            }
            y += 4f
        }

        private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isBlank()) return emptyList()
            val words = text.split(' ')
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) lines += current.toString()
                    current = StringBuilder(truncateText(word, paint, maxWidth))
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
            return lines.take(4)
        }

        private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 3 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
                end--
            }
            return text.substring(0, end) + "…"
        }
    }
}
