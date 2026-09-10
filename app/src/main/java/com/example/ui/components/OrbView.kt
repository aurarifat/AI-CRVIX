package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MayaTextPrimary
import com.example.ui.theme.MayaYellowBright
import com.example.ui.theme.MayaYellowDeep
import com.example.ui.theme.MayaYellowMuted
import com.example.ui.theme.MayaYellowPrimary
import com.example.voice.VoiceState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MayaXAnimatedOrb(
    voiceState: VoiceState,
    rmsLevel: Float = 0f,
    size: Dp = 180.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_animations")

    // Breathing pulse for IDLE
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    // Rotation for THINKING
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingRotation"
    )

    // Speaking pulse
    val speakingPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speakingPulse"
    )

    // Dynamic scale based on state and microphone RMS
    val currentScale = when (voiceState) {
        VoiceState.IDLE -> idlePulse
        VoiceState.LISTENING -> (1.0f + (rmsLevel * 0.35f)).coerceIn(1.0f, 1.35f)
        VoiceState.THINKING -> 1.02f
        VoiceState.SPEAKING -> speakingPulse
        VoiceState.ERROR -> 1.0f
    }

    Box(
        modifier = Modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val baseRadius = (this.size.minDimension / 2) * 0.72f

            when (voiceState) {
                VoiceState.IDLE -> {
                    // Soft warm ambient rings
                    drawCircle(
                        color = MayaYellowMuted.copy(alpha = 0.35f),
                        radius = baseRadius * currentScale * 1.15f,
                        center = center
                    )
                    drawCircle(
                        color = MayaYellowBright.copy(alpha = 0.2f),
                        radius = baseRadius * currentScale * 1.3f,
                        center = center
                    )
                }

                VoiceState.LISTENING -> {
                    // Expanding dynamic audio waveform rings
                    val waveRadius1 = baseRadius * (1.1f + rmsLevel * 0.4f)
                    val waveRadius2 = baseRadius * (1.25f + rmsLevel * 0.6f)
                    drawCircle(
                        color = MayaYellowPrimary.copy(alpha = 0.35f),
                        radius = waveRadius1,
                        center = center,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    drawCircle(
                        color = MayaYellowBright.copy(alpha = 0.2f),
                        radius = waveRadius2,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                VoiceState.THINKING -> {
                    // Orbiting energy particles
                    val orbitRadius = baseRadius * 1.18f
                    val angleRad = Math.toRadians(rotation.toDouble())
                    for (i in 0 until 4) {
                        val offsetAngle = angleRad + (i * Math.PI / 2)
                        val px = center.x + (orbitRadius * cos(offsetAngle)).toFloat()
                        val py = center.y + (orbitRadius * sin(offsetAngle)).toFloat()
                        drawCircle(
                            color = MayaYellowPrimary,
                            radius = 4.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                VoiceState.SPEAKING -> {
                    // Pulsing sound aura
                    drawCircle(
                        color = MayaYellowDeep.copy(alpha = 0.25f),
                        radius = baseRadius * currentScale * 1.22f,
                        center = center
                    )
                    drawCircle(
                        color = MayaYellowBright.copy(alpha = 0.15f),
                        radius = baseRadius * currentScale * 1.4f,
                        center = center
                    )
                }

                VoiceState.ERROR -> {
                    // Subtle warning amber ring
                    drawCircle(
                        color = Color(0xFFFFA000).copy(alpha = 0.3f),
                        radius = baseRadius * 1.15f,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }

        // Core Sphere with Yellow Gradient
        Box(
            modifier = Modifier
                .size(size * 0.62f)
                .scale(currentScale)
                .rotate(if (voiceState == VoiceState.THINKING) rotation else 0f)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = when (voiceState) {
                            VoiceState.ERROR -> listOf(Color(0xFFFFB300), Color(0xFFF57C00))
                            VoiceState.SPEAKING -> listOf(MayaYellowBright, MayaYellowDeep, MayaYellowPrimary)
                            VoiceState.LISTENING -> listOf(MayaYellowBright, MayaYellowPrimary, MayaYellowDeep)
                            else -> listOf(MayaYellowBright, MayaYellowPrimary, MayaYellowDeep)
                        }
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner Core Icon / Indicator
            val icon = when (voiceState) {
                VoiceState.LISTENING -> Icons.Default.Mic
                VoiceState.SPEAKING -> Icons.Default.Stop
                else -> Icons.Default.Mic
            }
            Icon(
                imageVector = icon,
                contentDescription = "Voice Action",
                tint = MayaTextPrimary,
                modifier = Modifier.size(size * 0.24f)
            )
        }
    }
}
