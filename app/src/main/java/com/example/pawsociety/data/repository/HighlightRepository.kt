package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HighlightRepository {

    private val apiService = ApiClient.apiService

    /**
     * Get all highlights for a user
     */
    suspend fun getHighlights(userId: String): Result<List<ApiHighlight>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getHighlights(userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    val highlights = body.highlights ?: emptyList()
                    Result.success(highlights)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to load highlights"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to load highlights"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new highlight
     */
    suspend fun createHighlight(
        userId: String,
        name: String,
        emoji: String,
        color: String,
        imageUrl: String? = null,
        postIds: List<String> = emptyList()
    ): Result<ApiHighlight> = withContext(Dispatchers.IO) {
        try {
            val request = CreateHighlightRequest(
                name = name,
                emoji = emoji,
                color = color,
                imageUrl = imageUrl,
                postIds = postIds
            )

            val response = apiService.createHighlight(userId, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to create highlight"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to create highlight"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update a highlight
     */
    suspend fun updateHighlight(
        userId: String,
        highlightId: String,
        name: String? = null,
        emoji: String? = null,
        color: String? = null,
        imageUrl: String? = null,
        postIds: List<String>? = null
    ): Result<ApiHighlight> = withContext(Dispatchers.IO) {
        try {
            val request = UpdateHighlightRequest(
                name = name,
                emoji = emoji,
                color = color,
                imageUrl = imageUrl,
                postIds = postIds
            )

            val response = apiService.updateHighlight(userId, highlightId, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to update highlight"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to update highlight"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a highlight
     */
    suspend fun deleteHighlight(userId: String, highlightId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Changed from using request body to query parameter
            val response = apiService.deleteHighlight(userId, highlightId, userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to delete highlight"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to delete highlight"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}