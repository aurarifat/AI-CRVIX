package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ai.ProviderSelection
import com.example.devicecontrol.ActionRegistry
import com.example.devicecontrol.MayaAccessibilityService
import com.example.devicecontrol.ParsedAction
import com.example.devicecontrol.PermissionManager
import com.example.ui.MainViewModel
import com.example.ui.theme.MayaBorder
import com.example.ui.theme.MayaTextPrimary
import com.example.ui.theme.MayaTextSecondary
import com.example.ui.theme.MayaYellowBright
import com.example.ui.theme.MayaYellowDeep
import com.example.ui.theme.MayaYellowPrimary

@Composable
fun OnboardingDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    val totalSteps = 7

    val prefs = viewModel.repository.providerManager
    var selectedLang by remember { mutableStateOf(prefs.voiceLanguage) }
    var selectedProvider by remember { mutableStateOf(prefs.providerSelection) }
    var openRouterKey by remember { mutableStateOf(prefs.openRouterApiKey) }
    var omniRouteKey by remember { mutableStateOf(prefs.omniRouteApiKey) }
    var freeOnly by remember { mutableStateOf(prefs.freeModelsOnly) }
    var voiceSpeed by remember { mutableStateOf(prefs.speechRate) }

    Dialog(
        onDismissRequest = { /* Require manual completion or skip */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Stepper Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MayaX AI Setup",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Step $step of $totalSteps",
                            fontSize = 12.sp,
                            color = MayaTextSecondary
                        )
                    }
                    // Progress Indicator Dots
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 1..totalSteps) {
                            Box(
                                modifier = Modifier
                                    .size(if (i == step) 10.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (i <= step) MayaYellowPrimary else MayaBorder)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Step Content
                when (step) {
                    1 -> {
                        Text(
                            text = "Choose Your Language",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "MayaX AI fluently supports Bangla and English voice conversations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MayaTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        }
                    }

                    2 -> {
                        Text(
                            text = "Configure AI Provider",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Auto mode attempts preferred provider first with automatic fallback.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MayaTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                ProviderSelection.AUTO to "Auto (Recommended with Fallback)",
                                ProviderSelection.OPENROUTER to "OpenRouter API",
                                ProviderSelection.OMNIROUTE to "OmniRoute API"
                            ).forEach { (prov, label) ->
                                FilterChip(
                                    selected = selectedProvider == prov,
                                    onClick = {
                                        selectedProvider = prov
                                        prefs.providerSelection = prov
                                    },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MayaYellowPrimary,
                                        selectedLabelColor = MayaTextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    3 -> {
                        Text(
                            text = "Enter AI Provider API Key",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Keys are securely stored on your device and never exposed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MayaTextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = openRouterKey,
                            onValueChange = {
                                openRouterKey = it
                                prefs.openRouterApiKey = it
                            },
                            label = { Text("OpenRouter API Key") },
                            placeholder = { Text("sk-or-v1-...") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = omniRouteKey,
                            onValueChange = {
                                omniRouteKey = it
                                prefs.omniRouteApiKey = it
                            },
                            label = { Text("OmniRoute API Key") },
                            placeholder = { Text("omni_...") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    4 -> {
                        Text(
                            text = "Free Models Only",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Guarantee you are never billed. Only models with zero pricing will be queried.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MayaTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enforce Free Models", fontWeight = FontWeight.Bold)
                                Text("Locks provider to free tier models", fontSize = 12.sp, color = MayaTextSecondary)
                            }
                            Switch(
                                checked = freeOnly,
                                onCheckedChange = {
                                    freeOnly = it
                                    prefs.freeModelsOnly = it
                                    viewModel.refreshModels()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MayaTextPrimary,
                                    checkedTrackColor = MayaYellowPrimary
                                )
                            )
                        }
                    }

                    5 -> {
                        Text(
                            text = "Voice Configuration",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Speech speed and interactive push-to-talk settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MayaTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Speech Speed: ${String.format("%.1fx", voiceSpeed)}")
                            Row {
                                TextButton(onClick = {
                                    voiceSpeed = (voiceSpeed - 0.1f).coerceAtLeast(0.7f)
                                    prefs.speechRate = voiceSpeed
                                    viewModel.syncVoiceSettings()
                                }) { Text("-") }
                                TextButton(onClick = {
                                    voiceSpeed = (voiceSpeed + 0.1f).coerceAtMost(1.5f)
                                    prefs.speechRate = voiceSpeed
                                    viewModel.syncVoiceSettings()
                                }) { Text("+") }
                            }
                        }
                    }

                    6 -> {
                        val context = LocalContext.current
                        val permManager = remember { PermissionManager(context) }
                        var permUpdated by remember { mutableIntStateOf(0) }
                        val permLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestMultiplePermissions()
                        ) {
                            permUpdated++
                        }

                        Text(
                            text = "Agentic Permissions & Automation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Grant permissions so MayaX AI can talk naturally, send WhatsApp messages to contacts, and automate tasks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MayaTextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Microphone (Voice)", fontSize = 13.sp)
                                Text(
                                    text = if (permManager.hasAudioPermission()) "✓ Granted" else "Required",
                                    fontSize = 12.sp,
                                    color = if (permManager.hasAudioPermission()) Color(0xFF2E7D32) else MayaYellowDeep,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Contacts (WhatsApp/Calls)", fontSize = 13.sp)
                                Text(
                                    text = if (permManager.hasContactsPermission()) "✓ Granted" else "Required",
                                    fontSize = 12.sp,
                                    color = if (permManager.hasContactsPermission()) Color(0xFF2E7D32) else MayaYellowDeep,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Notifications", fontSize = 13.sp)
                                Text(
                                    text = if (permManager.hasNotificationPermission()) "✓ Granted" else "Recommended",
                                    fontSize = 12.sp,
                                    color = if (permManager.hasNotificationPermission()) Color(0xFF2E7D32) else MayaTextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Display Over Apps (Agent)", fontSize = 13.sp)
                                if (permManager.hasDisplayOverlayPermission()) {
                                    Text("✓ Granted", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                } else {
                                    TextButton(onClick = { permManager.requestDisplayOverlayPermission() }) {
                                        Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val missing = permManager.getMissingPermissions()
                                if (missing.isNotEmpty()) {
                                    permLauncher.launch(missing)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MayaYellowPrimary,
                                contentColor = MayaTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant All Permissions", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val isA11yRunning = MayaAccessibilityService.isRunning()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isA11yRunning) "Auto-Send Service: Active" else "Auto-Send Service: Inactive",
                                fontSize = 11.sp,
                                color = if (isA11yRunning) Color(0xFF2E7D32) else MayaTextSecondary
                            )
                            if (!isA11yRunning) {
                                TextButton(onClick = {
                                    viewModel.executeDeviceAction(
                                        ParsedAction(
                                            intent = ActionRegistry.INTENT_OPEN_ACCESSIBILITY_SETTINGS,
                                            target = "Accessibility"
                                        )
                                    )
                                }) {
                                    Text("Enable", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    7 -> {
                        Text(
                            text = "You're All Set!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "MayaX AI is ready to assist you. Tap the animated orb or microphone anytime to speak.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MayaTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 1) {
                        TextButton(
                            onClick = { step-- },
                            modifier = Modifier.testTag("onboarding_back_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                    } else {
                        TextButton(onClick = {
                            viewModel.completeOnboarding()
                            onDismiss()
                        }) {
                            Text("Skip Setup", color = MayaTextSecondary)
                        }
                    }

                    if (step < totalSteps) {
                        Button(
                            onClick = { step++ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MayaYellowPrimary,
                                contentColor = MayaTextPrimary
                            ),
                            modifier = Modifier.testTag("onboarding_next_button")
                        ) {
                            Text("Next", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = "Next", modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.completeOnboarding()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MayaYellowPrimary,
                                contentColor = MayaTextPrimary
                            ),
                            modifier = Modifier.testTag("onboarding_finish_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Finish", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Get Started", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
