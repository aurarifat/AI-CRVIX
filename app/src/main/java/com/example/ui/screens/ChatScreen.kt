package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.example.devicecontrol.PermissionManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChatMessageEntity
import com.example.ui.MainViewModel
import com.example.ui.components.TaskExecutionStatusCard
import com.example.ui.theme.MayaBorder
import com.example.ui.theme.MayaTextPrimary
import com.example.ui.theme.MayaTextSecondary
import com.example.ui.theme.MayaWhite
import com.example.ui.theme.MayaYellowBright
import com.example.ui.theme.MayaYellowContainer
import com.example.ui.theme.MayaYellowDeep
import com.example.ui.theme.MayaYellowMuted
import com.example.ui.theme.MayaYellowPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    onNavigateToVoice: () -> Unit
) {
    val context = LocalContext.current
    val messages by viewModel.chatMessages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()
    val isDictating by viewModel.isDictating.collectAsState()
    val isTaskExecuting by viewModel.isTaskExecuting.collectAsState()
    val currentTaskFeedback by viewModel.currentTaskFeedback.collectAsState()
    val taskExecutionLogs by viewModel.taskExecutionLogs.collectAsState()
    val lastTaskSummary by viewModel.lastTaskSummary.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activeConvId by viewModel.activeConversationId.collectAsState()

    val permManager = remember { PermissionManager(context) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startDictation()
        } else {
            Toast.makeText(context, "Microphone permission required for voice-to-text typing", Toast.LENGTH_SHORT).show()
        }
    }

    val listState = rememberLazyListState()
    var showHistoryDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val activeConv = conversations.find { it.id == activeConvId }
                        Text(
                            text = activeConv?.title ?: "Chat",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val activeModel = viewModel.repository.providerManager.getActiveModel()
                        val activeProv = viewModel.repository.providerManager.providerSelection.name
                        Text(
                            text = "$activeProv • $activeModel",
                            fontSize = 11.sp,
                            color = MayaTextSecondary,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    // History Selector
                    Box {
                        IconButton(
                            onClick = { showHistoryDropdown = true },
                            modifier = Modifier.testTag("chat_history_button")
                        ) {
                            Icon(Icons.Default.History, contentDescription = "History")
                        }
                        DropdownMenu(
                            expanded = showHistoryDropdown,
                            onDismissRequest = { showHistoryDropdown = false }
                        ) {
                            Text(
                                text = "Conversations",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MayaTextSecondary
                            )
                            conversations.forEach { conv ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = conv.title,
                                            fontWeight = if (conv.id == activeConvId) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    onClick = {
                                        viewModel.selectConversation(conv.id)
                                        showHistoryDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // New Chat
                    IconButton(
                        onClick = { viewModel.createNewConversation() },
                        modifier = Modifier.testTag("chat_new_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Conversation")
                    }

                    // Clear Chat
                    IconButton(
                        onClick = { viewModel.clearAllConversations() },
                        modifier = Modifier.testTag("chat_clear_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Chat")
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
        ) {
            // Status banner if any
            if (statusBanner != null) {
                Surface(
                    color = MayaYellowContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MayaTextPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusBanner ?: "",
                            fontSize = 12.sp,
                            color = MayaTextPrimary
                        )
                    }
                }
            }

            // Sequential Task Manager Status Card
            if (isTaskExecuting || taskExecutionLogs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    TaskExecutionStatusCard(
                        isExecuting = isTaskExecuting,
                        currentFeedback = currentTaskFeedback,
                        recentLogs = taskExecutionLogs,
                        lastSummary = lastTaskSummary,
                        onClearLogs = { viewModel.clearTaskLogs() },
                        onRunTest = { viewModel.runTestTaskSequence() },
                        initiallyExpanded = isTaskExecuting
                    )
                }
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(MayaYellowContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✨", fontSize = 28.sp)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Start a conversation with MayaX AI",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Ask questions, explore Bangla & English, or execute safe device actions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MayaTextSecondary
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        onCopy = {
                            val clip = ClipData.newPlainText("MayaX AI", msg.content)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, msg.content)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share via"))
                        },
                        onRegenerate = {
                            viewModel.retryLastMessage()
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // Bottom Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Mic
                    IconButton(
                        onClick = onNavigateToVoice,
                        modifier = Modifier.testTag("chat_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Mode",
                            tint = MayaYellowDeep
                        )
                    }

                    // Input Field
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { viewModel.setInputText(it) },
                        placeholder = {
                            Text(if (isDictating) "Listening... speak now" else "Message MayaX AI...")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isDictating) Color(0xFFD32F2F) else MayaYellowPrimary,
                            unfocusedBorderColor = if (isDictating) Color(0xFFD32F2F) else MayaBorder
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (isDictating) {
                                        viewModel.stopDictation()
                                    } else {
                                        if (permManager.hasRecordAudioPermission()) {
                                            viewModel.startDictation()
                                        } else {
                                            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("chat_dictate_button")
                            ) {
                                Icon(
                                    imageVector = if (isDictating) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (isDictating) "Stop Voice Typing" else "Speech to Text Input",
                                    tint = if (isDictating) Color(0xFFD32F2F) else MayaYellowDeep
                                )
                            }
                        },
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isGenerating) {
                        // Stop Generation Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MayaYellowContainer)
                                .clickable { viewModel.stopGeneration() }
                                .testTag("chat_stop_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = MayaTextPrimary
                            )
                        }
                    } else {
                        // Send Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (inputText.isNotBlank()) MayaYellowPrimary else MayaBorder)
                                .clickable(enabled = inputText.isNotBlank()) {
                                    viewModel.sendMessage()
                                }
                                .testTag("chat_send_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) MayaTextPrimary else MayaTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessageEntity,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            // AI Header with Model Name and Provider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MayaYellowPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MayaTextPrimary)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (message.model.isNotBlank()) message.model else "MayaX AI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MayaTextSecondary
                )
                if (message.provider.isNotBlank()) {
                    Text(
                        text = " • ${message.provider}",
                        fontSize = 11.sp,
                        color = MayaTextSecondary
                    )
                }
                if (message.status == "streaming") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Generating...",
                        fontSize = 10.sp,
                        color = MayaYellowDeep
                    )
                }
            }
        }

        // Message Bubble
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(
                    if (isUser) MayaYellowPrimary else MayaYellowContainer
                )
                .border(
                    width = if (isUser) 0.dp else 1.dp,
                    color = if (isUser) androidx.compose.ui.graphics.Color.Transparent else MayaBorder,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .padding(14.dp)
        ) {
            SelectionContainer {
                RenderMessageContent(content = message.content, isUser = isUser)
            }
        }

        // Action Toolbar (Copy, Share, Regenerate)
        Row(
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MayaTextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = MayaTextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }

            if (!isUser) {
                IconButton(
                    onClick = onRegenerate,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerate",
                        tint = MayaTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RenderMessageContent(content: String, isUser: Boolean) {
    if (content.isBlank()) {
        Text("...", color = MayaTextSecondary)
        return
    }

    // Check for code blocks ``` ... ```
    val codeBlockRegex = Regex("""```([a-zA-Z0-9_]*)\n([\s\S]*?)```""")
    val matches = codeBlockRegex.findAll(content).toList()

    if (matches.isEmpty()) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MayaTextPrimary,
            lineHeight = 22.sp
        )
    } else {
        var lastIndex = 0
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            matches.forEach { match ->
                val range = match.range
                if (range.first > lastIndex) {
                    val preText = content.substring(lastIndex, range.first).trim()
                    if (preText.isNotEmpty()) {
                        Text(
                            text = preText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MayaTextPrimary,
                            lineHeight = 22.sp
                        )
                    }
                }
                val lang = match.groupValues[1].ifEmpty { "code" }
                val code = match.groupValues[2].trimEnd()

                // Code block container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(12.dp)
                ) {
                    Text(
                        text = lang.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MayaYellowPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFF0F0F0)
                    )
                }

                lastIndex = range.last + 1
            }

            if (lastIndex < content.length) {
                val postText = content.substring(lastIndex).trim()
                if (postText.isNotEmpty()) {
                    Text(
                        text = postText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MayaTextPrimary,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}
