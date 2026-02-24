package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FavoriteRepository {
    
    private val apiService = ApiClient.apiService
    
    /**
     * Get all favorites for a user
     */
    suspend fun getFavorites(firebaseUid: String): Result<List<ApiPost>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getFavorites(firebaseUid)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.posts != null) {
                    Result.success(body.posts)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to get favorites"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get favorites"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Add to favorites
     */
    suspend fun addToFavorites(userUid: String, postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = mapOf("userUid" to userUid, "postId" to postId)
            val response = apiService.addToFavorites(request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to add to favorites"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to add to favorites"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove from favorites
     */
    suspend fun removeFromFavorites(userUid: String, postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = mapOf("userUid" to userUid)
            val response = apiService.removeFromFavorites(postId, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to remove from favorites"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to remove from favorites"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check if post is in favorites
     */
    suspend fun checkFavorite(postId: String, userUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.checkFavorite(postId, userUid)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body.isFavorite ?: false)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to check favorite"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to check favorite"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
