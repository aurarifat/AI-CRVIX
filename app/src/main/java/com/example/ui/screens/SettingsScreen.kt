package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.ProviderSelection
import com.example.devicecontrol.ActionRegistry
import com.example.devicecontrol.InstalledAppItem
import com.example.devicecontrol.MayaAccessibilityService
import com.example.devicecontrol.ParsedAction
import com.example.devicecontrol.PermissionManager
import com.example.ui.MainViewModel
import com.example.ui.theme.MayaBorder
import com.example.ui.theme.MayaTextPrimary
import com.example.ui.theme.MayaTextSecondary
import com.example.ui.theme.MayaYellowBright
import com.example.ui.theme.MayaYellowContainer
import com.example.ui.theme.MayaYellowDeep
import com.example.ui.theme.MayaYellowPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = viewModel.repository.providerManager

    val availableModels by viewModel.availableModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()
    val openRouterFreeModels by viewModel.openRouterFreeModels.collectAsState()
    val isFetchingOpenRouterModels by viewModel.isFetchingOpenRouterFreeModels.collectAsState()
    val openRouterFetchStatus by viewModel.openRouterFetchStatus.collectAsState()
    val shizukuStatus by viewModel.shizukuStatus.collectAsState()
    val actionHistory by viewModel.actionHistory.collectAsState()
    val customCommands by viewModel.customCommands.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    var openRouterSearchQuery by remember { mutableStateOf("") }
    var showAllFreeModels by remember { mutableStateOf(false) }

    var selectedProvider by remember { mutableStateOf(prefs.providerSelection) }
    var openRouterKey by remember { mutableStateOf(prefs.openRouterApiKey) }
    var omniRouteKey by remember { mutableStateOf(prefs.omniRouteApiKey) }
    var showOpenRouterKey by remember { mutableStateOf(false) }
    var showOmniRouteKey by remember { mutableStateOf(false) }

    var freeOnly by remember { mutableStateOf(prefs.freeModelsOnly) }
    var selectedModel by remember { mutableStateOf(prefs.getActiveModel()) }
    var customModelInput by remember { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(prefs.temperature) }
    var maxTokens by remember { mutableIntStateOf(prefs.maxTokens) }

    var voiceLang by remember { mutableStateOf(prefs.voiceLanguage) }
    var speechRate by remember { mutableFloatStateOf(prefs.speechRate) }
    var speechPitch by remember { mutableFloatStateOf(prefs.speechPitch) }

    var deviceControlEnabled by remember { mutableStateOf(prefs.deviceControlEnabled) }
    var alwaysAllowSet by remember { mutableStateOf(prefs.alwaysAllowActions) }

    var personality by remember { mutableStateOf(prefs.personality) }
    var customSystemPrompt by remember { mutableStateOf(prefs.customSystemPrompt) }

    var showAppPicker by remember { mutableStateOf(false) }
    var showAddCommandDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Device Control",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. AI PROVIDER CONFIGURATION
            item {
                SettingsCard(title = "AI Provider & API Keys", icon = "🤖") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Choose preferred provider. In Auto mode, fallback is automatic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MayaTextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                ProviderSelection.AUTO to "Auto Fallback",
                                ProviderSelection.OPENROUTER to "OpenRouter",
                                ProviderSelection.OMNIROUTE to "OmniRoute"
                            ).forEach { (prov, label) ->
                                FilterChip(
                                    selected = selectedProvider == prov,
                                    onClick = {
                                        selectedProvider = prov
                                        prefs.providerSelection = prov
                                        viewModel.refreshModels()
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MayaYellowPrimary,
                                        selectedLabelColor = MayaTextPrimary
                                    )
                                )
                            }
                        }

                        // OpenRouter Key
                        OutlinedTextField(
                            value = openRouterKey,
                            onValueChange = {
                                openRouterKey = it
                                prefs.openRouterApiKey = it
                            },
                            label = { Text("OpenRouter API Key") },
                            placeholder = { Text("sk-or-v1-...") },
                            visualTransformation = if (showOpenRouterKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showOpenRouterKey = !showOpenRouterKey }) {
                                    Icon(
                                        imageVector = if (showOpenRouterKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // OmniRoute Key
                        OutlinedTextField(
                            value = omniRouteKey,
                            onValueChange = {
                                omniRouteKey = it
                                prefs.omniRouteApiKey = it
                            },
                            label = { Text("OmniRoute API Key") },
                            placeholder = { Text("omni_...") },
                            visualTransformation = if (showOmniRouteKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showOmniRouteKey = !showOmniRouteKey }) {
                                    Icon(
                                        imageVector = if (showOmniRouteKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // 2. MODELS & FREE MODELS ONLY
            item {
                SettingsCard(title = "AI Models & Free Mode", icon = "⚡") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Free Models Only Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Free Models Only", fontWeight = FontWeight.Bold)
                                Text(
                                    "Guarantees you will never be billed. Filters out paid models.",
                                    fontSize = 11.sp,
                                    color = MayaTextSecondary
                                )
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
                                ),
                                modifier = Modifier.testTag("free_models_switch")
                            )
                        }

                        // OpenRouter Free Models API Fetch Option
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MayaYellowPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .background(MayaYellowContainer.copy(alpha = 0.35f))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Fetch Free Models from OpenRouter API",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Query the live OpenRouter endpoint (/api/v1/models) to discover 100% free models with zero billing.",
                                            fontSize = 11.sp,
                                            color = MayaTextSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.fetchFreeModelsFromOpenRouter()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("fetch_openrouter_free_models_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MayaYellowPrimary,
                                        contentColor = MayaTextPrimary
                                    ),
                                    enabled = !isFetchingOpenRouterModels
                                ) {
                                    if (isFetchingOpenRouterModels) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MayaTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Fetching from OpenRouter API...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "Fetch Free Models",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Fetch Free Models from OpenRouter API", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                // Status Banner
                                if (!openRouterFetchStatus.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MayaTextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = openRouterFetchStatus ?: "",
                                            fontSize = 11.sp,
                                            color = MayaTextSecondary
                                        )
                                    }
                                }

                                // Model list from OpenRouter
                                val openRouterModelsList = if (openRouterFreeModels.isNotEmpty()) {
                                    openRouterFreeModels
                                } else {
                                    availableModels.filter { it.provider == "OpenRouter" && it.isFree }
                                }

                                if (openRouterModelsList.isNotEmpty()) {
                                    OutlinedTextField(
                                        value = openRouterSearchQuery,
                                        onValueChange = { openRouterSearchQuery = it },
                                        placeholder = { Text("Filter free models (e.g. gemini, llama)...", fontSize = 12.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp))
                                        },
                                        trailingIcon = {
                                            if (openRouterSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { openRouterSearchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    val filteredModels = openRouterModelsList.filter {
                                        openRouterSearchQuery.isBlank() ||
                                        it.name.contains(openRouterSearchQuery, ignoreCase = true) ||
                                        it.id.contains(openRouterSearchQuery, ignoreCase = true)
                                    }

                                    Text(
                                        text = "${filteredModels.size} free models available (${if (openRouterFreeModels.isNotEmpty()) "Live API" else "Preset"}):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MayaTextSecondary
                                    )

                                    val countToShow = if (showAllFreeModels || filteredModels.size <= 5) filteredModels.size else 5
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        filteredModels.take(countToShow).forEach { model ->
                                            val isCurrent = prefs.getActiveModel() == model.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isCurrent) 1.5.dp else 0.5.dp,
                                                        color = if (isCurrent) MayaYellowPrimary else MayaBorder,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(
                                                        if (isCurrent) MayaYellowContainer else MaterialTheme.colorScheme.surface
                                                    )
                                                    .clickable {
                                                        prefs.selectedModel = model.id
                                                        prefs.customModel = ""
                                                        selectedModel = model.id
                                                        Toast.makeText(context, "Selected ${model.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = model.name,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                            fontSize = 12.sp,
                                                            maxLines = 1
                                                        )
                                                        if (isCurrent) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Active",
                                                                tint = MayaTextPrimary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "${model.id} • Context: ${model.contextLength / 1024}k tokens",
                                                        fontSize = 10.sp,
                                                        color = MayaTextSecondary,
                                                        maxLines = 1
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MayaYellowPrimary)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("FREE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MayaTextPrimary)
                                                }
                                            }
                                        }
                                    }

                                    if (filteredModels.size > 5) {
                                        TextButton(
                                            onClick = { showAllFreeModels = !showAllFreeModels },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        ) {
                                            Text(
                                                text = if (showAllFreeModels) "Show Fewer" else "Show All ${filteredModels.size} Models",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MayaTextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Refresh models button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Model: ${prefs.getActiveModel()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedButton(
                                onClick = { viewModel.refreshModels() },
                                modifier = Modifier.testTag("refresh_models_button")
                            ) {
                                if (isLoadingModels) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("Refresh Models", fontSize = 11.sp)
                            }
                        }

                        // List of available models
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableModels.take(8).forEach { model ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (prefs.getActiveModel() == model.id) MayaYellowContainer else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            prefs.selectedModel = model.id
                                            selectedModel = model.id
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.name,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${model.provider} • Context: ${model.contextLength} tokens",
                                            fontSize = 10.sp,
                                            color = MayaTextSecondary
                                        )
                                    }
                                    if (model.isFree) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MayaYellowPrimary)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("FREE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MayaTextPrimary)
                                        }
                                    }
                                }
                            }
                        }

                        // Custom Model Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customModelInput,
                                onValueChange = { customModelInput = it },
                                placeholder = { Text("Custom Model ID (e.g. meta-llama/...)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (customModelInput.isNotBlank()) {
                                        prefs.customModel = customModelInput.trim()
                                        prefs.selectedModel = customModelInput.trim()
                                        selectedModel = customModelInput.trim()
                                        customModelInput = ""
                                        Toast.makeText(context, "Active model set", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MayaYellowPrimary,
                                    contentColor = MayaTextPrimary
                                )
                            ) {
                                Text("Set")
                            }
                        }

                        // Temperature Slider
                        Column {
                            Text("Temperature: ${String.format("%.2f", temperature)}", fontSize = 12.sp)
                            Slider(
                                value = temperature,
                                onValueChange = {
                                    temperature = it
                                    prefs.temperature = it
                                },
                                valueRange = 0.0f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MayaYellowPrimary,
                                    activeTrackColor = MayaYellowPrimary
                                )
                            )
                        }
                    }
                }
            }

            // 3. VOICE CONFIGURATION
            item {
                SettingsCard(title = "Voice & Speech", icon = "🎙️") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Voice Assistant Language:", fontSize = 12.sp, color = MayaTextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "bn-BD" to "বাংলা (Bangla)",
                                "en-US" to "English",
                                "mixed" to "Mixed"
                            ).forEach { (code, name) ->
                                FilterChip(
                                    selected = voiceLang == code,
                                    onClick = {
                                        voiceLang = code
                                        prefs.voiceLanguage = code
                                        viewModel.syncVoiceSettings()
                                    },
                                    label = { Text(name, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MayaYellowPrimary,
                                        selectedLabelColor = MayaTextPrimary
                                    )
                                )
                            }
                        }

                        // Speech Speed
                        Column {
                            Text("Speech Rate: ${String.format("%.2fx", speechRate)}", fontSize = 12.sp)
                            Slider(
                                value = speechRate,
                                onValueChange = {
                                    speechRate = it
                                    prefs.speechRate = it
                                    viewModel.syncVoiceSettings()
                                },
                                valueRange = 0.7f..1.4f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MayaYellowPrimary,
                                    activeTrackColor = MayaYellowPrimary
                                )
                            )
                        }

                        // Speech Pitch
                        Column {
                            Text("Voice Pitch / Tone: ${String.format("%.2fx", speechPitch)}", fontSize = 12.sp)
                            Slider(
                                value = speechPitch,
                                onValueChange = {
                                    speechPitch = it
                                    prefs.speechPitch = it
                                    viewModel.syncVoiceSettings()
                                },
                                valueRange = 0.8f..1.3f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MayaYellowPrimary,
                                    activeTrackColor = MayaYellowPrimary
                                )
                            )
                        }

                        // Test Voice Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.voiceEngine.previewVoice()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_natural_voice_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Natural Preview", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Natural Voice Preview", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 4. ANDROID DEVICE CONTROL
            item {
                SettingsCard(title = "Android Device Control", icon = "📱") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Global Switch: Disable Device Control
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Device Control Enabled", fontWeight = FontWeight.Bold)
                                Text(
                                    "Permits opening installed apps and safe settings intents.",
                                    fontSize = 11.sp,
                                    color = MayaTextSecondary
                                )
                            }
                            Switch(
                                checked = deviceControlEnabled,
                                onCheckedChange = {
                                    deviceControlEnabled = it
                                    prefs.deviceControlEnabled = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MayaTextPrimary,
                                    checkedTrackColor = MayaYellowPrimary
                                ),
                                modifier = Modifier.testTag("device_control_global_switch")
                            )
                        }

                        // Launch Installed Apps Picker Button
                        Button(
                            onClick = {
                                viewModel.loadInstalledApps()
                                showAppPicker = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MayaYellowPrimary,
                                contentColor = MayaTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("App Launcher & Installed Apps (${installedApps.size})", fontWeight = FontWeight.Bold)
                        }

                        // Always Allow Permissions Revocation
                        Text(
                            text = "Always-Allowed Actions (${alwaysAllowSet.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (alwaysAllowSet.isEmpty()) {
                            Text("No actions marked as 'Always Allow'.", fontSize = 11.sp, color = MayaTextSecondary)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                alwaysAllowSet.forEach { actionKey ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(actionKey, fontSize = 12.sp)
                                        IconButton(
                                            onClick = {
                                                val mutable = prefs.alwaysAllowActions.toMutableSet()
                                                mutable.remove(actionKey)
                                                prefs.alwaysAllowActions = mutable
                                                alwaysAllowSet = mutable
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Revoke", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. AGENTIC WORK & WHATSAPP AUTOMATION
            item {
                SettingsCard(title = "Agentic Work & WhatsApp Automation", icon = "💬") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "MayaX AI can perform complete multi-step tasks like opening WhatsApp, resolving contacts, and automatically sending messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MayaTextSecondary
                        )

                        // Accessibility Service Status
                        val isA11yRunning = MayaAccessibilityService.isRunning()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isA11yRunning) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isA11yRunning) "MayaX Accessibility Service: ACTIVE" else "MayaX Accessibility Service: INACTIVE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isA11yRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isA11yRunning) "Full auto-send enabled for WhatsApp and UI automations."
                                    else "Enable in Android Settings to allow MayaX AI to auto-click Send in WhatsApp.",
                                    fontSize = 11.sp,
                                    color = MayaTextSecondary
                                )
                            }
                            if (!isA11yRunning) {
                                Button(
                                    onClick = {
                                        viewModel.executeDeviceAction(
                                            ParsedAction(
                                                intent = ActionRegistry.INTENT_OPEN_ACCESSIBILITY_SETTINGS,
                                                target = "Accessibility"
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MayaYellowPrimary,
                                        contentColor = MayaTextPrimary
                                    )
                                ) {
                                    Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // App Permissions Status & Grant Button
                        val permManager = remember { PermissionManager(context) }
                        var permUpdated by remember { mutableIntStateOf(0) }
                        val permLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestMultiplePermissions()
                        ) {
                            permUpdated++
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("System Permissions", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = "Mic: ${if (permManager.hasAudioPermission()) "Granted" else "Needed"} • " +
                                            "Contacts: ${if (permManager.hasContactsPermission()) "Granted" else "Needed"} • " +
                                            "Notifs: ${if (permManager.hasNotificationPermission()) "Granted" else "Needed"}",
                                    fontSize = 11.sp,
                                    color = MayaTextSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val missing = permManager.getMissingPermissions()
                                    if (missing.isNotEmpty()) {
                                        permLauncher.launch(missing)
                                    } else {
                                        Toast.makeText(context, "All permissions granted!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Grant", fontSize = 12.sp)
                            }
                        }

                        // Display / Overlay Permission Card
                        val hasDisplayPermission = permManager.hasDisplayOverlayPermission()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (hasDisplayPermission) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasDisplayPermission) "Display Permission: GRANTED" else "Display Over Other Apps: NEEDED",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (hasDisplayPermission) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Allows MayaX AI to communicate with the phone and execute agent tasks on top of other applications.",
                                    fontSize = 11.sp,
                                    color = MayaTextSecondary
                                )
                            }
                            if (!hasDisplayPermission) {
                                Button(
                                    onClick = {
                                        permManager.requestDisplayOverlayPermission()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MayaYellowPrimary,
                                        contentColor = MayaTextPrimary
                                    )
                                ) {
                                    Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 6. SHIZUKU INTEGRATION
            item {
                SettingsCard(title = "Shizuku Integration (Optional)", icon = "⚡") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (shizukuStatus.isRunning && shizukuStatus.isPermissionGranted) Color(0xFFE8F5E9)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Shizuku",
                                tint = if (shizukuStatus.isRunning && shizukuStatus.isPermissionGranted) Color(0xFF2E7D32) else MayaYellowDeep
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (shizukuStatus.isRunning) "Shizuku: Running (v${shizukuStatus.version})" else "Shizuku: Not Connected",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (shizukuStatus.isPermissionGranted) "Permission: Granted" else "Permission: Not Granted",
                                    fontSize = 11.sp,
                                    color = MayaTextSecondary
                                )
                            }
                        }

                        Text(
                            text = "Setup Guide:\n1. Install Shizuku from Play Store or GitHub\n2. Start Shizuku service via Wireless Debugging\n3. Authorize MayaX AI in Shizuku Manager\n4. Tap 'Refresh Status' below",
                            fontSize = 11.sp,
                            color = MayaTextSecondary,
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.refreshShizukuStatus() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Refresh", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    val opened = viewModel.shizukuManager.openShizukuApp()
                                    if (!opened) {
                                        viewModel.shizukuManager.openPlayStoreForShizuku()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MayaYellowPrimary,
                                    contentColor = MayaTextPrimary
                                ),
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text(if (shizukuStatus.isInstalled) "Open Shizuku" else "Get Shizuku", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 6. PERSONALITY & PROMPTS
            item {
                SettingsCard(title = "Personality & System Prompt", icon = "🎭") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Assistant Persona:", fontSize = 12.sp, color = MayaTextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Normal", "Friendly", "Professional", "Study Assistant").forEach { p ->
                                FilterChip(
                                    selected = personality == p,
                                    onClick = {
                                        personality = p
                                        prefs.personality = p
                                    },
                                    label = { Text(p, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MayaYellowPrimary,
                                        selectedLabelColor = MayaTextPrimary
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = customSystemPrompt,
                            onValueChange = {
                                customSystemPrompt = it
                                prefs.customSystemPrompt = it
                            },
                            label = { Text("Custom System Instructions (Optional)") },
                            placeholder = { Text("e.g. Always reply in concise bullet points.") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                }
            }

            // 7. CUSTOM COMMANDS
            item {
                SettingsCard(title = "Custom Commands & Automation", icon = "⚡") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Trigger phrase to intent mapping", fontSize = 12.sp, color = MayaTextSecondary)
                            Button(
                                onClick = { showAddCommandDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MayaYellowPrimary,
                                    contentColor = MayaTextPrimary
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Command", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (customCommands.isEmpty()) {
                            Text("No custom commands configured.", fontSize = 11.sp, color = MayaTextSecondary)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                customCommands.forEach { cmd ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(cmd.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                "\"${cmd.triggerPhrase}\" ➔ ${cmd.actionIntent} (${cmd.actionTarget})",
                                                fontSize = 11.sp,
                                                color = MayaTextSecondary
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteCustomCommand(cmd.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. ACTION HISTORY
            item {
                SettingsCard(title = "Action History Logs", icon = "📋") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Audit log of executed intents", fontSize = 12.sp, color = MayaTextSecondary)
                            if (actionHistory.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearActionHistory() }) {
                                    Text("Clear Log", fontSize = 11.sp, color = Color(0xFFD32F2F))
                                }
                            }
                        }

                        if (actionHistory.isEmpty()) {
                            Text("No action history recorded yet.", fontSize = 11.sp, color = MayaTextSecondary)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                actionHistory.take(10).forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${item.actionType} • ${item.target}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(item.details, fontSize = 10.sp, color = MayaTextSecondary)
                                        }
                                        Text(
                                            item.status,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (item.status) {
                                                "Successful" -> Color(0xFF2E7D32)
                                                "Blocked" -> Color(0xFFE65100)
                                                else -> MayaTextSecondary
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 9. APPEARANCE & SECURITY
            item {
                SettingsCard(title = "Appearance & Security Policy", icon = "🔒") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Dark Mode Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Dark Theme", fontWeight = FontWeight.Medium)
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { viewModel.toggleDarkMode() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MayaTextPrimary,
                                    checkedTrackColor = MayaYellowPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Security Principles:\n• Zero arbitrary shell commands: All actions run through strict allowlists and intent validators.\n• Device control isolation: AI models cannot execute shell code.\n• Local privacy: Conversations and memories stay stored in on-device SQLite database.",
                            fontSize = 11.sp,
                            color = MayaTextSecondary,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("MayaX AI v1.0.0 (Production Release)", fontSize = 10.sp, color = MayaTextSecondary)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // App Picker Dialog
    if (showAppPicker) {
        var appSearch by remember { mutableStateOf("") }
        val filteredApps = installedApps.filter {
            it.appName.contains(appSearch, ignoreCase = true) || it.packageName.contains(appSearch, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Installed Applications", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        placeholder = { Text("Search apps...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        items(filteredApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.launchInstalledApp(app)
                                        showAppPicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(app.packageName, fontSize = 10.sp, color = MayaTextSecondary)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MayaYellowContainer)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MayaTextPrimary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Add Custom Command Dialog
    if (showAddCommandDialog) {
        var cmdName by remember { mutableStateOf("") }
        var trigger by remember { mutableStateOf("") }
        var intent by remember { mutableStateOf("OPEN_APP") }
        var target by remember { mutableStateOf("YouTube") }

        AlertDialog(
            onDismissRequest = { showAddCommandDialog = false },
            title = { Text("New Custom Command", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cmdName,
                        onValueChange = { cmdName = it },
                        label = { Text("Command Name (e.g. Study Mode)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = trigger,
                        onValueChange = { trigger = it },
                        label = { Text("Trigger Phrase (e.g. time to study)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = intent,
                        onValueChange = { intent = it },
                        label = { Text("Action Intent (OPEN_APP, OPEN_YOUTUBE, etc.)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text("Target (e.g. Calculator, Chrome)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cmdName.isNotBlank() && trigger.isNotBlank()) {
                            viewModel.addCustomCommand(cmdName.trim(), trigger.trim(), intent.trim(), target.trim())
                            showAddCommandDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MayaYellowPrimary,
                        contentColor = MayaTextPrimary
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCommandDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
