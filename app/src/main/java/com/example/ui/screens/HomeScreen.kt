package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppTab
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
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val voiceState by viewModel.voiceEngine.voiceState.collectAsState()
    val rmsLevel by viewModel.voiceEngine.rmsLevel.collectAsState()
    val statusMsg by viewModel.voiceEngine.statusMessage.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (currentHour) {
        in 4..11 -> "Good morning 👋"
        in 12..16 -> "Good afternoon 👋"
        in 17..21 -> "Good evening 👋"
        else -> "Good night 🌙"
    }

    var quickInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // MayaX AI Logo Symbol
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MayaYellowPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "M",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MayaTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MayaX AI",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val provName = viewModel.repository.providerManager.providerSelection.name
                            val freeOnly = viewModel.repository.providerManager.freeModelsOnly
                            Text(
                                text = if (freeOnly) "$provName • Free Tier" else provName,
                                fontSize = 11.sp,
                                color = MayaTextSecondary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Greeting Section
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "How can I help you today?",
                style = MaterialTheme.typography.bodyLarge,
                color = MayaTextSecondary,
                modifier = Modifier.align(Alignment.Start)
            )

            if (statusBanner != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MayaYellowContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusBanner ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MayaTextPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Central Animated AI Orb
            MayaXAnimatedOrb(
                voiceState = voiceState,
                rmsLevel = rmsLevel,
                size = 200.dp,
                onClick = {
                    onNavigateToVoice()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Orb Action Label
            Text(
                text = if (voiceState == VoiceState.IDLE) "Tap to talk" else statusMsg,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Bangla & English Voice Assistant",
                fontSize = 12.sp,
                color = MayaTextSecondary
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Quick Actions Title
            Text(
                text = "QUICK ACTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MayaTextSecondary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Horizontal Scroll of Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val chips = listOf(
                    "Open WhatsApp and send message" to "💬",
                    "Open YouTube" to "▶️",
                    "Device status & battery" to "⚡",
                    "Study mode" to "📚",
                    "Battery info" to "🔋",
                    "Open Chrome" to "🌐",
                    "আজকের দিন কেমন?" to "✨"
                )

                chips.forEach { (label, emoji) ->
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MayaBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                viewModel.sendMessage(label)
                                onNavigateToChat()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Quick Chat Input Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MayaBorder, RoundedCornerShape(28.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateToVoice,
                    modifier = Modifier.testTag("home_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Mode",
                        tint = MayaYellowDeep
                    )
                }

                OutlinedTextField(
                    value = quickInput,
                    onValueChange = { quickInput = it },
                    placeholder = { Text("Ask MayaX AI anything...", fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_quick_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    singleLine = true
                )

                if (quickInput.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MayaYellowPrimary)
                            .clickable {
                                viewModel.sendMessage(quickInput)
                                quickInput = ""
                                onNavigateToChat()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = MayaTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
