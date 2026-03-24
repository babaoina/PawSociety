package com.example.pawsociety.data.repository

import com.example.pawsociety.api.ApiClient
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.api.ApiUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchRepository {

    private val apiService = ApiClient.apiService

    suspend fun searchPosts(
        query: String,
        status: String? = null,
        viewerUid: String? = null,
        limit: Int = 50,
        skip: Int = 0
    ): Result<List<ApiPost>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchPosts(query, status, viewerUid, limit, skip)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.posts != null) {
                    Result.success(body.posts)
                } else {
                    Result.success(emptyList())
                }
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(
        query: String,
        limit: Int = 50,
        skip: Int = 0
    ): Result<List<ApiUser>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchUsers(query, limit, skip)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.users != null) {
                    Result.success(body.users)
                } else {
                    Result.success(emptyList())
                }
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
