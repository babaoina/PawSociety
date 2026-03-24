package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UserRepository {

    private val apiService = ApiClient.apiService

    private fun parseApiError(raw: String?, fallback: String): String {
        val body = raw?.trim().orEmpty()
        if (body.isEmpty()) return fallback

        fun extractValue(key: String): String? {
            val regex = """"$key"\s*:\s*"([^"]+)"""".toRegex()
            return regex.find(body)?.groupValues?.getOrNull(1)
        }

        return extractValue("error")
            ?: extractValue("message")
            ?: body.takeIf { !it.startsWith("{") }
            ?: fallback
    }

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
                // Backend returns success=true, just trust it even if data field is empty
                if (body != null && body.success) {
                    // If we have data, use it; otherwise create a minimal user object
                    val user = body.data ?: ApiUser(
                        firebaseUid = firebaseUid,
                        username = username ?: "",
                        email = "",
                        fullName = fullName ?: "",
                        phone = phone ?: "",
                        profileImageUrl = profileImageUrl ?: "",
                        bio = bio ?: "",
                        location = location ?: ""
                    )
                    Result.success(user)
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

    /**
     * Delete account with password verification
     * This calls the backend endpoint which verifies password and deletes all data
     */
    suspend fun deleteAccountWithPassword(
        firebaseUid: String,
        password: String
    ): Result<DeleteAccountResponse> = withContext(Dispatchers.IO) {
        try {
            val request = DeleteAccountRequest(
                firebaseUid = firebaseUid,
                password = password
            )

            val response = apiService.deleteAccountWithPassword(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("Failed to delete account"))
                }
            } else {
                val errorMessage = parseApiError(
                    response.errorBody()?.string(),
                    "Failed to delete account"
                )
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥 NEW: Update security setting real-time
    suspend fun updateSecuritySetting(
        firebaseUid: String,
        settingKey: String,
        value: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val updateData = mapOf(
                "firebaseUid" to firebaseUid,
                "settingKey" to settingKey,
                "value" to value
            )

            val response = apiService.updateSettings(updateData)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to update setting"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥 NEW: Get active sessions for user
    suspend fun getActiveSessions(firebaseUid: String): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getActiveSessions(firebaseUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    val sessions = body.sessions ?: emptyList<Map<String, Any>>()
                    Result.success(sessions)
                } else {
                    Result.failure(Exception("Failed to get sessions"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥 NEW: Logout from specific session
    suspend fun logoutSession(firebaseUid: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val logoutData = mapOf(
                "firebaseUid" to firebaseUid,
                "sessionId" to sessionId
            )

            val response = apiService.logoutSession(logoutData)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to logout"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥 NEW: Logout from all sessions
    suspend fun logoutAllSessions(firebaseUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val logoutData = mapOf("firebaseUid" to firebaseUid)

            val response = apiService.logoutAllSessions(logoutData)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to logout"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔥 NEW: Change user's password
    suspend fun changePassword(
        firebaseUid: String,
        oldPassword: String,
        newPassword: String
    ): Result<ChangePasswordResponse> = withContext(Dispatchers.IO) {
        try {
            // Create request object
            val request = ChangePasswordRequest(
                firebaseUid = firebaseUid,
                oldPassword = oldPassword,
                newPassword = newPassword
            )

            println("🔐 UserRepository: Calling changePassword API for UID: $firebaseUid")
            // Call the API endpoint
            val response = apiService.changePassword(request)
            println("🔐 UserRepository: API response code: ${response.code()}")
            println("🔐 UserRepository: API response headers: ${response.headers()}")

            if (response.isSuccessful) {
                val body = response.body()
                println("🔐 UserRepository: Response body: $body")
                if (body != null && body.success) {
                    // Return success with the response data
                    println("✅ UserRepository: Password change successful: ${body.message}")
                    Result.success(body)
                } else {
                    // Backend returned success=false with error message
                    val error = body?.error ?: "Failed to change password"
                    println("❌ UserRepository: Backend returned error: $error")
                    Result.failure(Exception(error))
                }
            } else {
                // Handle HTTP errors
                val errorMessage = response.errorBody()?.string() ?: "Failed to change password"
                println("❌ UserRepository: HTTP error ${response.code()}: $errorMessage")
                try {
                    // Try to parse JSON error response
                    val errorJson = JSONObject(errorMessage)
                    val error = errorJson.optString("error", "Failed to change password")
                    Result.failure(Exception(error))
                } catch (e: Exception) {
                    // Fallback if not JSON
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            // Network or other exceptions
            println("❌ UserRepository: Exception in changePassword: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 🔥 NEW: Change user's email
    suspend fun changeEmail(
        firebaseUid: String,
        newEmail: String,
        password: String
    ): Result<ChangeEmailResponse> = withContext(Dispatchers.IO) {
        try {
            val request = ChangeEmailRequest(
                firebaseUid = firebaseUid,
                newEmail = newEmail,
                password = password
            )

            println("📧 UserRepository: Calling changeEmail API for UID: $firebaseUid")
            val response = apiService.changeEmail(request)
            println("📧 UserRepository: API response code: ${response.code()}")

            if (response.isSuccessful) {
                val body = response.body()
                println("📧 UserRepository: Response body: $body")
                if (body != null && body.success) {
                    println("✅ UserRepository: Email change successful: ${body.message}")
                    Result.success(body)
                } else {
                    val error = body?.error ?: "Failed to change email"
                    println("❌ UserRepository: Backend returned error: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Failed to change email"
                println("❌ UserRepository: HTTP error ${response.code()}: $errorMessage")
                try {
                    val errorJson = JSONObject(errorMessage)
                    val error = errorJson.optString("error", "Failed to change email")
                    Result.failure(Exception(error))
                } catch (e: Exception) {
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            println("❌ UserRepository: Exception in changeEmail: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 🔥 NEW: Update user's phone number
    suspend fun updatePhone(
        firebaseUid: String,
        phoneNumber: String
    ): Result<UpdatePhoneResponse> = withContext(Dispatchers.IO) {
        try {
            val request = UpdatePhoneRequest(
                firebaseUid = firebaseUid,
                phoneNumber = phoneNumber
            )

            println("📱 UserRepository: Calling updatePhone API for UID: $firebaseUid")
            val response = apiService.updatePhone(request)
            println("📱 UserRepository: API response code: ${response.code()}")

            if (response.isSuccessful) {
                val body = response.body()
                println("📱 UserRepository: Response body: $body")
                if (body != null && body.success) {
                    println("✅ UserRepository: Phone update successful: ${body.message}")
                    Result.success(body)
                } else {
                    val error = body?.error ?: "Failed to update phone"
                    println("❌ UserRepository: Backend returned error: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Failed to update phone"
                println("❌ UserRepository: HTTP error ${response.code()}: $errorMessage")
                try {
                    val errorJson = JSONObject(errorMessage)
                    val error = errorJson.optString("error", "Failed to update phone")
                    Result.failure(Exception(error))
                } catch (e: Exception) {
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            println("❌ UserRepository: Exception in updatePhone: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getSecurityCooldowns(
        firebaseUid: String
    ): Result<SecurityCooldownSettings> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getSecurityCooldowns(firebaseUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.settings != null) {
                    Result.success(body.settings)
                } else {
                    Result.failure(Exception(body?.error ?: body?.message ?: "Failed to load security settings"))
                }
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Failed to load security settings"
                try {
                    val errorJson = JSONObject(errorMessage)
                    val error = errorJson.optString("error", "Failed to load security settings")
                    Result.failure(Exception(error))
                } catch (e: Exception) {
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
