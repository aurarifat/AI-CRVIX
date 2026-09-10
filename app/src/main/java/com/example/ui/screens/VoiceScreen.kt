package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.components.MayaXAnimatedOrb
import com.example.ui.theme.MayaBorder
import com.example.ui.theme.MayaTextPrimary
import com.example.ui.theme.MayaTextSecondary
import com.example.ui.theme.MayaWhite
import com.example.ui.theme.MayaYellowBright
import com.example.ui.theme.MayaYellowContainer
import com.example.ui.theme.MayaYellowDeep
import com.example.ui.theme.MayaYellowPrimary
import com.example.voice.VoiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceEngine.voiceState.collectAsState()
    val rmsLevel by viewModel.voiceEngine.rmsLevel.collectAsState()
    val transcription by viewModel.voiceEngine.transcription.collectAsState()
    val statusMsg by viewModel.voiceEngine.statusMessage.collectAsState()

    val prefs = viewModel.repository.providerManager
    var selectedLang by remember { mutableStateOf(prefs.voiceLanguage) }
    var speechSpeed by remember { mutableFloatStateOf(prefs.speechRate) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.voiceEngine.startListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Voice Assistant",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (voiceState == VoiceState.SPEAKING) {
                        // Interrupt AI Speech button
                        IconButton(
                            onClick = { viewModel.voiceEngine.interrupt() },
                            modifier = Modifier.testTag("voice_interrupt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Interrupt Speech",
                                tint = Color(0xFFD32F2F)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Controls: Language Switcher
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedLang == "bn-BD",
                        onClick = {
                            selectedLang = "bn-BD"
                            prefs.voiceLanguage = "bn-BD"
                            viewModel.syncVoiceSettings()
                        },
                        label = { Text("বাংলা (Bangla)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MayaYellowPrimary,
                            selectedLabelColor = MayaTextPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedLang == "en-US",
                        onClick = {
                            selectedLang = "en-US"
                            prefs.voiceLanguage = "en-US"
                            viewModel.syncVoiceSettings()
                        },
                        label = { Text("English") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MayaYellowPrimary,
                            selectedLabelColor = MayaTextPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedLang == "mixed",
                        onClick = {
                            selectedLang = "mixed"
                            prefs.voiceLanguage = "mixed"
                            viewModel.syncVoiceSettings()
                        },
                        label = { Text("Mixed") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MayaYellowPrimary,
                            selectedLabelColor = MayaTextPrimary
                        )
                    )
                }
            }

            // Central Visualizer Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Large MayaX Animated Orb
                MayaXAnimatedOrb(
                    voiceState = voiceState,
                    rmsLevel = rmsLevel,
                    size = 230.dp,
                    onClick = {
                        if (voiceState == VoiceState.LISTENING) {
                            viewModel.voiceEngine.stopListening()
                        } else if (voiceState == VoiceState.SPEAKING) {
                            viewModel.voiceEngine.interrupt()
                        } else {
                            if (!hasMicPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.voiceEngine.startListening()
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // State description text
                Text(
                    text = when (voiceState) {
                        VoiceState.IDLE -> "Tap orb or mic to speak"
                        VoiceState.LISTENING -> "Listening to your voice..."
                        VoiceState.THINKING -> "MayaX is thinking..."
                        VoiceState.SPEAKING -> "Speaking response..."
                        VoiceState.ERROR -> "Microphone or TTS warning"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Transcription preview
                if (transcription.isNotBlank()) {
                    Surface(
                        color = MayaYellowContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            text = "\"$transcription\"",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MayaTextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Audio Waveform Bar Visualizer when listening
                if (voiceState == VoiceState.LISTENING) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AudioWaveformBars(rmsLevel = rmsLevel)
                }
            }

            // Bottom Controls Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                // Speech Rate adjustment slider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Voice Speed",
                        tint = MayaTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Speed: ${String.format("%.1fx", speechSpeed)}",
                        fontSize = 12.sp,
                        color = MayaTextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = speechSpeed,
                        onValueChange = {
                            speechSpeed = it
                            prefs.speechRate = it
                            viewModel.syncVoiceSettings()
                        },
                        valueRange = 0.7f..1.5f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = MayaYellowPrimary,
                            activeTrackColor = MayaYellowPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Push-to-Talk / Listening Toggle Button
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (voiceState == VoiceState.LISTENING) Color(0xFFE53935) else MayaYellowPrimary
                            )
                            .clickable {
                                if (voiceState == VoiceState.LISTENING) {
                                    viewModel.voiceEngine.stopListening()
                                } else if (voiceState == VoiceState.SPEAKING) {
                                    viewModel.voiceEngine.interrupt()
                                } else {
                                    if (!hasMicPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.voiceEngine.startListening()
                                    }
                                }
                            }
                            .testTag("voice_main_action_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (voiceState) {
                                VoiceState.LISTENING -> Icons.Default.MicOff
                                VoiceState.SPEAKING -> Icons.Default.Stop
                                else -> Icons.Default.Mic
                            },
                            contentDescription = "Voice Action",
                            tint = if (voiceState == VoiceState.LISTENING) Color.White else MayaTextPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (voiceState == VoiceState.LISTENING) "Tap to finish listening" else "Push to talk",
                    fontSize = 13.sp,
                    color = MayaTextSecondary
                )
            }
        }
    }
}

@Composable
fun AudioWaveformBars(rmsLevel: Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bars = 9
        for (i in 0 until bars) {
            val factor = ((i - 4) * (i - 4)).toFloat() / 16f
            val heightMultiplier = (1f - factor * 0.6f) * (0.3f + rmsLevel * 0.7f)
            val barHeight = (8.dp + 32.dp * heightMultiplier).coerceIn(6.dp, 40.dp)

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MayaYellowPrimary)
            )
        }
    }
}
