package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FollowRepository {

    private val apiService = ApiClient.apiService

    /**
     * Follow a user
     */
    suspend fun followUser(followerUid: String, followingUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("📤 Follow request - follower: $followerUid, following: $followingUid")
            val request = mapOf(
                "followerUid" to followerUid,
                "followingUid" to followingUid
            )
            val response = apiService.followUser(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Followed successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Follow failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to follow user"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Follow error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to follow user"))
            }
        } catch (e: Exception) {
            println("❌ Follow exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Unfollow a user
     */
    suspend fun unfollowUser(followerUid: String, followingUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("📤 Unfollow request - follower: $followerUid, following: $followingUid")
            val response = apiService.unfollowUser(followerUid, followingUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Unfollowed successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Unfollow failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to unfollow user"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Unfollow error: $errorBody")
                // If relationship not found, consider it already unfollowed
                if (errorBody?.contains("not found") == true) {
                    println("⚠️ Follow relationship not found, considering already unfollowed")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(errorBody ?: "Failed to unfollow user"))
                }
            }
        } catch (e: Exception) {
            println("❌ Unfollow exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Check if a user is following another user
     */
    suspend fun checkFollowStatus(followerUid: String, followingUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            println("📤 Checking follow status - follower: $followerUid, following: $followingUid")
            val response = apiService.checkFollowStatus(followerUid, followingUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Follow status: ${body.isFollowing}")
                    Result.success(body.isFollowing ?: false)
                } else {
                    println("❌ Check follow failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to check follow status"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Check follow error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to check follow status"))
            }
        } catch (e: Exception) {
            println("❌ Check follow exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get followers count for a user
     */
    suspend fun getFollowersCount(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            println("📤 Getting followers count for: $userId")
            val response = apiService.getFollowersCount(userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Followers count: ${body.followersCount}")
                    Result.success(body.followersCount ?: 0)
                } else {
                    println("❌ Get followers count failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to get followers count"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Get followers count error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to get followers count"))
            }
        } catch (e: Exception) {
            println("❌ Get followers count exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get following count for a user
     */
    suspend fun getFollowingCount(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            println("📤 Getting following count for: $userId")
            val response = apiService.getFollowingCount(userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Following count: ${body.followingCount}")
                    Result.success(body.followingCount ?: 0)
                } else {
                    println("❌ Get following count failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to get following count"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Get following count error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to get following count"))
            }
        } catch (e: Exception) {
            println("❌ Get following count exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get list of followers
     */
    suspend fun getFollowers(userId: String): Result<List<ApiUser>> = withContext(Dispatchers.IO) {
        try {
            println("📤 Getting followers for: $userId")
            val response = apiService.getFollowers(userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.users != null) {
                    println("✅ Got ${body.users.size} followers")
                    Result.success(body.users)
                } else {
                    println("❌ Get followers failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to get followers"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Get followers error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to get followers"))
            }
        } catch (e: Exception) {
            println("❌ Get followers exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get list of following
     */
    suspend fun getFollowing(userId: String): Result<List<ApiUser>> = withContext(Dispatchers.IO) {
        try {
            println("📤 Getting following for: $userId")
            val response = apiService.getFollowing(userId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.users != null) {
                    println("✅ Got ${body.users.size} following")
                    Result.success(body.users)
                } else {
                    println("❌ Get following failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to get following"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Get following error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to get following"))
            }
        } catch (e: Exception) {
            println("❌ Get following exception: ${e.message}")
            Result.failure(e)
        }
    }
}