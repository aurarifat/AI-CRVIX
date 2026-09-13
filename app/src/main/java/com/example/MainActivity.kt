package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.devicecontrol.PermissionManager
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.components.ActionConfirmationDialog
import com.example.ui.components.OnboardingDialog
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceScreen
import com.example.ui.theme.MayaTextPrimary
import com.example.ui.theme.MayaTextSecondary
import com.example.ui.theme.MayaXTheme
import com.example.ui.theme.MayaYellowBright
import com.example.ui.theme.MayaYellowPrimary

class MainActivity : ComponentActivity() {
    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            mainViewModel = viewModel
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val currentTab by viewModel.currentTab.collectAsState()
            val showOnboarding by viewModel.showOnboarding.collectAsState()
            val confirmationState by viewModel.confirmationState.collectAsState()

            val context = LocalContext.current
            val permissionManager = remember { PermissionManager(context) }
            val permissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                // Permissions updated by user
            }

            // Prompt user for required permissions immediately at app startup
            LaunchedEffect(Unit) {
                val missing = permissionManager.getMissingPermissions()
                if (missing.isNotEmpty()) {
                    permissionsLauncher.launch(missing)
                }
            }

            MayaXTheme(darkTheme = isDarkMode) {
                // Back navigation handling
                BackHandler(enabled = currentTab != AppTab.HOME) {
                    viewModel.selectTab(AppTab.HOME)
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        MayaXBottomBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.selectTab(it) }
                        )
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Crossfade(targetState = currentTab, label = "tab_transition") { tab ->
                            when (tab) {
                                AppTab.HOME -> HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = { viewModel.selectTab(AppTab.SETTINGS) },
                                    onNavigateToVoice = { viewModel.selectTab(AppTab.VOICE) },
                                    onNavigateToChat = { viewModel.selectTab(AppTab.CHAT) }
                                )

                                AppTab.CHAT -> ChatScreen(
                                    viewModel = viewModel,
                                    onNavigateToVoice = { viewModel.selectTab(AppTab.VOICE) }
                                )

                                AppTab.VOICE -> VoiceScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { viewModel.selectTab(AppTab.HOME) }
                                )

                                AppTab.MEMORY -> MemoryScreen(
                                    viewModel = viewModel,
                                    onNavigateToChat = { viewModel.selectTab(AppTab.CHAT) }
                                )

                                AppTab.SETTINGS -> SettingsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { viewModel.selectTab(AppTab.HOME) }
                                )
                            }
                        }

                        // Onboarding Wizard
                        if (showOnboarding) {
                            OnboardingDialog(
                                viewModel = viewModel,
                                onDismiss = { viewModel.completeOnboarding() }
                            )
                        }

                        // Action Confirmation Dialog
                        if (confirmationState.isVisible && confirmationState.action != null) {
                            ActionConfirmationDialog(
                                action = confirmationState.action!!,
                                onConfirm = { allowAlways ->
                                    viewModel.confirmAction(allowAlways)
                                },
                                onCancel = {
                                    viewModel.cancelAction()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel?.refreshShizukuStatus(force = true)
    }
}

@Composable
fun MayaXBottomBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Box(
        modifier = Modifier.shadow(8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            NavigationBarItem(
                selected = currentTab == AppTab.HOME,
                onClick = { onTabSelected(AppTab.HOME) },
                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MayaYellowPrimary,
                    selectedIconColor = MayaTextPrimary,
                    unselectedIconColor = MayaTextSecondary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MayaTextSecondary
                ),
                modifier = Modifier.testTag("nav_home_tab")
            )

            NavigationBarItem(
                selected = currentTab == AppTab.CHAT,
                onClick = { onTabSelected(AppTab.CHAT) },
                icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Chat") },
                label = { Text("Chat", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MayaYellowPrimary,
                    selectedIconColor = MayaTextPrimary,
                    unselectedIconColor = MayaTextSecondary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MayaTextSecondary
                ),
                modifier = Modifier.testTag("nav_chat_tab")
            )

            // Elevated Center Voice Button
            NavigationBarItem(
                selected = currentTab == AppTab.VOICE,
                onClick = { onTabSelected(AppTab.VOICE) },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MayaYellowPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = MayaTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text("Voice", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MayaTextSecondary
                ),
                modifier = Modifier.testTag("nav_voice_tab")
            )

            NavigationBarItem(
                selected = currentTab == AppTab.MEMORY,
                onClick = { onTabSelected(AppTab.MEMORY) },
                icon = { Icon(Icons.Default.Psychology, contentDescription = "Memory") },
                label = { Text("Memory", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MayaYellowPrimary,
                    selectedIconColor = MayaTextPrimary,
                    unselectedIconColor = MayaTextSecondary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MayaTextSecondary
                ),
                modifier = Modifier.testTag("nav_memory_tab")
            )

            NavigationBarItem(
                selected = currentTab == AppTab.SETTINGS,
                onClick = { onTabSelected(AppTab.SETTINGS) },
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MayaYellowPrimary,
                    selectedIconColor = MayaTextPrimary,
                    unselectedIconColor = MayaTextSecondary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MayaTextSecondary
                ),
                modifier = Modifier.testTag("nav_settings_tab")
            )
        }
    }
}
