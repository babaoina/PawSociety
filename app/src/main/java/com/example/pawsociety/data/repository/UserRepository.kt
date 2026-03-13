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
     * Get multiple users by their UIDs (batch fetch)
     * Used for BlockedUsersActivity to load faster
     */
    suspend fun getUsersBatch(userIds: List<String>): Result<Map<String, BatchUser>> = withContext(Dispatchers.IO) {
        try {
            if (userIds.isEmpty()) {
                return@withContext Result.success(emptyMap())
            }

            val userIdsString = userIds.joinToString(",")
            val response = apiService.getUsersBatch(userIdsString)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body.users ?: emptyMap())
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
     * Delete user account
     */
    suspend fun deleteUser(firebaseUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("🗑️ Attempting to delete user: $firebaseUid")
            val response = apiService.deleteUser(firebaseUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ User deleted successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Delete failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to delete user"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ HTTP error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to delete user"))
            }
        } catch (e: Exception) {
            println("❌ Exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get user by Firebase UID
     */
    suspend fun getUserByUid(firebaseUid: String?): Result<ApiUser> = withContext(Dispatchers.IO) {
        if (firebaseUid.isNullOrEmpty()) {
            println("❌ UserRepository: Firebase UID is null or empty")
            return@withContext Result.failure(Exception("Firebase UID cannot be null or empty"))
        }

        try {
            println("📤 API call: Getting user by UID: $firebaseUid")
            val response = apiService.getUserByUid(firebaseUid)
            println("📥 API response code: ${response.code()}")

            if (response.isSuccessful) {
                val body = response.body()
                println("📦 API response body: $body")

                if (body != null && body.success) {
                    if (body.user != null) {
                        // Check if the user has a firebaseUid, if not, use the one we passed
                        val userWithUid = if (body.user.firebaseUid.isNullOrEmpty()) {
                            println("⚠️ Backend returned user without firebaseUid! Using: $firebaseUid")
                            body.user.copy(firebaseUid = firebaseUid)
                        } else {
                            body.user
                        }
                        println("✅ User found: ${userWithUid.username} with UID: ${userWithUid.firebaseUid}")
                        Result.success(userWithUid)
                    } else {
                        println("❌ User is null in response")
                        Result.failure(Exception("User not found"))
                    }
                } else {
                    println("❌ API returned success false: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "User not found"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ API error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to get user"))
            }
        } catch (e: Exception) {
            println("❌ Exception: ${e.message}")
            e.printStackTrace()
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
    /**
     * Search users by username or full name
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
