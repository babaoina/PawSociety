package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository {
    
    private val apiService = ApiClient.apiService
    
    /**
     * Get all conversations for a user
     */
    suspend fun getConversations(firebaseUid: String): Result<List<ApiConversation>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getConversations(firebaseUid)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.conversations != null) {
                    Result.success(body.conversations)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to get conversations"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get conversations"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get messages in a chat
     */
    suspend fun getMessages(
        chatId: String,
        limit: Int = 50,
        skip: Int = 0
    ): Result<List<ApiMessage>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMessages(chatId, limit, skip)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.messages != null) {
                    Result.success(body.messages)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to get messages"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get messages"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Send a message
     */
    suspend fun sendMessage(
        senderUid: String,
        receiverUid: String,
        text: String? = null,
        imageUrl: String? = null
    ): Result<ApiMessage> = withContext(Dispatchers.IO) {
        try {
            val request = SendMessageRequest(
                senderUid = senderUid,
                receiverUid = receiverUid,
                text = text,
                imageUrl = imageUrl
            )
            
            val response = apiService.sendMessage(request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to send message"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to send message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Mark message as read
     */
    suspend fun markMessageAsRead(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.markMessageAsRead(messageId)
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to mark message as read"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Mark all messages in chat as read
     */
    suspend fun markAllMessagesAsRead(chatId: String, firebaseUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.markAllMessagesAsRead(chatId, mapOf("firebaseUid" to firebaseUid))
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to mark messages as read"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a message
     */
    suspend fun deleteMessage(messageId: String, firebaseUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.deleteMessage(messageId, mapOf("firebaseUid" to firebaseUid))
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to delete message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
