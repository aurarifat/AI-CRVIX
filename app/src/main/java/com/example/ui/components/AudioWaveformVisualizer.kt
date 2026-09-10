package com.example.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MayaBorder
import com.example.ui.theme.MayaTextPrimary
import com.example.ui.theme.MayaTextSecondary
import com.example.ui.theme.MayaYellowBright
import com.example.ui.theme.MayaYellowContainer
import com.example.ui.theme.MayaYellowDeep
import com.example.ui.theme.MayaYellowPrimary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class WaveformStyle {
    DYNAMIC_BARS,
    OSCILLOSCOPE_RIBBON,
    CIRCULAR_RADAR
}

/**
 * Real-Time Microphone Audio Waveform Visualizer
 *
 * Visualizes live audio input with smooth physics-based dampening,
 * fluid bezier wave interpolation, multi-frequency bar dynamics, and
 * recording telemetry (live dB, decibel meter, and pulsing recording badge).
 */
@Composable
fun AudioWaveformVisualizer(
    rmsLevel: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    style: WaveformStyle = WaveformStyle.DYNAMIC_BARS,
    height: Dp = 80.dp,
    barCount: Int = 32,
    accentColor: Color = MayaYellowPrimary,
    glowColor: Color = MayaYellowBright,
    secondaryColor: Color = MayaYellowDeep
) {
    // Smoothed RMS with responsive attack & gentle decay
    var smoothRms by remember { mutableFloatStateOf(0.05f) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // Recording timer tracking
    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedSeconds = 0
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        } else {
            elapsedSeconds = 0
        }
    }

    // Smooth decay / attack interpolation loop
    LaunchedEffect(rmsLevel, isRecording) {
        val target = if (isRecording) rmsLevel.coerceIn(0.04f, 1.0f) else 0.02f
        val smoothingFactor = if (target > smoothRms) 0.65f else 0.25f
        smoothRms += (target - smoothRms) * smoothingFactor
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_animation")

    // Continuous wave phase for animated ripple motion
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Secondary harmonic phase
    val harmonicPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (4 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "harmonicPhase"
    )

    // Ambient breathing pulse for idle state
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MayaBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
            .testTag("audio_waveform_visualizer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Telemetry header: Recording status, timer & RMS dB meter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Pulsing recording red indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Color(0xFFE53935)
                            else MayaTextSecondary.copy(alpha = 0.5f)
                        )
                )
                Text(
                    text = if (isRecording) "RECORDING AUDIO" else "MIC READY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = if (isRecording) Color(0xFFE53935) else MayaTextSecondary
                )
            }

            // Real-time timer and decibel gauge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRecording) {
                    val minutes = elapsedSeconds / 60
                    val seconds = elapsedSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val dbEstimated = (-40f + (smoothRms * 40f)).toInt()
                Text(
                    text = if (isRecording) "${dbEstimated} dB" else "0 dB",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MayaTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Visualizer Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center
        ) {
            when (style) {
                WaveformStyle.DYNAMIC_BARS -> {
                    DynamicBarsCanvas(
                        barCount = barCount,
                        rmsLevel = smoothRms,
                        wavePhase = wavePhase,
                        harmonicPhase = harmonicPhase,
                        ambientPulse = ambientPulse,
                        isRecording = isRecording,
                        accentColor = accentColor,
                        glowColor = glowColor,
                        secondaryColor = secondaryColor
                    )
                }
                WaveformStyle.OSCILLOSCOPE_RIBBON -> {
                    OscilloscopeRibbonCanvas(
                        rmsLevel = smoothRms,
                        wavePhase = wavePhase,
                        harmonicPhase = harmonicPhase,
                        isRecording = isRecording,
                        accentColor = accentColor,
                        glowColor = glowColor
                    )
                }
                WaveformStyle.CIRCULAR_RADAR -> {
                    CircularWaveformCanvas(
                        rmsLevel = smoothRms,
                        wavePhase = wavePhase,
                        isRecording = isRecording,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

/**
 * Symmetrical, multi-frequency animated bars canvas
 */
@Composable
private fun DynamicBarsCanvas(
    barCount: Int,
    rmsLevel: Float,
    wavePhase: Float,
    harmonicPhase: Float,
    ambientPulse: Float,
    isRecording: Boolean,
    accentColor: Color,
    glowColor: Color,
    secondaryColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .testTag("waveform_dynamic_bars_canvas")
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val totalSpacing = width * 0.02f
        val usableWidth = width - totalSpacing * 2
        val barWidth = usableWidth / (barCount * 1.5f)
        val barSpacing = usableWidth / barCount

        val centerIndex = barCount / 2f

        for (i in 0 until barCount) {
            val distanceFromCenter = kotlin.math.abs(i - centerIndex) / centerIndex // 0.0 at center, 1.0 at edges
            // Bell curve envelope: higher at center, gracefully tapering off towards sides
            val envelope = kotlin.math.cos(distanceFromCenter * (Math.PI / 2.2)).toFloat().coerceIn(0.18f, 1.0f)

            // Calculate animated multi-harmonic height
            val wave1 = sin(wavePhase + (i.toFloat() / barCount) * Math.PI.toFloat() * 2.5f)
            val wave2 = cos(harmonicPhase + (i.toFloat() / barCount) * Math.PI.toFloat() * 1.8f) * 0.35f
            val modulation = (0.65f + 0.35f * (wave1 + wave2).toFloat()).coerceIn(0.2f, 1.4f)

            val baseMinHeight = 6f
            val activeAmplification = if (isRecording) {
                (rmsLevel * (height * 0.88f) * envelope * modulation)
            } else {
                (baseMinHeight * ambientPulse * envelope)
            }

            val finalBarHeight = max(baseMinHeight, activeAmplification).coerceAtMost(height - 4f)
            val x = totalSpacing + (i * barSpacing) + (barSpacing - barWidth) / 2f
            val topY = centerY - (finalBarHeight / 2f)

            // Dynamic gradient coloring based on height amplitude
            val barBrush = Brush.verticalGradient(
                colors = if (isRecording) {
                    listOf(
                        glowColor,
                        accentColor,
                        secondaryColor
                    )
                } else {
                    listOf(
                        accentColor.copy(alpha = 0.4f),
                        accentColor.copy(alpha = 0.2f)
                    )
                },
                startY = topY,
                endY = topY + finalBarHeight
            )

            // Draw rounded pill bar
            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, topY),
                size = Size(barWidth.coerceAtLeast(3f), finalBarHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )

            // Draw faint reflection / glow dot on top of peak bars
            if (isRecording && finalBarHeight > (height * 0.4f)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = (barWidth / 3.5f).coerceAtMost(3f),
                    center = Offset(x + barWidth / 2f, topY - 3f)
                )
            }
        }
    }
}

/**
 * Continuous sine oscilloscope wave path
 */
@Composable
private fun OscilloscopeRibbonCanvas(
    rmsLevel: Float,
    wavePhase: Float,
    harmonicPhase: Float,
    isRecording: Boolean,
    accentColor: Color,
    glowColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .testTag("waveform_oscilloscope_canvas")
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        val primaryPath = Path()
        val secondaryPath = Path()

        val points = 60
        val stepX = width / points
        val amplitude = if (isRecording) (rmsLevel * height * 0.42f).coerceAtLeast(4f) else 3f

        for (i in 0..points) {
            val x = i * stepX
            val normalizedX = (i.toFloat() / points)
            val window = sin(normalizedX * Math.PI.toFloat()) // Hanning window

            val y1 = centerY + sin(wavePhase + normalizedX * 4f * Math.PI.toFloat()) * amplitude * window
            val y2 = centerY + cos(harmonicPhase + normalizedX * 5f * Math.PI.toFloat()) * (amplitude * 0.6f) * window

            if (i == 0) {
                primaryPath.moveTo(x, y1)
                secondaryPath.moveTo(x, y2)
            } else {
                primaryPath.lineTo(x, y1)
                secondaryPath.lineTo(x, y2)
            }
        }

        // Ambient glow stroke
        drawPath(
            path = primaryPath,
            color = glowColor.copy(alpha = 0.35f),
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )

        // Primary waveform stroke
        drawPath(
            path = primaryPath,
            brush = Brush.horizontalGradient(
                listOf(accentColor.copy(alpha = 0.3f), accentColor, glowColor, accentColor.copy(alpha = 0.3f))
            ),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )

        // Harmonic secondary stroke
        drawPath(
            path = secondaryPath,
            color = accentColor.copy(alpha = 0.45f),
            style = Stroke(width = 1.8f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Circular radar radial waveform variant
 */
@Composable
private fun CircularWaveformCanvas(
    rmsLevel: Float,
    wavePhase: Float,
    isRecording: Boolean,
    accentColor: Color
) {
    Canvas(
        modifier = Modifier
            .size(76.dp)
            .testTag("waveform_circular_canvas")
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val baseRadius = size.width * 0.28f
        val rayCount = 24

        for (i in 0 until rayCount) {
            val angle = (i.toFloat() / rayCount) * (2 * Math.PI).toFloat()
            val wave = sin(wavePhase + i * 0.5f)
            val rayLength = if (isRecording) {
                (rmsLevel * 20f * (0.6f + 0.4f * wave)).coerceIn(4f, 26f)
            } else {
                4f
            }

            val startX = centerX + cos(angle) * baseRadius
            val startY = centerY + sin(angle) * baseRadius
            val endX = centerX + cos(angle) * (baseRadius + rayLength)
            val endY = centerY + sin(angle) * (baseRadius + rayLength)

            drawLine(
                color = accentColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}
