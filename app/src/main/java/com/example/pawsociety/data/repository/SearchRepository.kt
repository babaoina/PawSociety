package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class SearchRepository {

    private val apiService = ApiClient.apiService

    /**
     * Search posts by keyword
     */
    suspend fun searchPosts(
        query: String,
        status: String? = null,
        limit: Int = 50,
        skip: Int = 0
    ): Result<List<ApiPost>> = withContext(Dispatchers.IO) {
        try {
            println("🔍 Searching posts for: '$query'")
            val response = apiService.searchPosts(query, status, limit, skip)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.posts != null) {
                    println("✅ Found ${body.posts.size} posts")
                    Result.success(body.posts)
                } else {
                    Result.success(emptyList())
                }
            } else {
                println("❌ Search failed: ${response.errorBody()?.string()}")
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            println("❌ Search exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Search users by keyword
     */
    suspend fun searchUsers(
        query: String,
        limit: Int = 50,
        skip: Int = 0
    ): Result<List<ApiUser>> = withContext(Dispatchers.IO) {
        try {
            println("🔍 Searching users for: '$query'")
            val response = apiService.searchUsers(query, limit, skip)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.users != null) {
                    println("✅ Found ${body.users.size} users")
                    Result.success(body.users)
                } else {
                    Result.success(emptyList())
                }
            } else {
                println("❌ Search failed: ${response.errorBody()?.string()}")
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            println("❌ Search exception: ${e.message}")
            Result.failure(e)
        }
    }
}