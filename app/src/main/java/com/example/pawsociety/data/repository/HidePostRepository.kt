package com.example.pawsociety.data.repository

import com.example.pawsociety.api.ApiClient
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.api.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HidePostRepository {

    private val apiService = ApiClient.apiService

    suspend fun hidePost(userUid: String, postId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Using query parameters as per your server implementation
            val response = apiService.hidePost(userUid, postId)

            if (response.isSuccessful) {
                val body = response.body()
                // Check if the response indicates success
                if (body?.success == true) {
                    return@withContext Result.success(true)
                } else {
                    val errorMsg = body?.message ?: "Failed to hide post"
                    return@withContext Result.failure(Exception(errorMsg))
                }
            } else {
                // Handle HTTP errors
                val errorMsg = when (response.code()) {
                    400 -> "Invalid request"
                    404 -> "Post not found"
                    500 -> "Server error"
                    else -> "Error ${response.code()}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            // Network errors, timeouts, etc.
            return@withContext Result.failure(e)
        }
    }

    suspend fun unhidePost(userUid: String, postId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.unhidePost(userUid, postId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    return@withContext Result.success(true)
                } else {
                    val errorMsg = body?.message ?: "Failed to unhide post"
                    return@withContext Result.failure(Exception(errorMsg))
                }
            } else {
                return@withContext Result.failure(Exception("Error ${response.code()}"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun getHiddenPosts(userUid: String): Result<List<ApiPost>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getHiddenPosts(userUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    // CRITICAL: Filter out any null posts
                    val posts = body.data?.filterNotNull() ?: emptyList()

                    // Log for debugging
                    println("📦 Hidden posts received: ${posts.size} valid posts")
                    if (body.data?.size != posts.size) {
                        println("⚠️ Filtered out ${body.data?.size?.minus(posts.size)} null posts")
                    }

                    Result.success(posts)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to get hidden posts"))
                }
            } else {
                Result.failure(Exception("Error ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHiddenPostIds(userUid: String): Result<Set<String>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getHiddenPosts(userUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    val posts = body.data?.filterNotNull() ?: emptyList()
                    val ids = posts.map { it.postId }.toSet()
                    Result.success(ids)
                } else {
                    Result.success(emptySet())
                }
            } else {
                Result.success(emptySet())
            }
        } catch (e: Exception) {
            Result.success(emptySet())
        }
    }

    suspend fun getHiddenCount(userUid: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getHiddenCount(userUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    // The server returns { success: true, count: X }
                    return@withContext Result.success(body.count ?: 0)
                } else {
                    val errorMsg = body?.message ?: "Failed to get hidden count"
                    return@withContext Result.failure(Exception(errorMsg))
                }
            } else {
                return@withContext Result.failure(Exception("Error ${response.code()}"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}