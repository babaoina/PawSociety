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
            val request = mapOf(
                "userUid" to userUid,
                "postId" to postId
            )
            println("📤 Adding to favorites - userUid: $userUid, postId: $postId")
            val response = apiService.addToFavorites(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Added to favorites successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Failed to add to favorites: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to add to favorites"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ API error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to add to favorites"))
            }
        } catch (e: Exception) {
            println("❌ Exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Remove from favorites
     */
    suspend fun removeFromFavorites(userUid: String, postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("📤 Removing from favorites - userUid: $userUid, postId: $postId")
            val response = apiService.removeFromFavorites(postId, userUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Removed from favorites successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Failed to remove from favorites: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to remove from favorites"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ API error: $errorBody")
                // If favorite not found, consider it already removed
                if (errorBody?.contains("not found") == true) {
                    println("⚠️ Favorite not found, considering already removed")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(errorBody ?: "Failed to remove from favorites"))
                }
            }
        } catch (e: Exception) {
            println("❌ Exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Check if post is in favorites
     */
    suspend fun checkFavorite(postId: String, userUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            println("📤 Checking favorite - postId: $postId, userUid: $userUid")
            val response = apiService.checkFavorite(postId, userUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Favorite check result: ${body.isFavorite}")
                    Result.success(body.isFavorite ?: false)
                } else {
                    println("❌ Failed to check favorite: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to check favorite"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to check favorite"
                println("❌ API error: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            println("❌ Exception: ${e.message}")
            Result.failure(e)
        }
    }
}