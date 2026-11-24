package com.example.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val scanDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())

private val shortDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())

fun formatScanDateTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(scanDateTimeFormatter)
}

fun formatShortDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(shortDateFormatter)
}

fun formatForegroundDuration(durationMillis: Long): String {
    if (durationMillis <= 0L) return "< 1s"
    val totalSeconds = durationMillis / 1_000L
    if (totalSeconds < 1L) return "< 1s"
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> if (minutes > 0L) "${hours}h ${minutes}m" else "${hours}h"
        minutes > 0L -> if (seconds > 0L) "${minutes}m ${seconds}s" else "${minutes}m"
        else -> "${seconds}s"
    }
}

@Composable
fun formatInstallerSource(installerPackageName: String?, isSystemApp: Boolean): String {
    if (installerPackageName.isNullOrBlank()) {
        return if (isSystemApp) {
            stringResource(R.string.installer_system)
        } else {
            stringResource(R.string.installer_unknown)
        }
    }
    return when {
        installerPackageName == "com.android.vending" -> stringResource(R.string.installer_google_play)
        installerPackageName.contains("pstore", ignoreCase = true) ||
            installerPackageName.contains("p_store", ignoreCase = true) -> stringResource(R.string.installer_p_store)
        installerPackageName == "com.google.android.packageinstaller" ||
            installerPackageName == "com.android.packageinstaller" -> stringResource(R.string.installer_package_installer)
        installerPackageName == "com.amazon.venezia" -> stringResource(R.string.installer_amazon)
        installerPackageName == "com.sec.android.app.samsungapps" -> stringResource(R.string.installer_samsung)
        installerPackageName == "org.fdroid.fdroid" -> stringResource(R.string.installer_fdroid)
        installerPackageName == "com.android.shell" -> stringResource(R.string.installer_adb)
        else -> installerPackageName
    }
}

fun openAppInfoSettings(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Ignore if settings activity is unavailable on a custom ROM
    }
}

@Composable
fun AppPackageIcon(
    packageName: String,
    appName: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        iconBitmap = withContext(Dispatchers.IO) {
            loadPackageIconBitmap(context, packageName)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val loaded = iconBitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = stringResource(R.string.cd_app_icon, appName),
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = stringResource(R.string.cd_app_icon, appName),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

private fun loadPackageIconBitmap(context: Context, packageName: String): ImageBitmap? {
    return try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        drawableToBitmap(drawable, sizePx = 96).asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(sizePx) ?: sizePx
    val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(sizePx) ?: sizePx
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
