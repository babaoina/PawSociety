package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepository {
    
    private val apiService = ApiClient.apiService
    
    /**
     * Get notifications for a user
     */
    suspend fun getNotifications(userId: String, limit: Int = 50, skip: Int = 0): Result<List<ApiNotification>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getNotifications(userId, limit, skip)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.notifications != null) {
                    Result.success(body.notifications)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to load notifications"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to load notifications"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create a notification
     */
    suspend fun createNotification(
        userId: String,
        fromUserId: String,
        fromUserName: String,
        fromUserImage: String? = null,
        type: String,
        postId: String? = null,
        message: String
    ): Result<ApiNotification> = withContext(Dispatchers.IO) {
        try {
            val request = CreateNotificationRequest(
                userId = userId,
                fromUserId = fromUserId,
                fromUserName = fromUserName,
                fromUserImage = fromUserImage,
                type = type,
                postId = postId,
                message = message
            )
            
            val response = apiService.createNotification(request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to create notification"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to create notification"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Mark notification as read
     */
    suspend fun markAsRead(notificationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.markNotificationAsRead(notificationId)
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to mark as read"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Mark all notifications as read
     */
    suspend fun markAllAsRead(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.markAllNotificationsAsRead(userId)
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to mark all as read"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
