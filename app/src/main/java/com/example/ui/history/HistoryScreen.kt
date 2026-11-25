package com.example.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.ScanHistoryEntity
import com.example.ui.common.formatScanDateTime
import com.example.ui.common.formatShortDate
import com.example.ui.theme.PGuardTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    onOpenHistoryDetail: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_screen_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .testTag("history_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "score_chart") {
                    SecurityScoreChartCard(points = uiState.last30DaysChartPoints)
                }

                item(key = "retention_note") {
                    Text(
                        text = stringResource(R.string.history_retention_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (uiState.entries.isEmpty()) {
                    item(key = "empty_history") {
                        EmptyHistoryCard()
                    }
                } else {
                    items(
                        items = uiState.entries,
                        key = { it.id }
                    ) { entry ->
                        HistoryTimelineCard(
                            entry = entry,
                            onClick = { onOpenHistoryDetail(entry.id) }
                        )
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SecurityScoreChartCard(points: List<ScanHistoryEntity>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val semantic = PGuardTheme.semanticColors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_score_chart_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.history_chart_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (points.size >= 2) {
                            stringResource(
                                R.string.history_chart_subtitle_points,
                                points.size,
                                points.last().securityScore
                            )
                        } else {
                            stringResource(R.string.history_chart_subtitle_empty)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (points.isNotEmpty()) {
                val sampled = if (points.size > 30) points.takeLast(30) else points
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(124.dp)
                        .testTag("history_score_line_chart")
                ) {
                    val padLeft = 8.dp.toPx()
                    val padRight = 8.dp.toPx()
                    val padTop = 10.dp.toPx()
                    val padBottom = 10.dp.toPx()
                    val chartWidth = (size.width - padLeft - padRight).coerceAtLeast(1f)
                    val chartHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    // Reference horizontal guides at 100, 80, 50, 0
                    listOf(100, 80, 50, 0).forEach { level ->
                        val y = padTop + chartHeight * (1f - level / 100f)
                        drawLine(
                            color = gridColor,
                            start = Offset(padLeft, y),
                            end = Offset(padLeft + chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (level == 80 || level == 50) dashEffect else null
                        )
                    }

                    val coords = sampled.mapIndexed { index, item ->
                        val xFraction = if (sampled.size == 1) 0.5f else index.toFloat() / (sampled.size - 1)
                        val x = padLeft + chartWidth * xFraction
                        val y = padTop + chartHeight * (1f - item.securityScore.coerceIn(0, 100) / 100f)
                        Offset(x, y) to item.securityScore
                    }

                    if (coords.size >= 2) {
                        val linePath = Path().apply {
                            moveTo(coords.first().first.x, coords.first().first.y)
                            for (i in 1 until coords.size) {
                                lineTo(coords[i].first.x, coords[i].first.y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(coords.last().first.x, padTop + chartHeight)
                            lineTo(coords.first().first.x, padTop + chartHeight)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.22f),
                                    Color.Transparent
                                ),
                                startY = padTop,
                                endY = padTop + chartHeight
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = lineColor,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    coords.forEach { (point, score) ->
                        val dotColor = when {
                            score >= 80 -> semantic.statusClear
                            score >= 50 -> semantic.statusAttention
                            else -> semantic.statusDanger
                        }
                        drawCircle(
                            color = dotColor,
                            radius = 4.dp.toPx(),
                            center = point
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatShortDate(sampled.first().scannedAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatShortDate(sampled.last().scannedAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTimelineCard(
    entry: ScanHistoryEntity,
    onClick: () -> Unit
) {
    val semantic = PGuardTheme.semanticColors
    val (scoreBg, scoreFg, scoreBorder) = when {
        entry.securityScore >= 80 -> Triple(
            semantic.statusClearContainer,
            semantic.statusClear,
            semantic.statusClearBorder
        )
        entry.securityScore >= 50 -> Triple(
            semantic.statusAttentionContainer,
            semantic.statusAttention,
            semantic.statusAttentionBorder
        )
        else -> Triple(
            semantic.statusDangerContainer,
            semantic.statusDanger,
            semantic.statusDangerBorder
        )
    }

    val hasChanges = entry.summaryText.isNotBlank()

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${entry.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (entry.signatureChangesCount > 0) {
                semantic.statusDangerBorder
            } else if (hasChanges) {
                semantic.statusAttentionBorder
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = scoreBg,
                border = BorderStroke(1.dp, scoreBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = entry.securityScore.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = scoreFg
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatScanDateTime(entry.scannedAtMillis),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = stringResource(
                                if (entry.isBackground) R.string.badge_scan_background
                                else R.string.badge_scan_manual
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (hasChanges) entry.summaryText else stringResource(R.string.history_no_changes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasChanges) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.history_detail_screen_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_history_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Timeline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.empty_history_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.empty_history_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
