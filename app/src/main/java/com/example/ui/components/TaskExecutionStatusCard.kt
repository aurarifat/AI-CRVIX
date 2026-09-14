package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devicecontrol.TaskExecutionSummary
import com.example.devicecontrol.TaskLogEntry
import com.example.devicecontrol.TaskLogLevel
import com.example.devicecontrol.TaskStepFeedback
import com.example.devicecontrol.TaskStepStatus
import com.example.ui.theme.MayaBorder
import com.example.ui.theme.MayaTextPrimary
import com.example.ui.theme.MayaTextSecondary
import com.example.ui.theme.MayaYellowContainer
import com.example.ui.theme.MayaYellowPrimary

@Composable
fun TaskExecutionStatusCard(
    isExecuting: Boolean,
    currentFeedback: TaskStepFeedback?,
    recentLogs: List<TaskLogEntry>,
    lastSummary: TaskExecutionSummary?,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
    onRunTest: (() -> Unit)? = null,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded || isExecuting) }

    // Auto-expand when task starts executing
    if (isExecuting && !isExpanded) {
        isExpanded = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val progressAnimated by animateFloatAsState(
        targetValue = when {
            isExecuting && currentFeedback != null -> currentFeedback.progressPercent / 100f
            lastSummary != null && lastSummary.isSuccess -> 1f
            else -> 0f
        },
        animationSpec = tween(400),
        label = "progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MayaBorder, RoundedCornerShape(16.dp))
            .animateContentSize()
            .testTag("task_manager_status_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Icon, Title, Status Badge, Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isExecuting -> MayaYellowPrimary.copy(alpha = pulseAlpha)
                                    lastSummary?.isSuccess == true -> Color(0xFFE8F5E9)
                                    lastSummary != null && !lastSummary.isSuccess -> Color(0xFFFFEBEE)
                                    else -> MayaYellowContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                isExecuting -> "⚡"
                                lastSummary?.isSuccess == true -> "✓"
                                lastSummary != null && !lastSummary.isSuccess -> "✕"
                                else -> "📋"
                            },
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Sequential Task Manager",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isExecuting && currentFeedback != null ->
                                    "Executing step ${currentFeedback.stepIndex} of ${currentFeedback.totalSteps}"
                                lastSummary != null ->
                                    "${lastSummary.completedTasks}/${lastSummary.totalTasks} steps completed (${lastSummary.totalDurationMs}ms)"
                                recentLogs.isNotEmpty() ->
                                    "${recentLogs.size} sequential command events logged"
                                else -> "Idle • Ready for sequential device commands"
                            },
                            fontSize = 11.sp,
                            color = MayaTextSecondary
                        )
                    }
                }

                // Status Badge Pill
                Surface(
                    color = when {
                        isExecuting -> MayaYellowPrimary
                        lastSummary?.isSuccess == true -> Color(0xFF2E7D32)
                        lastSummary != null && !lastSummary.isSuccess -> Color(0xFFD32F2F)
                        recentLogs.isNotEmpty() -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = MayaTextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = when {
                                isExecuting -> "RUNNING"
                                lastSummary?.isSuccess == true -> "COMPLETED"
                                lastSummary != null && !lastSummary.isSuccess -> "FAILED"
                                recentLogs.isNotEmpty() -> "LOGGED"
                                else -> "IDLE"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isExecuting -> MayaTextPrimary
                                lastSummary?.isSuccess == true -> Color.White
                                lastSummary != null && !lastSummary.isSuccess -> Color.White
                                else -> MayaTextSecondary
                            }
                        )
                    }
                }

                // Expand/Collapse Button
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("task_manager_toggle_expand")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse Logs" else "Expand Logs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Progress bar if executing or has recent summary
            if (isExecuting || lastSummary != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentFeedback?.message ?: (lastSummary?.summaryMessage ?: "Sequence Complete"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (currentFeedback != null && currentFeedback.totalSteps > 0) {
                            Text(
                                text = "${currentFeedback.progressPercent}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MayaYellowPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressAnimated },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = when {
                            lastSummary != null && !lastSummary.isSuccess -> Color(0xFFD32F2F)
                            else -> MayaYellowPrimary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Expandable Logs & Command Results Section
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = "SEQUENTIAL COMMAND LOGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MayaTextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (recentLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No device commands executed yet. Issue a command like 'open app and turn on protection' or tap Test below.",
                                fontSize = 11.sp,
                                color = MayaTextSecondary
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            recentLogs.takeLast(12).reversed().forEach { logEntry ->
                                TaskLogItemRow(logEntry = logEntry)
                            }
                        }
                    }

                    // Action Controls Footer
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onRunTest != null) {
                            FilledTonalButton(
                                onClick = onRunTest,
                                enabled = !isExecuting,
                                modifier = Modifier.testTag("task_manager_run_test_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Sequence", fontSize = 12.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (recentLogs.isNotEmpty()) {
                            TextButton(
                                onClick = onClearLogs,
                                modifier = Modifier.testTag("task_manager_clear_logs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFD32F2F)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Logs", fontSize = 12.sp, color = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskLogItemRow(
    logEntry: TaskLogEntry,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }

    val statusColor = when (logEntry.status) {
        TaskStepStatus.SUCCESS -> Color(0xFF2E7D32)
        TaskStepStatus.FAILED -> Color(0xFFD32F2F)
        TaskStepStatus.RUNNING -> Color(0xFFE65100)
        TaskStepStatus.SKIPPED -> Color(0xFF757575)
        TaskStepStatus.CANCELLED -> Color(0xFFC62828)
        TaskStepStatus.PENDING -> MayaTextSecondary
    }

    val statusIcon = when (logEntry.status) {
        TaskStepStatus.SUCCESS -> Icons.Default.CheckCircle
        TaskStepStatus.FAILED -> Icons.Default.Cancel
        TaskStepStatus.RUNNING -> Icons.Default.HourglassEmpty
        TaskStepStatus.SKIPPED -> Icons.Default.RemoveCircleOutline
        TaskStepStatus.CANCELLED -> Icons.Default.Cancel
        TaskStepStatus.PENDING -> Icons.Default.HourglassEmpty
    }

    val hasShellDetails = logEntry.commandResult != null &&
            (logEntry.commandResult.stdout.isNotBlank() || logEntry.commandResult.stderr.isNotBlank())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(0.5.dp, MayaBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = logEntry.status.name,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val stepPrefix = if (logEntry.stepIndex > 0) {
                        if (logEntry.totalSteps > 0) "Step ${logEntry.stepIndex}/${logEntry.totalSteps}: " else "Step ${logEntry.stepIndex}: "
                    } else ""

                    Text(
                        text = "$stepPrefix${logEntry.taskName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (logEntry.executionTimeMs > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MayaTextSecondary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${logEntry.executionTimeMs}ms",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MayaTextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = logEntry.message,
                    fontSize = 11.sp,
                    color = when (logEntry.status) {
                        TaskStepStatus.FAILED -> Color(0xFFD32F2F)
                        TaskStepStatus.SUCCESS -> MaterialTheme.colorScheme.onSurface
                        else -> MayaTextSecondary
                    }
                )
            }

            if (hasShellDetails) {
                IconButton(
                    onClick = { showDetails = !showDetails },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Toggle Shell Output",
                        tint = MayaYellowPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Expandable Shell stdout/stderr details
        AnimatedVisibility(visible = showDetails && hasShellDetails) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(8.dp)
            ) {
                if (logEntry.commandResult?.stdout?.isNotBlank() == true) {
                    Text(
                        text = "STDOUT:\n${logEntry.commandResult.stdout.trim()}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF81C784)
                    )
                }
                if (logEntry.commandResult?.stderr?.isNotBlank() == true) {
                    if (logEntry.commandResult.stdout.isNotBlank()) Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "STDERR:\n${logEntry.commandResult.stderr.trim()}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFFE57373)
                    )
                }
            }
        }
    }
}
