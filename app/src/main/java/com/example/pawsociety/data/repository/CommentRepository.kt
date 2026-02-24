package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommentRepository {
    
    private val apiService = ApiClient.apiService
    
    /**
     * Get all comments for a post
     */
    suspend fun getComments(postId: String): Result<List<ApiComment>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getComments(postId)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.comments != null) {
                    Result.success(body.comments)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to get comments"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get comments"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Add a comment to a post
     */
    suspend fun createComment(
        postId: String,
        firebaseUid: String,
        userName: String,
        text: String
    ): Result<ApiComment> = withContext(Dispatchers.IO) {
        try {
            val request = CreateCommentRequest(
                postId = postId,
                firebaseUid = firebaseUid,
                userName = userName,
                text = text
            )
            
            val response = apiService.createComment(request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to add comment"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to add comment"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a comment
     */
    suspend fun deleteComment(commentId: String, firebaseUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.deleteComment(commentId, mapOf("firebaseUid" to firebaseUid))
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to delete comment"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to delete comment"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Like/unlike a comment
     */
    suspend fun likeComment(commentId: String, firebaseUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.likeComment(commentId, mapOf("firebaseUid" to firebaseUid))
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body.liked ?: false)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to like comment"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to like comment"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
