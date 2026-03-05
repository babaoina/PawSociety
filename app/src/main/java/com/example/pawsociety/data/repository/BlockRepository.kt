package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlockRepository {

    private val apiService = ApiClient.apiService

    /**
     * Block a user
     */
    suspend fun blockUser(blockerUid: String, blockedUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("🚫 Blocking user - blocker: $blockerUid, blocked: $blockedUid")
            val request = mapOf(
                "blockerUid" to blockerUid,
                "blockedUid" to blockedUid
            )
            val response = apiService.blockUser(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ User blocked successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Block failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to block user"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Block error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to block user"))
            }
        } catch (e: Exception) {
            println("❌ Block exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Unblock a user
     */
    suspend fun unblockUser(blockerUid: String, blockedUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("✅ Unblocking user - blocker: $blockerUid, blocked: $blockedUid")
            val response = apiService.unblockUser(blockerUid, blockedUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ User unblocked successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Unblock failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to unblock user"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Unblock error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to unblock user"))
            }
        } catch (e: Exception) {
            println("❌ Unblock exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Check if a user is blocked
     */
    suspend fun checkBlockStatus(blockerUid: String, blockedUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            println("🔍 Checking block status - blocker: $blockerUid, blocked: $blockedUid")
            val response = apiService.checkBlockStatus(blockerUid, blockedUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Block status: ${body.isBlocked}")
                    Result.success(body.isBlocked ?: false)
                } else {
                    println("❌ Check block failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to check block status"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Check block error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to check block status"))
            }
        } catch (e: Exception) {
            println("❌ Check block exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get all blocked users
     */
    suspend fun getBlockedUsers(userId: String): Result<List<Block>> = withContext(Dispatchers.IO) {
        try {
            println("📋 Getting blocked users for: $userId")
            val response = apiService.getBlockedUsers(userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.blocks != null) {
                    println("✅ Got ${body.blocks.size} blocked users")
                    Result.success(body.blocks)
                } else {
                    println("❌ Get blocked users failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to get blocked users"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Get blocked users error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to get blocked users"))
            }
        } catch (e: Exception) {
            println("❌ Get blocked users exception: ${e.message}")
            Result.failure(e)
        }
    }
}