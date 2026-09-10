package com.example.data.repository

import android.content.Context
import com.example.data.ai.ChatMessagePayload
import com.example.data.ai.PersonalityManager
import com.example.data.ai.ProviderExecutionResult
import com.example.data.ai.ProviderManager
import com.example.data.local.ActionHistoryEntity
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.CustomCommandEntity
import com.example.data.local.MemoryFactEntity
import com.example.devicecontrol.ActionIntentParser
import com.example.devicecontrol.ActionRegistry
import com.example.devicecontrol.ActionValidator
import com.example.devicecontrol.DeviceActionExecutor
import com.example.devicecontrol.ExecutionOutcome
import com.example.devicecontrol.ParsedAction
import com.example.devicecontrol.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class AssistantRepository(
    private val context: Context,
    val database: AppDatabase = AppDatabase.getDatabase(context),
    val providerManager: ProviderManager = ProviderManager(context),
    val actionExecutor: DeviceActionExecutor = DeviceActionExecutor(context, database),
    val actionValidator: ActionValidator = ActionValidator(context)
) {

    // Conversations
    fun getConversations(): Flow<List<ConversationEntity>> =
        database.conversationDao().getAllConversations()

    suspend fun createConversation(title: String = "New Chat"): Long = withContext(Dispatchers.IO) {
        val conv = ConversationEntity(title = title)
        database.conversationDao().insertConversation(conv)
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        database.chatMessageDao().deleteMessagesForConversation(id)
        database.conversationDao().deleteConversation(id)
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        database.chatMessageDao().clearAll()
        database.conversationDao().clearAll()
    }

    // Messages
    fun getMessages(conversationId: Long): Flow<List<ChatMessageEntity>> =
        database.chatMessageDao().getMessagesForConversation(conversationId)

    suspend fun addMessage(message: ChatMessageEntity): Long = withContext(Dispatchers.IO) {
        val id = database.chatMessageDao().insertMessage(message)
        // Update conversation timestamp
        val conv = database.conversationDao().getConversationById(message.conversationId)
        if (conv != null) {
            val title = if (conv.title == "New Chat" && message.role == "user") {
                message.content.take(32)
            } else {
                conv.title
            }
            database.conversationDao().updateConversation(conv.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
        id
    }

    suspend fun updateMessage(id: Long, content: String, status: String) = withContext(Dispatchers.IO) {
        database.chatMessageDao().updateMessageContent(id, content, status)
    }

    // Memory
    fun getMemoryFacts(): Flow<List<MemoryFactEntity>> =
        database.memoryDao().getAllFacts()

    suspend fun addMemoryFact(key: String, value: String, category: String = "general"): Long = withContext(Dispatchers.IO) {
        database.memoryDao().insertFact(MemoryFactEntity(key = key, value = value, category = category))
    }

    suspend fun deleteMemoryFact(id: Long) = withContext(Dispatchers.IO) {
        database.memoryDao().deleteFact(id)
    }

    suspend fun clearAllMemory() = withContext(Dispatchers.IO) {
        database.memoryDao().clearAll()
    }

    suspend fun exportMemoryAsText(): String = withContext(Dispatchers.IO) {
        val facts = database.memoryDao().getAllFactsSnapshot()
        val sb = StringBuilder("=== MayaX AI Local Memory Export ===\n\n")
        facts.forEach { f ->
            sb.appendLine("[${f.category.uppercase()}] ${f.key}: ${f.value}")
        }
        sb.toString()
    }

    // Action History
    fun getActionHistory(): Flow<List<ActionHistoryEntity>> =
        database.actionHistoryDao().getAllActions()

    suspend fun clearActionHistory() = withContext(Dispatchers.IO) {
        database.actionHistoryDao().clearAll()
    }

    // Custom Commands
    fun getCustomCommands(): Flow<List<CustomCommandEntity>> =
        database.customCommandDao().getAllCommands()

    suspend fun addCustomCommand(command: CustomCommandEntity): Long = withContext(Dispatchers.IO) {
        database.customCommandDao().insertCommand(command)
    }

    suspend fun deleteCustomCommand(id: Long) = withContext(Dispatchers.IO) {
        database.customCommandDao().deleteCommand(id)
    }

    // AI Execution Pipeline
    suspend fun sendUserPrompt(
        conversationId: Long,
        userPrompt: String,
        stream: Boolean = true,
        onChunk: ((String) -> Unit)? = null,
        onStatus: ((String) -> Unit)? = null
    ): Result<Pair<ChatMessageEntity, ParsedAction?>> = withContext(Dispatchers.IO) {
        // 1. Check custom commands first
        val customCmd = database.customCommandDao().getEnabledCommands().find {
            it.triggerPhrase.equals(userPrompt.trim(), ignoreCase = true) ||
            userPrompt.trim().contains(it.triggerPhrase, ignoreCase = true)
        }

        if (customCmd != null) {
            val parsedAction = ParsedAction(intent = customCmd.actionIntent, target = customCmd.actionTarget)
            val assistantMsg = ChatMessageEntity(
                conversationId = conversationId,
                role = "assistant",
                content = "Triggered custom command '${customCmd.name}'. Executing ${customCmd.actionIntent} on ${customCmd.actionTarget}.",
                provider = "Local Engine",
                model = "CustomCommand",
                status = "complete"
            )
            val insertedId = database.chatMessageDao().insertMessage(assistantMsg)
            return@withContext Result.success(Pair(assistantMsg.copy(id = insertedId), parsedAction))
        }

        // 2. Fetch history snapshot for LLM context
        val previousMessages = database.chatMessageDao().getMessagesSnapshot(conversationId)
        val chatPayloads = previousMessages.map {
            ChatMessagePayload(it.role, it.content)
        }.toMutableList()
        chatPayloads.add(ChatMessagePayload("user", userPrompt))

        // 3. Build system prompt with personality & memory facts
        val memoryList = database.memoryDao().getAllFactsSnapshot().map { "${it.key}: ${it.value}" }
        val systemPrompt = PersonalityManager.buildSystemPrompt(
            personalityName = providerManager.personality,
            customPrompt = providerManager.customSystemPrompt,
            memoryFacts = memoryList
        )

        // 4. Insert placeholder assistant message
        val assistantMessageId = database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                conversationId = conversationId,
                role = "assistant",
                content = "",
                provider = providerManager.providerSelection.name,
                model = providerManager.getActiveModel(),
                status = "streaming"
            )
        )

        val streamingAccumulator = StringBuilder()

        val executionResult = providerManager.executeChat(
            messages = chatPayloads,
            systemPrompt = systemPrompt,
            stream = stream,
            onChunk = { chunk ->
                streamingAccumulator.append(chunk)
                onChunk?.invoke(chunk)
            },
            onStatusUpdate = onStatus
        )

        if (executionResult.isSuccess) {
            val result = executionResult.getOrThrow()
            val fullContent = result.content

            // Parse any device control intent
            val parsedAction = ActionIntentParser.parse(fullContent)
            val cleanContent = ActionIntentParser.cleanResponseText(fullContent)

            // Update database record
            database.chatMessageDao().updateMessageContent(
                assistantMessageId,
                cleanContent,
                "complete"
            )

            val finalEntity = ChatMessageEntity(
                id = assistantMessageId,
                conversationId = conversationId,
                role = "assistant",
                content = cleanContent,
                provider = result.providerUsed,
                model = result.modelUsed,
                status = "complete"
            )

            Result.success(Pair(finalEntity, parsedAction))
        } else {
            val errorMsg = executionResult.exceptionOrNull()?.message ?: "An unexpected error occurred."
            database.chatMessageDao().updateMessageContent(
                assistantMessageId,
                "Error: $errorMsg",
                "error"
            )
            val errEntity = ChatMessageEntity(
                id = assistantMessageId,
                conversationId = conversationId,
                role = "assistant",
                content = "Error: $errorMsg",
                provider = "None",
                model = "None",
                status = "error"
            )
            Result.failure(Exception(errorMsg))
        }
    }
}
