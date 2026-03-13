package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.pawsociety.api.ConversationsResponse

class ChatRepository {

    private val apiService = ApiClient.apiService

    /**
     * Get all conversations for a user - UPDATED to return ConversationsResponse
     */
    suspend fun getConversations(firebaseUid: String): Result<ConversationsResponse> = withContext(Dispatchers.IO) {
        try {
            println("📬 ChatRepository: Getting conversations for user: $firebaseUid")
            val response = apiService.getConversations(firebaseUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ ChatRepository: Successfully loaded conversations")
                    Result.success(body)
                } else {
                    println("❌ ChatRepository: Failed to get conversations: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to get conversations"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to get conversations"
                println("❌ ChatRepository: HTTP error: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            println("❌ ChatRepository: Exception: ${e.message}")
            Result.failure(e)
        }
    }


    /**
     * Accept a message request
     */
    suspend fun acceptMessageRequest(chatId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("✅ Accepting message request - chatId: $chatId, userId: $userId")
            val response = apiService.acceptMessageRequest(chatId, mapOf("userId" to userId))

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Message request accepted successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Failed to accept request: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to accept request"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ HTTP error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to accept request"))
            }
        } catch (e: Exception) {
            println("❌ Exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Reject a message request
     */
    suspend fun rejectMessageRequest(chatId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("❌ Rejecting message request - chatId: $chatId, userId: $userId")
            val response = apiService.rejectMessageRequest(chatId, userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Message request rejected successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Failed to reject request: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to reject request"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ HTTP error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to reject request"))
            }
        } catch (e: Exception) {
            println("❌ Exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Clear all messages in a chat
     */
    suspend fun clearChat(chatId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.clearChat(chatId, userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to clear chat"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to clear chat"))
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
        userId: String,
        limit: Int = 50,
        skip: Int = 0
    ): Result<List<ApiMessage>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getMessages(chatId, userId, limit, skip)

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
    suspend fun deleteMessage(messageId: String, senderUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.deleteMessage(messageId, senderUid)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to delete message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== MUTE FEATURE ====================

    /**
     * Mute notifications from a user
     */
    suspend fun muteUser(userId: String, userToMute: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("🔇 Muting user - userId: $userId, userToMute: $userToMute")
            val request = mapOf(
                "userId" to userId,
                "userToMute" to userToMute
            )
            val response = apiService.muteUser(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ User muted successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Mute failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to mute user"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Mute error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to mute user"))
            }
        } catch (e: Exception) {
            println("❌ Mute exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Unmute notifications from a user
     */
    suspend fun unmuteUser(userId: String, userToUnmute: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("🔊 Unmuting user - userId: $userId, userToUnmute: $userToUnmute")
            val request = mapOf(
                "userId" to userId,
                "userToUnmute" to userToUnmute
            )
            val response = apiService.unmuteUser(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ User unmuted successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Unmute failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to unmute user"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Unmute error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to unmute user"))
            }
        } catch (e: Exception) {
            println("❌ Unmute exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get all muted users
     */
    suspend fun getMutedUsers(userId: String): Result<List<MutedUser>> = withContext(Dispatchers.IO) {
        try {
            println("📋 Getting muted users for: $userId")
            val response = apiService.getMutedUsers(userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    val mutedUsers = body.data ?: emptyList()
                    println("✅ Got ${mutedUsers.size} muted users")
                    Result.success(mutedUsers)
                } else {
                    println("❌ Get muted users failed: ${body?.message}")
                    Result.success(emptyList())
                }
            } else {
                println("❌ Get muted users error: ${response.errorBody()?.string()}")
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            println("❌ Get muted users exception: ${e.message}")
            Result.success(emptyList())
        }
    }

    // ==================== DELETE CONVERSATION FEATURE ====================

    /**
     * Delete a conversation entirely from the user's inbox
     */
    suspend fun deleteConversation(chatId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("🗑️ User $userId deleting conversation $chatId from inbox")
            val response = apiService.deleteConversation(chatId, userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Conversation deleted from inbox successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Delete failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to delete conversation"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Delete error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to delete conversation"))
            }
        } catch (e: Exception) {
            println("❌ Delete exception: ${e.message}")
            Result.failure(e)
        }
    }
}