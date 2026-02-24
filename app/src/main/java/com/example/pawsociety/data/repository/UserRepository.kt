package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository {
    
    private val apiService = ApiClient.apiService
    
    /**
     * Get all users
     */
    suspend fun getUsers(limit: Int = 50, skip: Int = 0): Result<List<ApiUser>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUsers(limit, skip)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.users != null) {
                    Result.success(body.users)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to get users"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get users"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get user by Firebase UID
     */
    suspend fun getUserByUid(firebaseUid: String): Result<ApiUser> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUserByUid(firebaseUid)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "User not found"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update user profile
     */
    suspend fun updateUser(
        firebaseUid: String,
        username: String? = null,
        fullName: String? = null,
        bio: String? = null,
        profileImageUrl: String? = null,
        phone: String? = null,
        location: String? = null
    ): Result<ApiUser> = withContext(Dispatchers.IO) {
        try {
            val request = UpdateUserRequest(
                username = username,
                fullName = fullName,
                bio = bio,
                profileImageUrl = profileImageUrl,
                phone = phone,
                location = location
            )
            
            val response = apiService.updateUser(firebaseUid, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to update user"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to update user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
