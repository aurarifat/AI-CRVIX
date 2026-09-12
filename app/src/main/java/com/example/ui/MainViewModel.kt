package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AIModelInfo
import com.example.data.ai.OpenRouterProvider
import com.example.data.ai.PersonalityManager
import com.example.data.ai.ProviderSelection
import com.example.data.local.ActionHistoryEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.CustomCommandEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.repository.AssistantRepository
import com.example.devicecontrol.ActionRegistry
import com.example.devicecontrol.InstalledAppItem
import com.example.devicecontrol.ParsedAction
import com.example.devicecontrol.PermissionManager
import com.example.devicecontrol.ShizukuBridge
import com.example.devicecontrol.ShizukuManager
import com.example.devicecontrol.ShizukuStatus
import com.example.devicecontrol.ValidationResult
import com.example.voice.VoiceEngine
import com.example.voice.VoiceSettings
import com.example.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    HOME,
    CHAT,
    VOICE,
    MEMORY,
    SETTINGS
}

data class ConfirmationDialogState(
    val isVisible: Boolean = false,
    val action: ParsedAction? = null,
    val message: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AssistantRepository(application)
    val shizukuBridge = ShizukuBridge.getInstance(application)
    val shizukuManager = ShizukuManager(application)
    val permissionManager = PermissionManager(application)

    // Current Navigation Tab
    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Active Conversation
    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    // Conversations Flow
    val conversations: StateFlow<List<ConversationEntity>> = repository.getConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Chat Messages Flow
    private val _chatMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageEntity>> = _chatMessages.asStateFlow()

    // Message Input
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // Loading / Streaming states
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Memory Facts Flow
    val memoryFacts: StateFlow<List<MemoryFactEntity>> = repository.getMemoryFacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Action History Flow
    val actionHistory: StateFlow<List<ActionHistoryEntity>> = repository.getActionHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Custom Commands Flow
    val customCommands: StateFlow<List<CustomCommandEntity>> = repository.getCustomCommands()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Installed Apps
    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    // Models
    private val _availableModels = MutableStateFlow<List<AIModelInfo>>(emptyList())
    val availableModels: StateFlow<List<AIModelInfo>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    // OpenRouter Free Models
    private val _openRouterFreeModels = MutableStateFlow<List<AIModelInfo>>(emptyList())
    val openRouterFreeModels: StateFlow<List<AIModelInfo>> = _openRouterFreeModels.asStateFlow()

    private val _isFetchingOpenRouterFreeModels = MutableStateFlow(false)
    val isFetchingOpenRouterFreeModels: StateFlow<Boolean> = _isFetchingOpenRouterFreeModels.asStateFlow()

    private val _openRouterFetchStatus = MutableStateFlow<String?>(null)
    val openRouterFetchStatus: StateFlow<String?> = _openRouterFetchStatus.asStateFlow()

    // Confirmation Dialog
    private val _confirmationState = MutableStateFlow(ConfirmationDialogState())
    val confirmationState: StateFlow<ConfirmationDialogState> = _confirmationState.asStateFlow()

    // Shizuku Status
    private val _shizukuStatus = MutableStateFlow(shizukuManager.getStatus())
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    // Onboarding
    private val _showOnboarding = MutableStateFlow(!repository.providerManager.isOnboardingCompleted)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    // Dark Mode
    private val _isDarkMode = MutableStateFlow(repository.providerManager.darkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Voice Engine
    val voiceEngine = VoiceEngine(
        context = application,
        onTranscriptFinalized = { transcript ->
            handleVoiceInput(transcript)
        },
        onErrorOccurred = { err ->
            _statusBanner.value = err
        }
    )

    init {
        // Initialize Shizuku Bridge for system automation
        shizukuBridge.initialize()

        // Sync voice settings
        syncVoiceSettings()

        // Load installed apps
        loadInstalledApps()

        // Load models
        refreshModels()

        // Initialize default conversation if needed
        viewModelScope.launch {
            conversations.collect { list ->
                if (_activeConversationId.value == null && list.isNotEmpty()) {
                    selectConversation(list.first().id)
                } else if (_activeConversationId.value == null && list.isEmpty()) {
                    val newId = repository.createConversation("New Chat")
                    selectConversation(newId)
                }
            }
        }

        // Reactively sync Shizuku Bridge status into _shizukuStatus StateFlow
        viewModelScope.launch {
            shizukuBridge.status.collect { bridgeStatus ->
                _shizukuStatus.value = ShizukuStatus(
                    isInstalled = bridgeStatus.isInstalled,
                    isRunning = bridgeStatus.isRunning,
                    isPermissionGranted = bridgeStatus.isPermissionGranted,
                    version = bridgeStatus.version,
                    summary = bridgeStatus.summary
                )
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    // Voice-to-Text Input Dictation
    private val _isDictating = MutableStateFlow(false)
    val isDictating: StateFlow<Boolean> = _isDictating.asStateFlow()

    private var preDictationText = ""

    fun startDictation(
        currentText: String = _inputText.value,
        onTextUpdated: (String) -> Unit = { setInputText(it) }
    ) {
        if (!permissionManager.hasRecordAudioPermission()) {
            _statusBanner.value = "Microphone permission is required for voice typing."
            return
        }
        if (!permissionManager.isSpeechRecognitionAvailable()) {
            _statusBanner.value = "Speech recognition service is not available on this device."
            return
        }

        preDictationText = currentText
        _isDictating.value = true
        _statusBanner.value = "Listening... Speak now for voice-to-text"

        voiceEngine.startDictation(
            onPartial = { partial ->
                val combined = if (preDictationText.isBlank()) partial else "$preDictationText $partial"
                onTextUpdated(combined)
            },
            onComplete = { completed ->
                val combined = if (preDictationText.isBlank()) completed else "$preDictationText $completed"
                onTextUpdated(combined)
                _isDictating.value = false
                _statusBanner.value = "Voice input transcribed"
            }
        )
    }

    fun stopDictation() {
        if (_isDictating.value) {
            voiceEngine.stopDictation()
            _isDictating.value = false
            _statusBanner.value = null
        }
    }

    fun toggleDictation(
        currentText: String = _inputText.value,
        onTextUpdated: (String) -> Unit = { setInputText(it) }
    ) {
        if (_isDictating.value) {
            stopDictation()
        } else {
            startDictation(currentText, onTextUpdated)
        }
    }

    fun selectConversation(id: Long) {
        _activeConversationId.value = id
        viewModelScope.launch {
            repository.getMessages(id).collect { messages ->
                _chatMessages.value = messages
            }
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val newId = repository.createConversation("New Chat")
            selectConversation(newId)
            _currentTab.value = AppTab.CHAT
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.clearAllConversations()
            createNewConversation()
        }
    }

    fun syncVoiceSettings() {
        voiceEngine.settings = VoiceSettings(
            languageCode = repository.providerManager.voiceLanguage,
            speechRate = repository.providerManager.speechRate,
            speechPitch = repository.providerManager.speechPitch
        )
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = repository.actionExecutor.getInstalledLaunchableApps()
        }
    }

    fun refreshShizukuStatus() {
        val bridgeStatus = shizukuBridge.refreshStatus()
        _shizukuStatus.value = ShizukuStatus(
            isInstalled = bridgeStatus.isInstalled,
            isRunning = bridgeStatus.isRunning,
            isPermissionGranted = bridgeStatus.isPermissionGranted,
            version = bridgeStatus.version,
            summary = bridgeStatus.summary
        )
    }

    fun refreshModels() {
        viewModelScope.launch {
            _isLoadingModels.value = true
            try {
                _availableModels.value = repository.providerManager.listAllAvailableModels(refresh = true)
            } finally {
                _isLoadingModels.value = false
            }
        }
    }

    fun fetchFreeModelsFromOpenRouter() {
        viewModelScope.launch {
            _isFetchingOpenRouterFreeModels.value = true
            _openRouterFetchStatus.value = "Fetching free models from OpenRouter API..."
            try {
                val result = repository.providerManager.fetchOpenRouterFreeModels()
                if (result.isSuccess) {
                    val models = result.getOrThrow()
                    _openRouterFreeModels.value = models
                    // Merge with existing available models so they are selectable everywhere
                    val otherModels = _availableModels.value.filter { it.provider != "OpenRouter" }
                    _availableModels.value = models + otherModels
                    _openRouterFetchStatus.value = "Found ${models.size} free models from OpenRouter API"
                    _statusBanner.value = "Fetched ${models.size} free models from OpenRouter"
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Network error"
                    _openRouterFreeModels.value = OpenRouterProvider.KNOWN_FREE_MODELS
                    _openRouterFetchStatus.value = "Network issue ($err). Showing cached free models."
                    _statusBanner.value = "OpenRouter fetch notice: $err"
                }
            } catch (e: Exception) {
                _openRouterFreeModels.value = OpenRouterProvider.KNOWN_FREE_MODELS
                _openRouterFetchStatus.value = "Notice: ${e.message}. Showing cached free models."
            } finally {
                _isFetchingOpenRouterFreeModels.value = false
            }
        }
    }

    fun sendMessage(promptText: String? = null) {
        val messageToSend = (promptText ?: _inputText.value).trim()
        if (messageToSend.isBlank() || _isGenerating.value) return

        val conversationId = _activeConversationId.value ?: return
        _inputText.value = ""
        _isGenerating.value = true
        _statusBanner.value = null

        viewModelScope.launch {
            // Save user message
            val userMsg = ChatMessageEntity(
                conversationId = conversationId,
                role = "user",
                content = messageToSend
            )
            repository.addMessage(userMsg)

            // Send to AI
            val result = repository.sendUserPrompt(
                conversationId = conversationId,
                userPrompt = messageToSend,
                stream = true,
                onChunk = { /* flow updates via room */ },
                onStatus = { status ->
                    _statusBanner.value = status
                }
            )

            _isGenerating.value = false
            _statusBanner.value = null

            if (result.isSuccess) {
                val (assistantMessage, parsedAction) = result.getOrThrow()
                if (parsedAction != null) {
                    processDeviceAction(parsedAction)
                }
            } else {
                _statusBanner.value = result.exceptionOrNull()?.message
            }
        }
    }

    private fun handleVoiceInput(transcript: String) {
        if (transcript.isBlank()) return
        val conversationId = _activeConversationId.value ?: return

        viewModelScope.launch {
            val userMsg = ChatMessageEntity(
                conversationId = conversationId,
                role = "user",
                content = transcript
            )
            repository.addMessage(userMsg)

            voiceEngine.setThinkingState()

            val result = repository.sendUserPrompt(
                conversationId = conversationId,
                userPrompt = transcript,
                stream = false,
                onStatus = { status ->
                    _statusBanner.value = status
                }
            )

            if (result.isSuccess) {
                val (assistantMsg, parsedAction) = result.getOrThrow()
                // Speak response aloud in voice mode
                voiceEngine.speak(assistantMsg.content)
                if (parsedAction != null) {
                    processDeviceAction(parsedAction)
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to generate response."
                voiceEngine.setErrorState(err)
                _statusBanner.value = err
            }
        }
    }

    fun stopGeneration() {
        _isGenerating.value = false
        _statusBanner.value = "Generation stopped"
        voiceEngine.interrupt()
    }

    fun retryLastMessage() {
        val lastUserMsg = _chatMessages.value.findLast { it.role == "user" }
        if (lastUserMsg != null) {
            sendMessage(lastUserMsg.content)
        }
    }

    private fun processDeviceAction(action: ParsedAction) {
        if (!repository.providerManager.deviceControlEnabled) {
            _statusBanner.value = "Device control is globally disabled in Settings."
            return
        }

        val validation = repository.actionValidator.validate(action)
        if (validation is ValidationResult.Invalid) {
            _statusBanner.value = "Action blocked: ${validation.reason}"
            return
        }

        val regAction = (validation as ValidationResult.Valid).registeredAction
        val actionKey = "${action.intent}:${action.target}"

        // Check if always allowed
        val isAlwaysAllowed = repository.providerManager.alwaysAllowActions.contains(actionKey) ||
                repository.providerManager.alwaysAllowActions.contains("${action.intent}:*") ||
                (action.target.isBlank() && repository.providerManager.alwaysAllowActions.contains(action.intent)) ||
                (action.target.isNotBlank() && repository.providerManager.alwaysAllowActions.contains("${action.intent}:${action.target.lowercase()}"))

        if (!regAction.requiresConfirmation || isAlwaysAllowed) {
            executeDeviceActionInternal(action)
        } else {
            // Require user confirmation (Allow Once / Always Allow)
            val friendlyTarget = action.target.ifBlank { action.intent }
            _confirmationState.value = ConfirmationDialogState(
                isVisible = true,
                action = action,
                message = "Allow MayaX AI to open '$friendlyTarget'?"
            )
        }
    }

    fun confirmAction(allowAlways: Boolean) {
        val action = _confirmationState.value.action
        _confirmationState.value = ConfirmationDialogState()
        if (action != null) {
            if (allowAlways) {
                val currentSet = repository.providerManager.alwaysAllowActions.toMutableSet()
                val key = if (action.target.isNotBlank()) "${action.intent}:${action.target}" else action.intent
                currentSet.add(key)
                if (action.target.isNotBlank()) {
                    currentSet.add("${action.intent}:${action.target.lowercase()}")
                }
                repository.providerManager.alwaysAllowActions = currentSet
            }
            executeDeviceActionInternal(action)
        }
    }

    fun cancelAction() {
        val action = _confirmationState.value.action
        _confirmationState.value = ConfirmationDialogState()
        _statusBanner.value = "Action cancelled by user."
    }

    fun executeDeviceAction(action: ParsedAction) {
        processDeviceAction(action)
    }

    private fun executeDeviceActionInternal(action: ParsedAction) {
        viewModelScope.launch {
            val outcome = repository.actionExecutor.executeAction(action)
            _statusBanner.value = outcome.userMessage
            if (outcome.isSuccess && _currentTab.value == AppTab.VOICE) {
                voiceEngine.speak(outcome.userMessage)
            }
        }
    }

    fun launchInstalledApp(app: InstalledAppItem) {
        processDeviceAction(ParsedAction(ActionRegistry.INTENT_OPEN_APP, app.appName))
    }

    // Memory operations
    fun addMemoryFact(key: String, value: String, category: String) {
        viewModelScope.launch {
            repository.addMemoryFact(key, value, category)
        }
    }

    fun deleteMemoryFact(id: Long) {
        viewModelScope.launch {
            repository.deleteMemoryFact(id)
        }
    }

    fun clearAllMemory() {
        viewModelScope.launch {
            repository.clearAllMemory()
        }
    }

    // Custom Commands
    fun addCustomCommand(name: String, triggerPhrase: String, actionIntent: String, actionTarget: String) {
        viewModelScope.launch {
            repository.addCustomCommand(
                CustomCommandEntity(
                    name = name,
                    triggerPhrase = triggerPhrase,
                    actionIntent = actionIntent,
                    actionTarget = actionTarget
                )
            )
        }
    }

    fun deleteCustomCommand(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomCommand(id)
        }
    }

    fun clearActionHistory() {
        viewModelScope.launch {
            repository.clearActionHistory()
        }
    }

    fun completeOnboarding() {
        repository.providerManager.isOnboardingCompleted = true
        _showOnboarding.value = false
    }

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        repository.providerManager.darkMode = newMode
    }

    fun requestShizukuPermission() {
        val success = shizukuBridge.requestPermission()
        if (!success) {
            _statusBanner.value = "Shizuku service not responding or permission request redirected."
        }
        refreshShizukuStatus(force = true)
    }

    fun refreshShizukuStatus(force: Boolean = true) {
        viewModelScope.launch {
            if (force) {
                shizukuBridge.forceReconnect()
            }
            val status = shizukuBridge.refreshStatus()
            _statusBanner.value = status.summary
        }
    }

    fun testShizukuShell() {
        viewModelScope.launch {
            _statusBanner.value = "Testing Shizuku privileged shell..."
            val result = shizukuBridge.executeCommand("whoami; getprop ro.build.version.release")
            if (result.isSuccess) {
                _statusBanner.value = "Shizuku Shell Success! Output: ${result.stdout.trim().replace("\n", " | ")}"
            } else {
                _statusBanner.value = "Shizuku Shell Failed: ${result.stderr.ifBlank { "Exit code ${result.exitCode}" }}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.destroy()
        shizukuBridge.destroy()
    }
}
